package com.example.opticore.util;

import com.example.opticore.compat.OpticoreCompat;
import com.example.opticore.config.FastAccessConfig;
import com.example.opticore.culling.OcclusionRaycaster;
import com.example.opticore.culling.PredictiveVisibility;
import com.example.opticore.culling.TemporalVisibilityCache;
import com.example.opticore.render.RenderLodManager;
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

/** Snapshot-based asynchronous culling. Workers never access live Minecraft state. */
public final class CullWorker {
    private static final AtomicReference<CullingSnapshot> PENDING_SNAPSHOT = new AtomicReference<>();
    private static final AtomicLong LATEST_GENERATION = new AtomicLong();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final AtomicBoolean WORKER_ACTIVE = new AtomicBoolean();
    private static final AtomicReference<IntOpenHashSet> CULLED_ENTITY_IDS = new AtomicReference<>(new IntOpenHashSet());
    private static final AtomicReference<LongOpenHashSet> CULLED_BLOCK_ENTITY_HASHES = new AtomicReference<>(new LongOpenHashSet());
    private static final ConcurrentLinkedQueue<BlockEntitySample> PENDING_BLOCK_ENTITIES = new ConcurrentLinkedQueue<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "opticore-cull-worker");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 2);
        return t;
    });

    private static volatile long lastCaptureNanos;
    private static final long CAPTURE_INTERVAL_NANOS = 75_000_000L;
    private static final long OCCLUSION_CACHE_TTL_NANOS = 125_000_000L;
    private static final int MAX_OCCLUSION_TESTS_PER_CAPTURE = 8;
    private static final Map<Integer, OcclusionCacheEntry> OCCLUSION_CACHE = new ConcurrentHashMap<>();
    private static final TemporalVisibilityCache TEMPORAL_CACHE = new TemporalVisibilityCache(150_000_000L);

    private CullWorker() {}

    public static void start() {
        if (RUNNING.compareAndSet(false, true)) {
            lastCaptureNanos = 0L;
            PENDING_SNAPSHOT.set(null);
            CULLED_ENTITY_IDS.set(new IntOpenHashSet());
            CULLED_BLOCK_ENTITY_HASHES.set(new LongOpenHashSet());
            OCCLUSION_CACHE.clear();
            TEMPORAL_CACHE.clear();
        }
    }

    public static void stop() {
        RUNNING.set(false);
        PENDING_SNAPSHOT.set(null);
        LATEST_GENERATION.incrementAndGet();
        PENDING_BLOCK_ENTITIES.clear();
        CULLED_ENTITY_IDS.set(new IntOpenHashSet());
        CULLED_BLOCK_ENTITY_HASHES.set(new LongOpenHashSet());
        OCCLUSION_CACHE.clear();
        TEMPORAL_CACHE.clear();
    }

    public static void captureAndSubmit(MinecraftClient client) {
        if (!RUNNING.get() || client == null || client.world == null || client.player == null) return;
        long now = System.nanoTime();
        if (now - lastCaptureNanos < CAPTURE_INTERVAL_NANOS) return;
        lastCaptureNanos = now;
        submitSnapshot(captureSnapshot(client.world, client.player, now));
    }

    public static void submitSnapshot(CullingSnapshot snapshot) {
        if (!RUNNING.get() || snapshot == null) return;
        long generation = LATEST_GENERATION.incrementAndGet();
        PENDING_SNAPSHOT.set(snapshot.withGeneration(generation));
        scheduleWorkerIfNeeded();
    }

    public static void registerBlockEntity(long posHash, double distSq, int typeKey) {
        if (RUNNING.get()) PENDING_BLOCK_ENTITIES.offer(new BlockEntitySample(posHash, distSq, typeKey));
    }

    public static IntOpenHashSet getCulledEntityIds() { return CULLED_ENTITY_IDS.get(); }
    public static LongOpenHashSet getCulledBlockEntityHashes() { return CULLED_BLOCK_ENTITY_HASHES.get(); }
    public static IntOpenHashSet culledEntityIds() { return CULLED_ENTITY_IDS.get(); }
    public static LongOpenHashSet culledBlockEntityHashes() { return CULLED_BLOCK_ENTITY_HASHES.get(); }

    private static void scheduleWorkerIfNeeded() {
        if (WORKER_ACTIVE.compareAndSet(false, true)) EXECUTOR.execute(CullWorker::drainSnapshots);
    }

    private static void drainSnapshots() {
        try {
            while (RUNNING.get()) {
                CullingSnapshot snapshot = PENDING_SNAPSHOT.getAndSet(null);
                if (snapshot == null) return;
                process(snapshot);
            }
        } finally {
            WORKER_ACTIVE.set(false);
            if (RUNNING.get() && PENDING_SNAPSHOT.get() != null) scheduleWorkerIfNeeded();
        }
    }

    private static void process(CullingSnapshot snapshot) {
        IntOpenHashSet nextEntityCulls = new IntOpenHashSet(Math.max(16, snapshot.entities().size()));
        LongOpenHashSet nextBlockEntityCulls = new LongOpenHashSet(Math.max(16, snapshot.blockEntities().size()));

        for (EntitySample entity : snapshot.entities()) {
            RenderLodManager.Tier tier = RenderLodManager.entityTier(entity.distanceSq(), snapshot.profile().stutterRisk());
            if (tier == RenderLodManager.Tier.CULLED || entity.distanceSq() > entity.thresholdSq() || entity.occluded()) {
                nextEntityCulls.add(entity.id());
            }
        }
        for (BlockEntitySample blockEntity : snapshot.blockEntities()) {
            if (blockEntity.distSq() > blockEntityThreshold(blockEntity.typeKey(), snapshot.profile())) {
                nextBlockEntityCulls.add(blockEntity.posHash());
            }
        }

        if (!RUNNING.get() || LATEST_GENERATION.get() != snapshot.generation()) return;
        CULLED_ENTITY_IDS.set(nextEntityCulls);
        CULLED_BLOCK_ENTITY_HASHES.set(nextBlockEntityCulls);
    }

    private static CullingSnapshot captureSnapshot(ClientWorld world, ClientPlayerEntity player, long captureNanos) {
        double playerX = player.getX(), playerY = player.getY(), playerZ = player.getZ();
        Vec3d cameraPos = new Vec3d(playerX, playerY + player.getStandingEyeHeight(), playerZ);
        Vec3d look = player.getRotationVec(1.0f);
        double cameraSpeed = player.getVelocity().horizontalLength();
        SnapshotProfile profile = SnapshotProfile.capture();
        int cameraBlockX = MathHelper.floor(cameraPos.x), cameraBlockY = MathHelper.floor(cameraPos.y), cameraBlockZ = MathHelper.floor(cameraPos.z);
        long cameraCellKey = blockKey(cameraBlockX, cameraBlockY, cameraBlockZ);
        List<EntitySample> entities = new ArrayList<>();
        int occlusionTestsRemaining = MAX_OCCLUSION_TESTS_PER_CAPTURE;

        for (Entity entity : world.getEntities()) {
            if (entity == null || entity instanceof PlayerEntity || entity instanceof AreaEffectCloudEntity || entity instanceof EndCrystalEntity) continue;
            if (OpticoreCompat.IS_DH_LOADED && entity.isSpectator()) continue;

            double dx = entity.getX() - playerX, dy = entity.getY() - playerY, dz = entity.getZ() - playerZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            double threshold = entityThreshold(entity, profile);
            boolean occluded = false;

            if (distSq <= threshold && distSq > 16.0D) {
                double dirLenSq = dx * dx + dz * dz;
                if (dirLenSq > 1.0D) {
                    double lookDot = dx * look.x + dz * look.z;
                    double forwardCosSq = (lookDot * lookDot) / (dirLenSq + 1.0e-6D);
                    if (PredictiveVisibility.likelyRelevant(dx, dz, look, cameraSpeed, distSq) && lookDot > 0.0D && forwardCosSq > 0.12D) {
                        Vec3d center = entity.getBoundingBox().getCenter();
                        long entityBlockKey = blockKey(MathHelper.floor(center.x), MathHelper.floor(center.y), MathHelper.floor(center.z));
                        TemporalVisibilityCache.Entry temporal = TEMPORAL_CACHE.get(entity.getId());
                        OcclusionCacheEntry cached = OCCLUSION_CACHE.get(entity.getId());
                        if (TEMPORAL_CACHE.valid(temporal, cameraCellKey, entityBlockKey, captureNanos) && temporal.visible()) {
                            occluded = false;
                        } else if (cached != null && cached.cameraBlockX == cameraBlockX && cached.cameraBlockY == cameraBlockY && cached.cameraBlockZ == cameraBlockZ
                                && cached.entityBlockKey == entityBlockKey && captureNanos - cached.checkedAtNanos <= OCCLUSION_CACHE_TTL_NANOS) {
                            occluded = cached.occluded;
                        } else if (occlusionTestsRemaining > 0) {
                            occluded = !OcclusionRaycaster.hasLineOfSight(world, cameraPos, center);
                            OCCLUSION_CACHE.put(entity.getId(), new OcclusionCacheEntry(captureNanos, cameraBlockX, cameraBlockY, cameraBlockZ, entityBlockKey, occluded));
                            TEMPORAL_CACHE.put(entity.getId(), new TemporalVisibilityCache.Entry(cameraCellKey, entityBlockKey, captureNanos, !occluded));
                            occlusionTestsRemaining--;
                        }
                    }
                }
            }
            entities.add(new EntitySample(entity.getId(), distSq, threshold, occluded));
        }

        return new CullingSnapshot(0L, captureNanos, entities, drainBlockEntitySamples(), profile);
    }

    private static List<BlockEntitySample> drainBlockEntitySamples() {
        ArrayList<BlockEntitySample> samples = new ArrayList<>();
        BlockEntitySample sample;
        while ((sample = PENDING_BLOCK_ENTITIES.poll()) != null) samples.add(sample);
        return samples;
    }

    private static double entityThreshold(Entity entity, SnapshotProfile profile) {
        double base = entity instanceof HostileEntity ? profile.hostileMobDistanceSq()
                : entity instanceof ArmorStandEntity ? profile.armorStandDistanceSq()
                : entity instanceof ItemEntity ? profile.droppedItemDistanceSq()
                : (entity instanceof PassiveEntity || entity instanceof AnimalEntity || entity instanceof WaterCreatureEntity || entity instanceof AmbientEntity)
                ? profile.passiveMobDistanceSq() : profile.miscEntityDistanceSq();
        if (profile.stutterRisk()) base *= 0.04D;
        if (profile.chunkLoadingBusy()) base *= 0.5D;
        if (profile.aggressiveShaderCulling() && profile.shaderPackActive()) base *= profile.shaderCullMultiplier();
        return base;
    }

    private static double blockEntityThreshold(int typeKey, SnapshotProfile profile) {
        double base = typeKey;
        if (profile.stutterRisk()) base = Math.min(base, 64.0D);
        if (profile.chunkLoadingBusy()) base *= 0.5D;
        if (profile.aggressiveShaderCulling() && profile.shaderPackActive()) base *= profile.shaderCullMultiplier();
        return base;
    }

    private static long blockKey(int x, int y, int z) {
        return ((x & 0x3ffffffL) << 38) | ((z & 0x3ffffffL) << 12) | (y & 0xfffL);
    }

    private record OcclusionCacheEntry(long checkedAtNanos, int cameraBlockX, int cameraBlockY, int cameraBlockZ, long entityBlockKey, boolean occluded) {}

    public record CullingSnapshot(long generation, long capturedAtNanos, List<EntitySample> entities, List<BlockEntitySample> blockEntities, SnapshotProfile profile) {
        public CullingSnapshot { entities = List.copyOf(entities); blockEntities = List.copyOf(blockEntities); }
        private CullingSnapshot withGeneration(long generation) { return new CullingSnapshot(generation, capturedAtNanos, entities, blockEntities, profile); }
    }
    public record EntitySample(int id, double distanceSq, double thresholdSq, boolean occluded) {}
    public record BlockEntitySample(long posHash, double distSq, int typeKey) {}
    public record SnapshotProfile(int passiveMobDistanceSq, int hostileMobDistanceSq, int armorStandDistanceSq, int droppedItemDistanceSq,
                                  int miscEntityDistanceSq, boolean stutterRisk, boolean chunkLoadingBusy, boolean aggressiveShaderCulling,
                                  boolean shaderPackActive, double shaderCullMultiplier) {
        private static SnapshotProfile capture() {
            return new SnapshotProfile(FastAccessConfig.PASSIVE_MOB_DISTANCE_SQ, FastAccessConfig.HOSTILE_MOB_DISTANCE_SQ,
                    FastAccessConfig.ARMOR_STAND_DISTANCE_SQ, FastAccessConfig.DROPPED_ITEM_DISTANCE_SQ, FastAccessConfig.MISC_ENTITY_DISTANCE_SQ,
                    FastAccessConfig.isStutterRisk, FastAccessConfig.chunkLoadingBusy, FastAccessConfig.AGGRESSIVE_SHADER_CULLING,
                    OpticoreCompat.isShaderPackActive(), FastAccessConfig.shaderCullMultiplier);
        }
    }
}
