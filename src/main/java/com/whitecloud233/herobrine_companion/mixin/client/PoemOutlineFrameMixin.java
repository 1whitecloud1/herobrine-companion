package com.whitecloud233.herobrine_companion.mixin.client;

import com.whitecloud233.herobrine_companion.client.render.PoemEmissiveEffect;
import com.whitecloud233.herobrine_companion.client.render.PoemOutlineEffect;
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

    // The caller boundary runs after Iris' LevelRenderer RETURN callbacks and before
    // vanilla clears the scene depth to render its hand. Keep world occlusion intact.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            shift = At.Shift.AFTER))
    private void poem$compositeOutlines(CallbackInfo ci) {
        PoemEmissiveEffect.finishWorldFrame();
        PoemOutlineEffect.finishWorldFrame();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void poem$endOutlines(CallbackInfo ci) {
        PoemEmissiveEffect.discardWorldFrame();
        PoemOutlineEffect.discardWorldFrame();
    }
}
