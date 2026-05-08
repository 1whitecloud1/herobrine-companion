package com.whitecloud233.herobrine_companion.entity.ai.goal;

import com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.HeroCombatWeaponHelper;
import com.whitecloud233.herobrine_companion.entity.ai.combat.HeroCombatPlanner;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class HeroBattleStanceGoal extends Goal {
    private static final double SEARCH_RANGE = 24.0D;
    private static final double MOVE_SPEED = 1.35D;
    private static final int DEFAULT_ACTION_DURATION = 8;
    private static final int DEFAULT_HIT_FRAME = 4;

    private final HeroEntity hero;
    private LivingEntity target;
    private int retargetCooldown;
    private int attackRecoveryTicks;
    private int comboGraceTicks;
    private int queuedAttackAction;
    private boolean hitApplied;

    public HeroBattleStanceGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return isBattleModeActive();
    }

    @Override
    public boolean canContinueToUse() {
        return isBattleModeActive();
    }

    @Override
    public void start() {
        this.retargetCooldown = 0;
        this.attackRecoveryTicks = 0;
        this.comboGraceTicks = 0;
        this.queuedAttackAction = HeroEntity.BATTLE_ACTION_IDLE;
        this.hitApplied = false;
        this.hero.setFloating(false);
        this.hero.setNoGravity(false);
        this.hero.setAggressive(true);
        this.target = pickBattleTarget();
        this.hero.resetBattleCombatState();
    }

    @Override
    public void stop() {
        this.target = null;
        this.hero.setTarget(null);
        this.hero.getNavigation().stop();
        this.hitApplied = false;
        this.attackRecoveryTicks = 0;
        this.comboGraceTicks = 0;
        this.queuedAttackAction = HeroEntity.BATTLE_ACTION_IDLE;
        this.hero.setAggressive(false);
        this.hero.resetBattleCombatState();
    }

    @Override
    public void tick() {
        this.hero.tickBattleBufferedAction();

        LivingEntity prioritizedTarget = pickBattleTarget();
        if (prioritizedTarget != null) {
            this.target = prioritizedTarget;
        }

        if (this.retargetCooldown > 0) {
            this.retargetCooldown--;
        }
        if (this.attackRecoveryTicks > 0) {
            this.attackRecoveryTicks--;
        }
        if (this.comboGraceTicks > 0) {
            this.comboGraceTicks--;
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
            this.hero.getNavigation().stop();
            this.hitApplied = false;
            this.attackRecoveryTicks = 0;
            this.comboGraceTicks = 0;
            this.queuedAttackAction = HeroEntity.BATTLE_ACTION_IDLE;
            this.hero.resetBattleActionTimeline();
            return;
        }

        this.hero.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (isPerformingAttack()) {
            tickAttackTimeline();
            return;
        }

        HeroCombatPlanner.CombatTuning tuning = this.getFallbackCombatTuning(this.target);
        HeroCombatPlanner.queuePreferredFollowUp(this.hero, this.target, tuning);

        if (HeroCombatPlanner.canStartMeleeAttack(this.hero, this.target, getAttackReachSqr(this.target), 3)
                && HeroCombatPlanner.prefersAction(this.hero, this.target, tuning, HeroCombatPlanner.PlannedAction.LIGHT_COMBO)) {
            this.hero.getNavigation().stop();
            this.hero.setBattleActionState(HeroEntity.BATTLE_ACTION_IDLE);
            this.hero.setBattleActionTicks(0);
            if (this.attackRecoveryTicks <= 0) {
                startNextAttackAction(this.resolveOpeningAttackAction());
            }
        } else {
            moveTowardsTarget(MOVE_SPEED);
        }
    }

    private boolean isBattleModeActive() {
        return this.hero.isBattleModeActive()
                && !this.hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)
                && !HeroEpicFightCompat.shouldUseEpicFightCombatAI(this.hero)
                && !HeroCombatWeaponHelper.isRangedLoadout(this.hero);
    }

    private boolean isPerformingAttack() {
        int action = this.hero.getBattleActionState();
        return action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1 || action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2;
    }

    private LivingEntity pickBattleTarget() {
        LivingEntity currentTarget = this.hero.getTarget();
        return isValidTarget(currentTarget) ? currentTarget : this.target;
    }

    private void startNextAttackAction(int action) {
        int normalizedAction = action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2 ? HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2 : HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1;
        this.hero.setBattleComboStep(normalizedAction == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2 ? 2 : 1);
        this.hero.beginBattleAction(normalizedAction);
        this.hero.swing(InteractionHand.MAIN_HAND);
        this.queuedAttackAction = HeroEntity.BATTLE_ACTION_IDLE;
        this.comboGraceTicks = 0;
        this.hitApplied = false;
    }

    private void tickAttackTimeline() {
        int action = this.hero.getBattleActionState();
        int ticks = this.hero.getBattleActionTicks() + 1;
        int totalDuration = getActionDuration(action);
        int hitFrame = getHitFrame(action);
        int chainStartTick = getChainStartTick(action, totalDuration, hitFrame);
        double attackReachSqr = this.target != null ? getAttackReachSqr(this.target) : 0.0D;

        this.hero.setBattleActionTicks(ticks);

        if (this.target != null && isValidTarget(this.target) && ticks < hitFrame && !this.hero.hasLineOfSight(this.target)) {
            moveTowardsTarget(MOVE_SPEED * 1.05D);
        } else if (this.target != null && isValidTarget(this.target) && ticks <= hitFrame + 1
                && HeroCombatPlanner.predictedDistanceSqr(this.hero, this.target, Math.max(1, hitFrame - ticks + 1), 0.35D) > attackReachSqr * 0.82D) {
            moveTowardsTarget(MOVE_SPEED * 0.95D);
        } else {
            this.hero.getNavigation().stop();
        }

        if (action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1 && ticks >= chainStartTick && this.target != null && isValidTarget(this.target)) {
            HeroCombatPlanner.CombatTuning tuning = this.getFallbackCombatTuning(this.target);
            HeroCombatPlanner.queuePreferredFollowUp(this.hero, this.target, tuning);
            if (HeroCombatPlanner.canQueueComboFollowUp(this.hero, this.target, attackReachSqr, 3)
                    && this.hero.getBattleBufferedAction() == HeroEntity.BATTLE_BUFFER_LIGHT) {
                this.queuedAttackAction = HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2;
                this.comboGraceTicks = 4;
            }
        }

        if (!this.hitApplied && ticks >= hitFrame) {
            if (this.target != null
                    && isValidTarget(this.target)
                    && HeroCombatPlanner.canStartMeleeAttack(this.hero, this.target, attackReachSqr, 0)) {
                this.hero.doHurtTarget(this.target);
            }
            this.hitApplied = true;
        }

        if (action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1
                && this.queuedAttackAction != HeroEntity.BATTLE_ACTION_IDLE
                && ticks >= Math.max(chainStartTick, totalDuration - 1)) {
            startNextAttackAction(this.queuedAttackAction);
            return;
        }

        if (ticks >= totalDuration) {
            this.hitApplied = false;
            if (this.target != null && isValidTarget(this.target)) {
                if (HeroCombatPlanner.canStartMeleeAttack(this.hero, this.target, attackReachSqr, 1)) {
                    if (action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1
                            && (this.queuedAttackAction != HeroEntity.BATTLE_ACTION_IDLE
                            || (this.comboGraceTicks > 0 && HeroCombatPlanner.canQueueComboFollowUp(this.hero, this.target, attackReachSqr, 2)))) {
                        startNextAttackAction(this.queuedAttackAction != HeroEntity.BATTLE_ACTION_IDLE
                                ? this.queuedAttackAction
                                : HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2);
                        return;
                    }
                    this.hero.resetBattleActionTimeline();
                    this.attackRecoveryTicks = 1;
                    this.comboGraceTicks = 0;
                    this.queuedAttackAction = HeroEntity.BATTLE_ACTION_IDLE;
                } else {
                    this.hero.setBattleActionState(HeroEntity.BATTLE_ACTION_APPROACH);
                    this.hero.setBattleActionTicks(0);
                    this.attackRecoveryTicks = 0;
                    this.queuedAttackAction = HeroEntity.BATTLE_ACTION_IDLE;
                }
            } else {
                this.hero.resetBattleActionTimeline();
                this.attackRecoveryTicks = 0;
                this.comboGraceTicks = 0;
                this.queuedAttackAction = HeroEntity.BATTLE_ACTION_IDLE;
            }
        }
    }

    private int resolveOpeningAttackAction() {
        if (this.hero.getBattleBufferedAction() == HeroEntity.BATTLE_BUFFER_LIGHT && this.comboGraceTicks > 0) {
            return HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2;
        }

        if (this.hero.getBattleComboStep() == 2 && this.comboGraceTicks > 0) {
            return HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2;
        }

        return HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1;
    }

    private int getChainStartTick(int action, int totalDuration, int hitFrame) {
        HeroCombatPlanner.ActionPhaseSpec phaseSpec = HeroCombatPlanner.getActionProfileForState(action).phaseSpec();
        return Math.min(totalDuration - 1, Math.max(hitFrame + 1, phaseSpec.activeEndTick() + 1));
    }

    private void moveTowardsTarget(double speed) {
        if (this.target == null) {
            return;
        }

        this.hero.setBattleActionState(HeroEntity.BATTLE_ACTION_APPROACH);
        this.hero.setBattleActionTicks(0);
        boolean pathStarted = this.hero.getNavigation().moveTo(this.target, speed);
        if (!pathStarted) {
            this.hero.getMoveControl().setWantedPosition(this.target.getX(), this.target.getY(), this.target.getZ(), speed);
        }
    }

    private LivingEntity findNearestHostile() {
        LivingEntity existingTarget = pickBattleTarget();
        if (isValidTarget(existingTarget) && this.hero.distanceToSqr(existingTarget) <= SEARCH_RANGE * SEARCH_RANGE) {
            return existingTarget;
        }

        List<LivingEntity> targets = this.hero.level().getEntitiesOfClass(
                LivingEntity.class,
                this.hero.getBoundingBox().inflate(getSearchRange()),
                candidate -> {
                    clearSubmission(candidate);
                    return this.isValidTarget(candidate);
                }
        );

        return targets.stream()
                .min(Comparator
                        .comparing((LivingEntity candidate) -> !isNonVanillaHostile(candidate))
                        .thenComparingDouble(this.hero::distanceToSqr))
                .orElse(null);
    }

    private boolean isValidTarget(LivingEntity candidate) {
        return canHeroAttackTarget(candidate);
    }

    public static boolean canHeroAttackTarget(LivingEntity candidate) {
        return candidate != null
                && candidate.isAlive()
                && !candidate.isRemoved()
                && !(candidate instanceof HeroEntity)
                && !(candidate instanceof Player)
                && (candidate instanceof Enemy || candidate instanceof Monster);
    }

    private static boolean isNonVanillaHostile(LivingEntity candidate) {
        ResourceLocation entityId = candidate == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(candidate.getType());
        return entityId != null && !"minecraft".equals(entityId.getNamespace());
    }

    private double getSearchRange() {
        return Math.max(SEARCH_RANGE, this.hero.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE));
    }

    private void clearSubmission(LivingEntity candidate) {
        if (candidate instanceof Mob mob) {
            mob.getPersistentData().putBoolean("HeroSubmission", false);
            mob.getPersistentData().remove("HeroSubmissionYaw");
            if (mob.isSilent()) {
                mob.setSilent(false);
            }
        }
    }

    private double getAttackReachSqr(LivingEntity target) {
        double reach = Math.max(2.6D, (this.hero.getBbWidth() * 2.0F) + target.getBbWidth());
        return reach * reach;
    }

    private HeroCombatPlanner.CombatTuning getFallbackCombatTuning(LivingEntity target) {
        double reach = target != null ? Math.sqrt(this.getAttackReachSqr(target)) : 2.8D;
        return HeroCombatPlanner.CombatTuning.comboOnly(reach + 0.45D);
    }

    private int getActionDuration(int action) {
        ItemStack stack = this.hero.getMainHandItem();
        if (stack.getItem() instanceof PoemOfTheEndItem poem) {
            return switch (poem.getMode(stack)) {
                case PoemOfTheEndItem.MODE_VOID_SHATTER -> action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1 ? 6 : 7;
                case PoemOfTheEndItem.MODE_REALM_BREAKER, PoemOfTheEndItem.MODE_THUNDER_CALL -> action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1 ? 10 : 12;
                default -> action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1 ? DEFAULT_ACTION_DURATION : 9;
            };
        }
        return action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1 ? 7 : 8;
    }

    private int getHitFrame(int action) {
        ItemStack stack = this.hero.getMainHandItem();
        if (stack.getItem() instanceof PoemOfTheEndItem poem) {
            return switch (poem.getMode(stack)) {
                case PoemOfTheEndItem.MODE_VOID_SHATTER -> 3;
                case PoemOfTheEndItem.MODE_REALM_BREAKER, PoemOfTheEndItem.MODE_THUNDER_CALL -> action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1 ? 4 : 5;
                default -> action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1 ? DEFAULT_HIT_FRAME : 5;
            };
        }
        return action == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1 ? 3 : 4;
    }
}

