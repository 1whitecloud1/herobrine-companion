package com.whitecloud233.herobrine_companion.compat.mixin.accessories;

import com.whitecloud233.herobrine_companion.compat.accessories.HeroAccessoriesCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shows empty Accessories slots in the native screen when its target is Hero. */
@Pseudo
@Mixin(targets = {
        "io.wispforest.accessories.menu.variants.AccessoriesMenu",
        "io.wispforest.accessories.menu.variants.AccessoriesExperimentalMenu"
}, remap = false)
public abstract class AccessoriesHeroUnusedSlotsMenuMixin {

    @Inject(method = "areUnusedSlotsShown", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void herobrineCompanion$showHeroUnusedSlots(CallbackInfoReturnable<Boolean> cir) {
        if (HeroAccessoriesCompat.isHeroAccessoriesMenu(this)) {
            cir.setReturnValue(true);
        }
    }
}
