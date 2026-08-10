package com.whitecloud233.modid.herobrine_companion.entity.ai.learning;

import com.whitecloud233.modid.herobrine_companion.compat.cooking.HeroCookingCompat;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroInvitationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class HeroDomesticActivityGoal extends Goal {
    private static final int REST_SEARCH_RADIUS = 10;
    private static final int COOK_SEARCH_RADIUS = 12;
    private static final int MAX_VERTICAL_OFFSET = 3;
    private static final int START_CHANCE = 600;
    private static final int MIN_COOLDOWN = 600;
    private static final int MAX_COOLDOWN = 1200;
    private static final int REST_DURATION_MIN = 20 * 30;
    private static final int REST_DURATION_MAX = 20 * 60;

    private final HeroEntity hero;

    private int cooldownTicks;
    private int activeAction = HeroInvitationHelper.ACTION_NONE;
    @Nullable private BlockPos targetPos;
    private int activeTicks;
    private boolean restSettled;

    public HeroDomesticActivityGoal(HeroEntity hero) {
        this.hero = hero;
        this.cooldownTicks = 120;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (this.activeAction != HeroInvitationHelper.ACTION_NONE) {
            return false;
        }
        if (isBlocked()) {
            return false;
        }
        if (isStateExcluded()) {
            return false;
        }
        if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
            return false;
        }
        if (this.hero.getRandom().nextInt(START_CHANCE) != 0) {
            return false;
        }

        BlockPos plannedRestPos = findNearestRestTarget();
        BlockPos plannedCookPos = findNearestCookTarget();
        if (plannedRestPos == null && plannedCookPos == null) {
            this.cooldownTicks = 120 + this.hero.getRandom().nextInt(160);
            return false;
        }

        if (plannedRestPos != null && plannedCookPos != null) {
            if (this.hero.getRandom().nextBoolean()) {
                prepareRest(plannedRestPos);
            } else {
                prepareCook(plannedCookPos);
            }
        } else if (plannedCookPos != null) {
            prepareCook(plannedCookPos);
        } else {
            prepareRest(plannedRestPos);
        }
        return this.targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.activeAction == HeroInvitationHelper.ACTION_NONE || this.targetPos == null) {
            return false;
        }
        if (isBlocked() || isStateExcluded()) {
            return false;
        }

        BlockPos invitedPos = this.hero.getInvitedPos();
        if (invitedPos == null || !invitedPos.equals(this.targetPos) || this.hero.getInvitedAction() != this.activeAction) {
            return false;
        }

        if (this.activeAction == HeroInvitationHelper.ACTION_COOK) {
            return HeroCookingCompat.isAutonomousCookingActive(this.hero, this.targetPos);
        }
        return true;
    }

    @Override
    public void start() {
        if (this.targetPos == null) {
            return;
        }

        if (this.activeAction == HeroInvitationHelper.ACTION_REST) {
            if (this.hero.isFloating()) {
                this.hero.setFloating(false);
            }
            this.hero.setNoGravity(false);
        } else if (this.activeAction == HeroInvitationHelper.ACTION_COOK
                && !HeroCookingCompat.beginAutonomousCooking(this.hero, this.targetPos)) {
            clearPlannedActivity();
            this.cooldownTicks = 80;
            return;
        }

        this.hero.setInvitedPos(this.targetPos);
        this.hero.setInvitedAction(this.activeAction);
        this.restSettled = false;

        if (this.activeAction == HeroInvitationHelper.ACTION_REST) {
            HeroDialogueHandler.onAutonomousRestStart(this.hero, this.hero.level().getBlockState(this.targetPos));
        }
    }

    @Override
    public void tick() {
        if (this.targetPos == null) {
            return;
        }

        if (this.activeAction == HeroInvitationHelper.ACTION_REST) {
            if (!this.restSettled) {
                this.restSettled = this.hero.isPassenger()
                        || this.hero.distanceToSqr(this.targetPos.getX() + 0.5D, this.targetPos.getY() + 0.5D, this.targetPos.getZ() + 0.5D) <= 4.0D;
                return;
            }

            if (this.activeTicks > 0) {
                this.activeTicks--;
            }
            if (this.activeTicks <= 0) {
                clearInvitation();
            }
        }
    }

    @Override
    public void stop() {
        if (this.activeAction == HeroInvitationHelper.ACTION_COOK) {
            HeroCookingCompat.clearAutonomousCooking(this.hero);
        }
        clearInvitation();
        clearPlannedActivity();
        this.cooldownTicks = MIN_COOLDOWN + this.hero.getRandom().nextInt(Math.max(1, MAX_COOLDOWN - MIN_COOLDOWN + 1));
    }

    private void prepareRest(@Nullable BlockPos pos) {
        this.activeAction = HeroInvitationHelper.ACTION_REST;
        this.targetPos = pos;
        this.activeTicks = REST_DURATION_MIN + this.hero.getRandom().nextInt(REST_DURATION_MAX - REST_DURATION_MIN + 1);
        this.restSettled = false;
    }

    private void prepareCook(@Nullable BlockPos pos) {
        this.activeAction = HeroInvitationHelper.ACTION_COOK;
        this.targetPos = pos;
        this.activeTicks = 0;
        this.restSettled = false;
    }

    private boolean isBlocked() {
        if (this.hero.isBattleModeActive() || this.hero.getTarget() != null || this.hero.getTradingPlayer() != null) {
            return true;
        }
        if (this.hero.getInvitedPos() != null && this.activeAction == HeroInvitationHelper.ACTION_NONE) {
            return true;
        }
        if (this.hero.isInWater()) {
            return true;
        }
        if (this.hero.getVehicle() != null && this.activeAction != HeroInvitationHelper.ACTION_REST) {
            return true;
        }
        if (this.hero.level().isClientSide) {
            return true;
        }
        if (this.hero.isCompanionMode()) {
            return true;
        }
        return false;
    }

    private boolean isStateExcluded() {
        SimpleNeuralNetwork.MindState state = this.hero.getMindState();
        return state == SimpleNeuralNetwork.MindState.JUDGE
                || state == SimpleNeuralNetwork.MindState.MAINTAINER;
    }

    @Nullable
    private BlockPos findNearestRestTarget() {
        BlockPos origin = this.hero.blockPosition();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-REST_SEARCH_RADIUS, -MAX_VERTICAL_OFFSET, -REST_SEARCH_RADIUS),
                origin.offset(REST_SEARCH_RADIUS, MAX_VERTICAL_OFFSET, REST_SEARCH_RADIUS))) {
            BlockPos immutablePos = pos.immutable();
            if (!HeroInvitationHelper.isRestTarget(this.hero.level(), immutablePos)) {
                continue;
            }
            double distance = this.hero.distanceToSqr(immutablePos.getX() + 0.5D, immutablePos.getY() + 0.5D, immutablePos.getZ() + 0.5D);
            if (distance < 2.0D || distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            bestPos = immutablePos;
        }
        return bestPos;
    }

    @Nullable
    private BlockPos findNearestCookTarget() {
        BlockPos origin = this.hero.blockPosition();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-COOK_SEARCH_RADIUS, -MAX_VERTICAL_OFFSET, -COOK_SEARCH_RADIUS),
                origin.offset(COOK_SEARCH_RADIUS, MAX_VERTICAL_OFFSET, COOK_SEARCH_RADIUS))) {
            BlockPos immutablePos = pos.immutable();
            if (!HeroInvitationHelper.isCookTarget(this.hero.level(), immutablePos)) {
                continue;
            }
            if (!HeroCookingCompat.hasAutonomousCookOptions(this.hero, immutablePos)) {
                continue;
            }
            double distance = this.hero.distanceToSqr(immutablePos.getX() + 0.5D, immutablePos.getY() + 0.5D, immutablePos.getZ() + 0.5D);
            if (distance < 2.0D || distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            bestPos = immutablePos;
        }
        return bestPos;
    }

    private void clearInvitation() {
        if (this.targetPos == null) {
            return;
        }
        if (this.hero.getInvitedPos() != null
                && this.targetPos.equals(this.hero.getInvitedPos())
                && this.hero.getInvitedAction() == this.activeAction) {
            this.hero.setInvitedPos(null);
            this.hero.setInvitedAction(HeroInvitationHelper.ACTION_NONE);
        }
    }

    private void clearPlannedActivity() {
        this.activeAction = HeroInvitationHelper.ACTION_NONE;
        this.targetPos = null;
        this.activeTicks = 0;
        this.restSettled = false;
    }
}
