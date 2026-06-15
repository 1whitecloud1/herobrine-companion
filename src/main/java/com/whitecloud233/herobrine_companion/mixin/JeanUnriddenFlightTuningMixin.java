package com.whitecloud233.herobrine_companion.mixin;

import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyMemberType;
import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyMembers;
import com.whitecloud233.herobrine_companion.entity.family.JeanCombatResponseService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragon.class)
public abstract class JeanUnriddenFlightTuningMixin extends Mob {
    @Unique
    private static final double HEROBRINE_COMPANION_UNRIDDEN_FLIGHT_SPEED_SCALE = 0.45D;
    @Unique
    private static final float HEROBRINE_COMPANION_UNRIDDEN_FLAP_SPEED_SCALE = 0.42F;
    @Unique
    private double herobrineCompanion$unriddenStartX;
    @Unique
    private double herobrineCompanion$unriddenStartY;
    @Unique
    private double herobrineCompanion$unriddenStartZ;
    @Unique
    private float herobrineCompanion$unriddenStartFlapTime;

    @Shadow
    public float oFlapTime;

    @Shadow
    public float flapTime;

    protected JeanUnriddenFlightTuningMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void herobrineCompanion$captureUnriddenFlightStart(CallbackInfo ci) {
        EnderDragon dragon = (EnderDragon) (Object) this;
        if (!herobrineCompanion$shouldTuneUnriddenFlight(dragon)) {
            return;
        }

        this.herobrineCompanion$unriddenStartX = dragon.getX();
        this.herobrineCompanion$unriddenStartY = dragon.getY();
        this.herobrineCompanion$unriddenStartZ = dragon.getZ();
        this.herobrineCompanion$unriddenStartFlapTime = this.flapTime;
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

        double dx = dragon.getX() - this.herobrineCompanion$unriddenStartX;
        double dy = dragon.getY() - this.herobrineCompanion$unriddenStartY;
        double dz = dragon.getZ() - this.herobrineCompanion$unriddenStartZ;
        dragon.setPos(
                this.herobrineCompanion$unriddenStartX + dx * HEROBRINE_COMPANION_UNRIDDEN_FLIGHT_SPEED_SCALE,
                this.herobrineCompanion$unriddenStartY + dy * HEROBRINE_COMPANION_UNRIDDEN_FLIGHT_SPEED_SCALE,
                this.herobrineCompanion$unriddenStartZ + dz * HEROBRINE_COMPANION_UNRIDDEN_FLIGHT_SPEED_SCALE
        );
        dragon.setDeltaMovement(dragon.getDeltaMovement().scale(HEROBRINE_COMPANION_UNRIDDEN_FLIGHT_SPEED_SCALE));

        float flapDelta = this.flapTime - this.herobrineCompanion$unriddenStartFlapTime;
        this.oFlapTime = this.herobrineCompanion$unriddenStartFlapTime;
        this.flapTime = this.herobrineCompanion$unriddenStartFlapTime
                + flapDelta * HEROBRINE_COMPANION_UNRIDDEN_FLAP_SPEED_SCALE;
    }

    @Unique
    private boolean herobrineCompanion$shouldTuneUnriddenFlight(EnderDragon dragon) {
        return HerobrineFamilyMembers.isFamilyMember(dragon, HerobrineFamilyMemberType.JEAN)
                && !dragon.isSilent()
                && !JeanCombatResponseService.shouldUseOriginalUnriddenFlightSpeed(dragon)
                && !herobrineCompanion$hasPlayerPassenger(dragon);
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
