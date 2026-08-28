package com.example.opticore.util;

import com.example.opticore.compat.OpticoreCompat;
import com.example.opticore.config.FastAccessConfig;
import com.example.opticore.culling.OcclusionRaycaster;
import com.example.opticore.culling.PredictiveVisibility;
import com.example.opticore.culling.TemporalVisibilityCache;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Safe async culler: workers operate only on immutable snapshots. */
public final class CullWorker {
    private static final AtomicReference<CullingSnapshot> pending = new AtomicReference<>();
    private static final AtomicReference<IntOpenHashSet> culledEntities = new AtomicReference<>(new IntOpenHashSet());
    private static final AtomicReference<LongOpenHashSet> culledBlockEntities = new AtomicReference<>(new LongOpenHashSet());
    private static final AtomicLong generation = new AtomicLong();
    private static final AtomicBoolean running = new AtomicBoolean();
    private static final AtomicBoolean workerActive = new AtomicBoolean();
    private static final ConcurrentLinkedQueue<BlockEntitySample> blockEntitySamples = new ConcurrentLinkedQueue<>();
    private static final Map<Integer, OcclusionCacheEntry> occlusionCache = new ConcurrentHashMap<>();
    private static final TemporalVisibilityCache temporalCache = new TemporalVisibilityCache(150_000_000L);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "opticore-cull-worker");
        t.setDaemon(true); t.setPriority(Thread.NORM_PRIORITY - 2); return t;
    });
    private static final long CAPTURE_INTERVAL_NS = 75_000_000L;
    private static final long OCCLUSION_TTL_NS = 125_000_000L;
    private static final int MAX_OCCLUSION_TESTS = 8;
    private static volatile long lastCapture;
    private CullWorker() {}

    public static void start() {
        if (running.compareAndSet(false, true)) {
            pending.set(null); generation.incrementAndGet();
            culledEntities.set(new IntOpenHashSet()); culledBlockEntities.set(new LongOpenHashSet());
            occlusionCache.clear(); temporalCache.clear();
        }
    }
    public static void stop() {
        running.set(false); pending.set(null); generation.incrementAndGet(); blockEntitySamples.clear();
        occlusionCache.clear(); temporalCache.clear();
        culledEntities.set(new IntOpenHashSet()); culledBlockEntities.set(new LongOpenHashSet());
    }
    public static void captureAndSubmit(MinecraftClient client) {
        if (!running.get() || client == null || client.world == null || client.player == null) return;
        long now=System.nanoTime(); if(now-lastCapture<CAPTURE_INTERVAL_NS)return; lastCapture=now;
        submit(capture(client.world,client.player,now));
    }
    public static void registerBlockEntity(long posHash,double distSq,int typeKey){if(running.get())blockEntitySamples.offer(new BlockEntitySample(posHash,distSq,typeKey));}
    public static IntOpenHashSet getCulledEntityIds(){return culledEntities.get();}
    public static LongOpenHashSet getCulledBlockEntityHashes(){return culledBlockEntities.get();}
    public static IntOpenHashSet culledEntityIds(){return culledEntities.get();}
    public static LongOpenHashSet culledBlockEntityHashes(){return culledBlockEntities.get();}

    private static void submit(CullingSnapshot snapshot){
        if(!running.get())return; long id=generation.incrementAndGet(); pending.set(snapshot.withGeneration(id));
        if(workerActive.compareAndSet(false,true))EXECUTOR.execute(CullWorker::drain);
    }
    private static void drain(){
        try{while(running.get()){CullingSnapshot s=pending.getAndSet(null);if(s==null)return;process(s);}}
        finally{workerActive.set(false);if(running.get()&&pending.get()!=null&&workerActive.compareAndSet(false,true))EXECUTOR.execute(CullWorker::drain);}
    }
    private static void process(CullingSnapshot s){
        IntOpenHashSet entities=new IntOpenHashSet(Math.max(16,s.entities.size()));
        LongOpenHashSet blocks=new LongOpenHashSet(Math.max(16,s.blockEntities.size()));
        for(EntitySample e:s.entities)if(e.distanceSq>e.thresholdSq||e.occluded)entities.add(e.id);
        for(BlockEntitySample e:s.blockEntities)if(e.distSq>blockEntityThreshold(e.typeKey,s.profile))blocks.add(e.posHash);
        if(!running.get()||generation.get()!=s.generation)return;
        culledEntities.set(entities);culledBlockEntities.set(blocks);
    }
    private static CullingSnapshot capture(ClientWorld world,ClientPlayerEntity player,long now){
        double px=player.getX(),py=player.getY(),pz=player.getZ();
        Vec3d camera=new Vec3d(px,py+player.getStandingEyeHeight(),pz),look=player.getRotationVec(1.0f);
        double speed=player.getVelocity().horizontalLength(); SnapshotProfile profile=SnapshotProfile.capture();
        List<EntitySample> entities=new ArrayList<>(); int budget=MAX_OCCLUSION_TESTS;
        int cbx=MathHelper.floor(camera.x),cby=MathHelper.floor(camera.y),cbz=MathHelper.floor(camera.z);
        for(Entity entity:world.getEntities()){
            if(entity==null||entity instanceof PlayerEntity||entity instanceof AreaEffectCloudEntity||entity instanceof EndCrystalEntity)continue;
            double dx=entity.getX()-px,dy=entity.getY()-py,dz=entity.getZ()-pz,distSq=dx*dx+dy*dy+dz*dz;
            double threshold=entityThreshold(entity,profile); boolean occluded=false;
            if(distSq>16.0&&distSq<=threshold){
                double horizontalSq=dx*dx+dz*dz,dot=dx*look.x+dz*look.z;
                if(horizontalSq>1.0&&dot>0.0&&PredictiveVisibility.likelyRelevant(dx,dz,look,speed,distSq)){
                    Vec3d center=entity.getBoundingBox().getCenter();
                    int ebx=MathHelper.floor(center.x),ebz=MathHelper.floor(center.z);
                    long entityCell=cellKey(ebx>>4,ebz>>4),cameraCell=cellKey(cbx>>4,cbz>>4);
                    TemporalVisibilityCache.Entry temporal=temporalCache.get(entity.getId());
                    if(temporalCache.valid(temporal,cameraCell,entityCell,now))occluded=!temporal.visible();
                    else if(budget>0){
                        OcclusionCacheEntry cached=occlusionCache.get(entity.getId());
                        if(cached!=null&&cached.cameraX==cbx&&cached.cameraY==cby&&cached.cameraZ==cbz&&cached.entityCell==entityCell&&now-cached.timestamp<=OCCLUSION_TTL_NS)occluded=cached.occluded;
                        else{occluded=!OcclusionRaycaster.hasLineOfSight(world,camera,center);occlusionCache.put(entity.getId(),new OcclusionCacheEntry(now,cbx,cby,cbz,entityCell,occluded));budget--;}
                        temporalCache.put(entity.getId(),new TemporalVisibilityCache.Entry(cameraCell,entityCell,now,!occluded));
                    }
                }
            }
            entities.add(new EntitySample(entity.getId(),distSq,threshold,occluded));
        }
        return new CullingSnapshot(0,now,entities,drainBlockEntities(),profile);
    }
    private static List<BlockEntitySample> drainBlockEntities(){ArrayList<BlockEntitySample> out=new ArrayList<>();BlockEntitySample e;while((e=blockEntitySamples.poll())!=null)out.add(e);return out;}
    private static double entityThreshold(Entity e,SnapshotProfile p){
        double base=e instanceof HostileEntity?p.hostile:e instanceof ArmorStandEntity?p.armor:e instanceof ItemEntity?p.items:(e instanceof PassiveEntity||e instanceof AnimalEntity||e instanceof WaterCreatureEntity||e instanceof AmbientEntity?p.passive:p.misc);
        if(p.stutter)base*=0.5;if(p.chunkBusy)base*=0.75;if(p.shader&&FastAccessConfig.AGGRESSIVE_SHADER_CULLING)base*=FastAccessConfig.shaderCullMultiplier;return base;
    }
    private static double blockEntityThreshold(int typeKey,SnapshotProfile p){double base=typeKey;if(p.stutter)base=Math.min(base,64);if(p.chunkBusy)base*=0.75;return base;}
    private static long cellKey(int x,int z){return((long)x<<32)^(z&0xffffffffL);}
    private record OcclusionCacheEntry(long timestamp,int cameraX,int cameraY,int cameraZ,long entityCell,boolean occluded){}
    public record EntitySample(int id,double distanceSq,double thresholdSq,boolean occluded){}
    public record BlockEntitySample(long posHash,double distSq,int typeKey){}
    public record SnapshotProfile(int passive,int hostile,int armor,int items,int misc,boolean stutter,boolean chunkBusy,boolean shader){
        static SnapshotProfile capture(){return new SnapshotProfile(FastAccessConfig.PASSIVE_MOB_DISTANCE_SQ,FastAccessConfig.HOSTILE_MOB_DISTANCE_SQ,FastAccessConfig.ARMOR_STAND_DISTANCE_SQ,FastAccessConfig.DROPPED_ITEM_DISTANCE_SQ,FastAccessConfig.MISC_ENTITY_DISTANCE_SQ,FastAccessConfig.isStutterRisk,FastAccessConfig.chunkLoadingBusy,OpticoreCompat.isShaderPackActive());}
    }
    public record CullingSnapshot(long generation,long capturedAtNanos,List<EntitySample> entities,List<BlockEntitySample> blockEntities,SnapshotProfile profile){
        public CullingSnapshot{entities=List.copyOf(entities);blockEntities=List.copyOf(blockEntities);}
        CullingSnapshot withGeneration(long g){return new CullingSnapshot(g,capturedAtNanos,entities,blockEntities,profile);}
    }
}
