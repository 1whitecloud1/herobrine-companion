package com.whitecloud233.modid.herobrine_companion.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whitecloud233.modid.herobrine_companion.client.render.StandalonePoemRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class PoemLivingRendererMixin {
    @Shadow protected EntityModel<?> model;
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void poem$begin(LivingEntity entity, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        StandalonePoemRenderer.push(entity, model, partial);
    }
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void poem$end(LivingEntity entity, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        StandalonePoemRenderer.pop();
    }
    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V"), index = 3)
    private float poem$attackFacing(LivingEntity entity, PoseStack stack, float age, float bodyYaw, float partial) {
        // Body-yaw smoothing is client-only. Both views and the server instead use the same player yaw during a cut.
        // Avoid @ModifyArgs: its synthetic Args class cannot be resolved by this Forge module classloader at startup.
        return StandalonePoemRenderer.attackYaw(entity, bodyYaw, partial);
    }
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"))
    private void poem$layer(RenderLayer<?, ?> layer, PoseStack stack, MultiBufferSource buffers, int light,
                            net.minecraft.world.entity.Entity entity, float walk, float amount, float partial, float age, float yaw, float pitch) {
        StandalonePoemRenderer.renderLayer(layer, stack, buffers, light, (LivingEntity) entity, walk, amount, partial, age, yaw, pitch);
    }
}
