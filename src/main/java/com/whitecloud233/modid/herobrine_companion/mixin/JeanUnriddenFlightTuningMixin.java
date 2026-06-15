package com.whitecloud233.modid.herobrine_companion.mixin;

import com.whitecloud233.modid.herobrine_companion.entity.family.HerobrineFamilyMemberType;
import com.whitecloud233.modid.herobrine_companion.entity.family.HerobrineFamilyMembers;
import com.whitecloud233.modid.herobrine_companion.entity.family.JeanCombatResponseService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragon.class)
public abstract class JeanUnriddenFlightTuningMixin extends Mob {
    @Unique
    private static final String HEROBRINE_COMPANION_JEAN_FRONT_HERO_TAG = "HerobrineCompanionJeanFrontHero";
    @Unique
    private static final String HEROBRINE_COMPANION_JEAN_PLAYER_RIDDEN_TAG = "HerobrineCompanionJeanPlayerRidden";
    @Unique
    private static final String HEROBRINE_COMPANION_JEAN_ENTITY_TAG = "herobrine_companion_jean";
    @Unique
    private static final double HEROBRINE_COMPANION_UNRIDDEN_FLIGHT_SPEED_SCALE = 0.45D;
    @Unique
    private static final float HEROBRINE_COMPANION_UNRIDDEN_FLAP_SPEED_SCALE = 0.42F;

    @Shadow
    public float oFlapTime;

    @Shadow
    public float flapTime;

    protected JeanUnriddenFlightTuningMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @ModifyArg(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"
            ),
            index = 1
    )
    private Vec3 herobrineCompanion$tuneUnriddenFlightMovement(Vec3 movement) {
        EnderDragon dragon = (EnderDragon) (Object) this;
        if (!herobrineCompanion$shouldTuneUnriddenFlight(dragon)) {
            return movement;
        }
        return movement.scale(HEROBRINE_COMPANION_UNRIDDEN_FLIGHT_SPEED_SCALE);
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void herobrineCompanion$tuneUnriddenFlightEnd(CallbackInfo ci) {
        EnderDragon dragon = (EnderDragon) (Object) this;
        if (JeanCombatResponseService.enforcePursuitFlight(dragon)) {
            return;
        }
        if (!herobrineCompanion$shouldTuneUnriddenFlight(dragon)) {
            return;
        }

        dragon.setDeltaMovement(dragon.getDeltaMovement().scale(HEROBRINE_COMPANION_UNRIDDEN_FLIGHT_SPEED_SCALE));

        float flapDelta = this.flapTime - this.oFlapTime;
        this.flapTime = this.oFlapTime + flapDelta * HEROBRINE_COMPANION_UNRIDDEN_FLAP_SPEED_SCALE;
    }

    @Unique
    private boolean herobrineCompanion$shouldTuneUnriddenFlight(EnderDragon dragon) {
        return herobrineCompanion$isJean(dragon)
                && !dragon.isNoAi()
                && !JeanCombatResponseService.shouldUseOriginalUnriddenFlightSpeed(dragon)
                && !dragon.getPersistentData().hasUUID(HEROBRINE_COMPANION_JEAN_FRONT_HERO_TAG)
                && !dragon.getPersistentData().getBoolean(HEROBRINE_COMPANION_JEAN_PLAYER_RIDDEN_TAG)
                && !herobrineCompanion$hasPlayerPassenger(dragon);
    }

    @Unique
    private boolean herobrineCompanion$isJean(EnderDragon dragon) {
        return HerobrineFamilyMembers.isFamilyMember(dragon, HerobrineFamilyMemberType.JEAN)
                || dragon.getTags().contains(HEROBRINE_COMPANION_JEAN_ENTITY_TAG)
                || dragon.hasCustomName() && "jean".equalsIgnoreCase(dragon.getCustomName().getString());
    }

    @Unique
    private boolean herobrineCompanion$hasPlayerPassenger(EnderDragon dragon) {
        for (Entity passenger : dragon.getPassengers()) {
            if (passenger instanceof Player) {
                return true;
            }
        }
        return false;
    }
}
