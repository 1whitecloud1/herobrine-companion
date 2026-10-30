package com.whitecloud233.herobrine_companion.entity.ai.goal;

import com.whitecloud233.herobrine_companion.api.compat.HeroEpicFightCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.combat.HeroCombatTargeting;
import com.whitecloud233.herobrine_companion.entity.ai.HeroCombatWeaponHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

import java.util.EnumSet;
import java.util.List;

public class HeroFallbackRangedAttackGoal extends Goal {
    private static final double SEARCH_RANGE = 24.0D;
    private static final double MOVE_SPEED = 1.1D;

    private final HeroEntity hero;
    private LivingEntity target;
    private int retargetCooldown;
    private int attackCooldown;
    private int useTicks;

    public HeroFallbackRangedAttackGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return isBattleModeActive();
    }

    @Override
    public boolean canContinueToUse() {
        return isBattleModeActive() && ((this.target != null && this.target.isAlive()) || this.hero.isUsingItem());
    }

    @Override
    public void start() {
        this.target = pickBattleTarget();
        this.retargetCooldown = 0;
        this.attackCooldown = 0;
        this.useTicks = 0;
        this.hero.setFloating(false);
        this.hero.setNoGravity(false);
        this.hero.setAggressive(true);
        this.hero.resetBattleCombatState();
    }

    @Override
    public void stop() {
        this.target = null;
        this.hero.setTarget(null);
        this.hero.stopUsingItem();
        this.hero.getNavigation().stop();
        this.hero.setAggressive(false);
        this.attackCooldown = 0;
        this.useTicks = 0;
        this.hero.resetBattleCombatState();
    }

    @Override
    public void tick() {
        LivingEntity prioritizedTarget = pickBattleTarget();
        if (prioritizedTarget != null) {
            this.target = prioritizedTarget;
        }

        if (this.retargetCooldown > 0) {
            this.retargetCooldown--;
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }

        if (!isValidTarget(this.target) || this.hero.distanceToSqr(this.target) > SEARCH_RANGE * SEARCH_RANGE) {
            this.target = findNearestHostile();
            this.retargetCooldown = 10;
        } else if (this.retargetCooldown <= 0) {
            LivingEntity nearbyBetterTarget = findNearestHostile();
            if (nearbyBetterTarget != null && nearbyBetterTarget != this.target
                    && this.hero.distanceToSqr(nearbyBetterTarget) + 4.0D < this.hero.distanceToSqr(this.target)) {
                this.target = nearbyBetterTarget;
            }
            this.retargetCooldown = 10;
        }

        this.hero.setTarget(this.target);
        clearSubmission(this.target);

        if (this.target == null) {
            this.hero.stopUsingItem();
            this.hero.getNavigation().stop();
            return;
        }

        ItemStack stack = this.hero.getMainHandItem();
        if (!HeroCombatWeaponHelper.isRangedLoadout(stack)) {
            this.hero.stopUsingItem();
            this.hero.getNavigation().stop();
            return;
        }

        boolean canSee = this.hero.hasLineOfSight(this.target);
        float attackRadius = HeroCombatWeaponHelper.getPreferredAttackRadius(stack);
        double attackRadiusSqr = attackRadius * attackRadius;
        double distanceSqr = this.hero.distanceToSqr(this.target);
        boolean inRange = distanceSqr <= attackRadiusSqr;

        this.hero.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (inRange && canSee) {
            this.hero.getNavigation().stop();
        } else {
            boolean pathStarted = this.hero.getNavigation().moveTo(this.target, MOVE_SPEED);
            if (!pathStarted) {
                this.hero.getMoveControl().setWantedPosition(this.target.getX(), this.target.getY(), this.target.getZ(), MOVE_SPEED);
            }
        }

        if (HeroCombatWeaponHelper.isCrossbowWeapon(stack)) {
            tickCrossbow(stack, canSee, inRange);
            return;
        }

        tickChargingWeapon(stack, canSee, inRange);
    }

    private void tickCrossbow(ItemStack stack, boolean canSee, boolean inRange) {
        if (net.minecraft.world.item.CrossbowItem.isCharged(stack)) {
            if (this.attackCooldown <= 0 && canSee && inRange && HeroCombatWeaponHelper.fireCrossbow(this.hero, this.target, stack)) {
                this.attackCooldown = HeroCombatWeaponHelper.getPostShotCooldown(stack);
            }
            return;
        }

        if (!this.hero.isUsingItem()) {
            if (this.attackCooldown <= 0 && canSee && inRange) {
                this.hero.startUsingItem(InteractionHand.MAIN_HAND);
                this.useTicks = 0;
            }
            return;
        }

        this.useTicks++;
        if (this.useTicks >= HeroCombatWeaponHelper.getChargeTicks(stack)) {
            this.hero.releaseUsingItem();
            this.hero.stopUsingItem();
            this.useTicks = 0;
            this.attackCooldown = 10;
        }
    }

    private void tickChargingWeapon(ItemStack stack, boolean canSee, boolean inRange) {
        if (!this.hero.isUsingItem()) {
            if (this.attackCooldown <= 0 && canSee && inRange) {
                this.hero.startUsingItem(InteractionHand.MAIN_HAND);
                this.useTicks = 0;
            }
            return;
        }

        this.useTicks++;
        if (this.useTicks >= HeroCombatWeaponHelper.getChargeTicks(stack) && canSee) {
            boolean fired = stack.getUseAnimation() == UseAnim.BOW && HeroCombatWeaponHelper.fireBowAtTarget(this.hero, this.target, stack, this.useTicks);
            if (!fired) {
                this.hero.releaseUsingItem();
                this.hero.swing(InteractionHand.MAIN_HAND);
            }
            this.hero.stopUsingItem();
            this.useTicks = 0;
            this.attackCooldown = HeroCombatWeaponHelper.getPostShotCooldown(stack);
        }
    }

    private boolean isBattleModeActive() {
        return this.hero.isBattleModeActive()
                && !this.hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)
                && !HeroEpicFightCompat.shouldUseEpicFightCombatAI(this.hero)
                && HeroCombatWeaponHelper.isRangedLoadout(this.hero)
                // 陪伴 + 战斗：清场后要把控制权还给跟随，别一直霸占着瞄准/移动。
                && (!this.hero.isCompanionMode() || HeroCombatTargeting.hasEngagedTarget(this.hero));
    }

    /** 目标获取已收敛到 {@link HeroCombatTargeting}（近战/远程共用同一份）。 */
    private LivingEntity pickBattleTarget() {
        return HeroCombatTargeting.pickBattleTarget(this.hero, this.target);
    }

    private LivingEntity findNearestHostile() {
        return HeroCombatTargeting.findNearestHostile(this.hero, this.target);
    }

    private boolean isValidTarget(LivingEntity candidate) {
        return HeroCombatTargeting.canHeroAttackTarget(this.hero, candidate);
    }

    private void clearSubmission(LivingEntity candidate) {
        HeroCombatTargeting.clearSubmission(candidate);
    }
}

