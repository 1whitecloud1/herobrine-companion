package com.whitecloud233.modid.herobrine_companion.mixin.client;

import com.whitecloud233.modid.herobrine_companion.client.render.PoemOutlineEffect;
import com.whitecloud233.modid.herobrine_companion.client.render.PoemEmissiveEffect;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class PoemOutlineFrameMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void poem$beginOutlines(CallbackInfo ci) {
        PoemOutlineEffect.beginWorldFrame();
        PoemEmissiveEffect.beginWorldFrame();
    }

    // Oculus renders both hand passes and its final composite inside LevelRenderer.
    // Intercept the caller: its RETURN injectors have all finished, but vanilla has
    // not yet cleared the scene depth for renderItemInHand. AFTER_LEVEL is later too.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            shift = At.Shift.AFTER))
    private void poem$compositeOutlines(CallbackInfo ci) {
        PoemEmissiveEffect.finishWorldFrame();
        PoemOutlineEffect.finishWorldFrame();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void poem$endOutlines(CallbackInfo ci) {
        PoemOutlineEffect.discardWorldFrame();
        PoemEmissiveEffect.discardWorldFrame();
    }
}
