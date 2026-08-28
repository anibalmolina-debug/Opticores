package com.example.opticore.mixin;

import com.example.opticore.compat.OpticoreCompat;
import com.example.opticore.util.CullWorker;
import com.example.opticore.util.CullingTracker;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class MixinBlockEntityRenderDispatcher {
    @Inject(method="render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",at=@At("HEAD"),cancellable=true)
    private <E extends BlockEntity> void opticore$cull(E blockEntity,float tickDelta,MatrixStack matrices,VertexConsumerProvider vertexConsumers,CallbackInfo ci){
        BlockPos pos=blockEntity.getPos();if(CullWorker.getCulledBlockEntityHashes().contains(pos.asLong())){CullingTracker.entitiesCulledThisFrame++;ci.cancel();return;}
        Box box=new Box(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY()+1,pos.getZ()+1);Frustum f=OpticoreCompat.isRenderingShadowPass()?OpticoreCompat.currentShadowFrustum:OpticoreCompat.currentFrustum;
        if(f!=null&&!f.isVisible(box)){CullingTracker.entitiesCulledThisFrame++;ci.cancel();}
    }
}
