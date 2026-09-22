package com.whitecloud233.modid.herobrine_companion.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whitecloud233.modid.herobrine_companion.client.render.StandalonePoemRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class PoemHeldItemMixin {
    @Shadow @Final private ItemInHandRenderer itemInHandRenderer;
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void poem$held(LivingEntity entity, ItemStack item, ItemDisplayContext display, HumanoidArm arm,
                            PoseStack stack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (StandalonePoemRenderer.renderHeld(entity, item, display, arm, stack, buffers, light, itemInHandRenderer)) ci.cancel();
    }
}
