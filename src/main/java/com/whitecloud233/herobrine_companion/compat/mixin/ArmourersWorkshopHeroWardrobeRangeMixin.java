package com.whitecloud233.herobrine_companion.compat.mixin;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.util.HeroMenuValidity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps AW's native Hero wardrobe open while Hero remains in the player's loaded chunks. */
@Pseudo
@Mixin(targets = "moe.plushie.armourers_workshop.core.menu.SkinWardrobeMenu")
public abstract class ArmourersWorkshopHeroWardrobeRangeMixin {

    @Shadow @Final private Player player;

    // AW <= 3.2.x overrides stillValid directly; 3.3+ moves the body into abi$stillValid
    // behind its compat layer, so both names must be accepted.
    @Redirect(
            method = {"stillValid", "abi$stillValid"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;distanceToSqr(DDD)D"))
    private double herobrineCompanion$useLoadedChunkRange(Entity target, double x, double y, double z) {
        if (target instanceof HeroEntity hero) {
            return HeroMenuValidity.isHeroInPlayerLoadedArea(hero, this.player)
                    ? 0.0D
                    : Double.POSITIVE_INFINITY;
        }
        return target.distanceToSqr(x, y, z);
    }
}
