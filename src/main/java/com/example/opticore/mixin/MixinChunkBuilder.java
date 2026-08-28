package com.example.opticore.mixin;

import com.example.opticore.chunk.VBOUploadBudget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.render.chunk.ChunkBuilder;

/** Measures vanilla chunk uploads without cancelling or dropping them. */
@Mixin(ChunkBuilder.class)
public class MixinChunkBuilder {
    @Inject(method = "upload", at = @At("HEAD"), require = 0)
    private void opticore$beginUpload(CallbackInfoReturnable<Boolean> cir) { VBOUploadBudget.beginUpload(); }
    @Inject(method = "upload", at = @At("RETURN"), require = 0)
    private void opticore$finishUpload(CallbackInfoReturnable<Boolean> cir) { VBOUploadBudget.completeUpload(cir.getReturnValue()); }
}
