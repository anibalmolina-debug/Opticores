package com.example.opticore.mixin;

import com.example.opticore.compat.OpticoreCompat;
import com.example.opticore.util.CullWorker;
import com.example.opticore.util.CullingTracker;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private <E extends Entity> void opticore$cull(E entity,double x,double y,double z,float yaw,float tickDelta,MatrixStack matrices,VertexConsumerProvider vertexConsumers,int light,CallbackInfo ci){
        if(entity instanceof PlayerEntity)return;
        IntOpenHashSet culled=CullWorker.getCulledEntityIds();
        if(culled.contains(entity.getId())){CullingTracker.entitiesCulledThisFrame++;ci.cancel();return;}
        Frustum frustum=OpticoreCompat.isRenderingShadowPass()?OpticoreCompat.currentShadowFrustum:OpticoreCompat.currentFrustum;
        if(frustum!=null&&!frustum.isVisible(entity.getBoundingBox())){CullingTracker.entitiesCulledThisFrame++;ci.cancel();}
    }
}
