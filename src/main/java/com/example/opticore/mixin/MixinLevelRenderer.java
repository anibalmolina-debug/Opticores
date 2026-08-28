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
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldRenderer.class, priority = 1500)
public abstract class MixinLevelRenderer {
    @Shadow public abstract ChunkBuilder getChunkBuilder();

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void opticores$beginFrame(RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera,
                                      GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager,
                                      Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        ChunkBuilder chunkBuilder = getChunkBuilder();
        int scheduled = chunkBuilder == null ? 0 : chunkBuilder.getScheduledTaskCount();
        int uploads = chunkBuilder == null ? 0 : chunkBuilder.getChunksToUpload();
        FastAccessConfig.chunkLoadingBusy = scheduled > 0 || uploads > 0;
        FastAccessConfig.isStutterRisk = FastAccessConfig.ENABLE_LOAD_STABILIZER && (scheduled > 50 || uploads > 50);
        ChunkThrottleManager.updateBudget();
        ChunkThrottleManager.resetFrameCounter();
        CullingTracker.reset();
        VBOUploadBudget.resetFrame();
        AdaptiveWorkScheduler.beginFrame();
        DynamicQualityScaler.tick();
        ShadowFrustumCuller.setShadowDistanceCap(DynamicQualityScaler.getShadowDistanceMultiplier());
        ShadowFrustumCuller.invalidate();
        OpticoreCompat.currentShadowFrustum = null;
        OpticoreCompat.currentFrustum = new net.minecraft.client.render.Frustum(positionMatrix, projectionMatrix);
        OpticoreCompat.currentFrustum.setPosition(camera.getPos().x, camera.getPos().y, camera.getPos().z);
    }
}
