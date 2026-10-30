package com.whitecloud233.herobrine_companion.compat.mixin.accessories;

import com.whitecloud233.herobrine_companion.compat.accessories.HeroAccessoriesClientCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the Accessories experimental screen from hiding Hero's empty native slots. */
@Pseudo
@Mixin(targets = "io.wispforest.accessories.client.gui.AccessoriesExperimentalScreen", remap = false)
public abstract class AccessoriesHeroUnusedSlotsScreenMixin {

    @Inject(method = "showUnusedSlots", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void herobrineCompanion$showHeroUnusedSlots(CallbackInfoReturnable<Boolean> cir) {
        if (HeroAccessoriesClientCompat.isHeroAccessoriesScreen(this)) {
            cir.setReturnValue(true);
        }
    }
}
