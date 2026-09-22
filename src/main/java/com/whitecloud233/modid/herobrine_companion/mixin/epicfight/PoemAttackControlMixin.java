package com.whitecloud233.modid.herobrine_companion.mixin.epicfight;

import com.whitecloud233.modid.herobrine_companion.client.event.PoemScytheInputEvents;
import com.whitecloud233.modid.herobrine_companion.combat.PoemAttackInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feed a released tap into EF's native combo buffer without its shared hold skill. */
@Pseudo
@Mixin(targets = "yesman.epicfight.client.events.engine.ControlEngine", remap = false)
public abstract class PoemAttackControlMixin {
    @Shadow private boolean attackLightPressToggle;
    @Shadow private boolean weaponInnatePressToggle;
    @Shadow private int weaponInnatePressCounter;
    @Shadow public abstract void releaseAllServedKeys();

    @Inject(method = "handleEpicFightKeyMappings", at = @At("HEAD"))
    private void herobrineCompanion$resolvePoemAttack(CallbackInfo ci) {
        int control = PoemScytheInputEvents.takeEngineControl();
        if ((control & PoemAttackInput.CLEAR_ENGINE_INPUT) != 0) {
            releaseAllServedKeys();
            attackLightPressToggle = false;
            weaponInnatePressToggle = false;
            weaponInnatePressCounter = 0;
        }
        if ((control & PoemAttackInput.REPLAY_LIGHT_ATTACK) != 0) attackLightPressToggle = true;
    }

    @Inject(method = "maybeAttack", at = @At("HEAD"), cancellable = true)
    private void herobrineCompanion$ownCapturedAttack(CallbackInfo ci) {
        if (PoemScytheInputEvents.blocksNativeAttack()) ci.cancel();
    }

    @Inject(method = "handleSeparateWeaponInnateSkill", at = @At("HEAD"), cancellable = true)
    private void herobrineCompanion$ownSharedHold(CallbackInfo ci) {
        if (PoemScytheInputEvents.blocksNativeInnate()) ci.cancel();
    }

    @Inject(method = "maybeGuard", at = @At("HEAD"), cancellable = true)
    private void herobrineCompanion$deferSharedUse(CallbackInfo ci) {
        if (PoemScytheInputEvents.blocksNativeGuard()) ci.cancel();
    }
}
