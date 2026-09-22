package com.whitecloud233.herobrine_companion.mixin;

import com.whitecloud233.herobrine_companion.combat.poem.PoemMeleeHit;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PoemPlayerAttackMixin {
    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void poem$cutStrength(float partialTick, CallbackInfoReturnable<Float> ci) {
        float strength = PoemMeleeHit.strength((Player) (Object) this);
        if (strength >= 0) ci.setReturnValue(strength);
    }

    @Inject(method = "resetAttackStrengthTicker", at = @At("HEAD"), cancellable = true)
    private void poem$consumeChargeOnce(CallbackInfo ci) {
        if (PoemMeleeHit.strength((Player) (Object) this) >= 0) ci.cancel();
    }
}
