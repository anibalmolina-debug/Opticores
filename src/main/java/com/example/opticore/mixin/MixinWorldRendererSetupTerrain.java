package com.example.opticore.mixin;

import com.example.opticore.compat.OpticoreCompat;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=WorldRenderer.class,priority=1500)
public class MixinWorldRendererSetupTerrain {
    @Inject(method="setupTerrain",at=@At("HEAD"),require=0)
    private void opticore$capture(Camera camera,Frustum frustum,boolean hasForcedFrustum,boolean spectator,CallbackInfo ci){
        if(OpticoreCompat.isRenderingShadowPass()&&frustum!=null)OpticoreCompat.currentShadowFrustum=frustum;
    }
}
