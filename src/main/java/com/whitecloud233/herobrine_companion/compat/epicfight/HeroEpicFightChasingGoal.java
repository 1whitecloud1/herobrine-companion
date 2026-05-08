package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

final class HeroEpicFightChasingGoal extends Goal {
    private final HeroEpicFightPatch patch;
    private final HeroEntity hero;
    private final double speedModifier;
    private final double stopDistanceSqr;
    private final double resumeDistanceSqr;
    private int pathUpdateTicks;
    private boolean holdingMeleeRange;

    HeroEpicFightChasingGoal(HeroEpicFightPatch patch, HeroEntity hero, double speedModifier) {
        this(patch, hero, speedModifier, 0.0D);
    }

    HeroEpicFightChasingGoal(HeroEpicFightPatch patch, HeroEntity hero, double speedModifier, double stopDistance) {
        this.patch = patch;
        this.hero = hero;
        this.speedModifier = speedModifier;
        double normalizedStopDistance = stopDistance > 0.0D ? stopDistance : 2.0D;
        double resumeDistance = normalizedStopDistance + 0.85D;
        this.stopDistanceSqr = normalizedStopDistance * normalizedStopDistance;
        this.resumeDistanceSqr = resumeDistance * resumeDistance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public void start() {
        this.pathUpdateTicks = 0;
        this.holdingMeleeRange = false;
    }

    @Override
    public boolean canUse() {
        return this.hasValidTarget();
    }

    @Override
    public boolean canContinueToUse() {
        return this.hasValidTarget();
    }

    @Override
    public void stop() {
        this.hero.getNavigation().stop();
        this.pathUpdateTicks = 0;
        this.holdingMeleeRange = false;
    }

    @Override
    public void tick() {
        LivingEntity target = this.hero.getTarget();
        if (target == null) {
            return;
        }

        this.hero.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distanceSqr = this.hero.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean insideStopDistance = this.stopDistanceSqr <= 0.0D || distanceSqr <= this.stopDistanceSqr;
        if (this.holdingMeleeRange && (this.resumeDistanceSqr <= 0.0D || distanceSqr <= this.resumeDistanceSqr)) {
            this.hero.getNavigation().stop();
            return;
        }

        if (insideStopDistance && this.patch.shouldStopChasingForMelee(target)) {
            this.holdingMeleeRange = true;
            this.hero.getNavigation().stop();
            return;
        }

        this.holdingMeleeRange = false;
        if (this.pathUpdateTicks > 0) {
            this.pathUpdateTicks--;
            return;
        }

        this.pathUpdateTicks = 4;
        boolean pathStarted = this.hero.getNavigation().moveTo(target, this.speedModifier);
        if (!pathStarted || this.hero.getNavigation().isDone()) {
            this.hero.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), this.speedModifier);
        }
    }

    private boolean hasValidTarget() {
        LivingEntity target = this.hero.getTarget();
        return this.hero.isBattleModeActive()
                && target != null
                && target.isAlive()
                && !target.isRemoved();
    }
}

