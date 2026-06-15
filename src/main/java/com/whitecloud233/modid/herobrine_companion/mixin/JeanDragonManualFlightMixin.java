package com.whitecloud233.modid.herobrine_companion.mixin;

import com.whitecloud233.modid.herobrine_companion.entity.family.HerobrineFamilyMemberType;
import com.whitecloud233.modid.herobrine_companion.entity.family.HerobrineFamilyMembers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragon.class)
public abstract class JeanDragonManualFlightMixin extends Mob {
    private static final String HEROBRINE_COMPANION_JEAN_FRONT_HERO_TAG = "HerobrineCompanionJeanFrontHero";

    protected JeanDragonManualFlightMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void herobrineCompanion$skipJeanAiDuringManualFlight(CallbackInfo ci) {
        EnderDragon dragon = (EnderDragon) (Object) this;
        if (!HerobrineFamilyMembers.isFamilyMember(dragon, HerobrineFamilyMemberType.JEAN)) {
            return;
        }

        for (Entity passenger : dragon.getPassengers()) {
            if (passenger instanceof Player) {
                dragon.setDeltaMovement(0.0D, 0.0D, 0.0D);
                ci.cancel();
                return;
            }
        }

        if (dragon.getPersistentData().hasUUID(HEROBRINE_COMPANION_JEAN_FRONT_HERO_TAG)) {
            // Keep dragon parts ticking during the boarding pose so right-click and attack targets stay live.
            dragon.setDeltaMovement(0.0D, 0.0D, 0.0D);
        }
    }
}
