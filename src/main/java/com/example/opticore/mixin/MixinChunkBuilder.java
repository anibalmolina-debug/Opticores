package com.example.opticore.mixin;

import com.example.opticore.chunk.VBOUploadBudget;
import com.example.opticore.profiler.MicroProfiler;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measures chunk meshing and upload work without cancelling required work. */
@Mixin(ChunkBuilder.class)
public class MixinChunkBuilder {
    private static final ThreadLocal<Long> MESH_START = new ThreadLocal<>();

    @Inject(method = "rebuild(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;Lnet/minecraft/client/render/chunk/ChunkRendererRegionBuilder;)V", at = @At("HEAD"), require = 0)
    private void opticores$beginMeshing(ChunkBuilder.BuiltChunk chunk, net.minecraft.client.render.chunk.ChunkRendererRegionBuilder builder, CallbackInfo ci) { MESH_START.set(System.nanoTime()); }

    @Inject(method = "rebuild(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;Lnet/minecraft/client/render/chunk/ChunkRendererRegionBuilder;)V", at = @At("RETURN"), require = 0)
    private void opticores$finishMeshing(ChunkBuilder.BuiltChunk chunk, net.minecraft.client.render.chunk.ChunkRendererRegionBuilder builder, CallbackInfo ci) {
        Long start = MESH_START.get();
        MESH_START.remove();
        if (start != null) MicroProfiler.recordMeshingTime(System.nanoTime() - start);
    }

    @Inject(method = "upload()V", at = @At("HEAD"), require = 0)
    private void opticores$beginUpload(CallbackInfo ci) { VBOUploadBudget.beginUpload(); }

    @Inject(method = "upload()V", at = @At("RETURN"), require = 0)
    private void opticores$finishUpload(CallbackInfo ci) { VBOUploadBudget.completeUpload(true); }
}
