package com.example.opticore.mixin;

import com.example.opticore.chunk.AdaptiveWorkScheduler;
import com.example.opticore.chunk.ChunkThrottleManager;
import com.example.opticore.chunk.DynamicQualityScaler;
import com.example.opticore.chunk.ShadowFrustumCuller;
import com.example.opticore.chunk.VBOUploadBudget;
import com.example.opticore.compat.OpticoreCompat;
import com.example.opticore.config.FastAccessConfig;
import com.example.opticore.util.CullingTracker;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=WorldRenderer.class,priority=1500)
public abstract class MixinLevelRenderer {
    @Shadow public abstract String getChunksDebugString();
    @Inject(method="render",at=@At("HEAD"),require=0)
    private void opticore$begin(RenderTickCounter tickCounter,boolean renderBlockOutline,Camera camera,GameRenderer gameRenderer,LightmapTextureManager lightmapTextureManager,Matrix4f positionMatrix,Matrix4f projectionMatrix,CallbackInfo ci){
        FastAccessConfig.chunkLoadingBusy=ChunkThrottleManager.compilationsThisFrame>0;
        if(FastAccessConfig.ENABLE_LOAD_STABILIZER){
            int pending=0,upload=0;String stats=getChunksDebugString();
            try{int p=stats.indexOf("p: ");if(p>=0){int e=stats.indexOf(',',p);pending=Integer.parseInt(stats.substring(p+3,e<0?stats.length():e).trim());}int u=stats.indexOf("u: ");if(u>=0){int e=stats.indexOf(',',u);upload=Integer.parseInt(stats.substring(u+3,e<0?stats.length():e).trim());}}catch(RuntimeException ignored){}
            if(pending>50||upload>50)FastAccessConfig.isStutterRisk=true;else if(pending<10&&upload<10)FastAccessConfig.isStutterRisk=false;
        }else FastAccessConfig.isStutterRisk=false;
        ChunkThrottleManager.updateBudget();ChunkThrottleManager.resetFrameCounter();CullingTracker.reset();VBOUploadBudget.resetFrame();AdaptiveWorkScheduler.beginFrame();DynamicQualityScaler.tick();ShadowFrustumCuller.setShadowDistanceCap(DynamicQualityScaler.getShadowDistanceMultiplier());ShadowFrustumCuller.invalidate();
        OpticoreCompat.currentFrustum=new Frustum(positionMatrix,projectionMatrix);OpticoreCompat.currentFrustum.setPosition(camera.getPos().x,camera.getPos().y,camera.getPos().z);
    }
}
