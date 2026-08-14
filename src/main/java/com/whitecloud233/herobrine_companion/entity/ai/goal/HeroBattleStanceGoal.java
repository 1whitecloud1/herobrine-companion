package com.whitecloud233.herobrine_companion.entity.ai.goal;

import com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.HeroCombatWeaponHelper;
import com.whitecloud233.herobrine_companion.entity.ai.combat.HeroCombatPlanner;
import com.whitecloud233.herobrine_companion.entity.ai.combat.HeroCombatPursuit;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class HeroBattleStanceGoal extends Goal {
   private static final double SEARCH_RANGE = (double)24.0F;
   private static final double MOVE_SPEED = 1.35;
   private static final int DEFAULT_ACTION_DURATION = 8;
   private static final int DEFAULT_HIT_FRAME = 4;
   private final HeroEntity hero;
   private LivingEntity target;
   private int retargetCooldown;
   private int attackRecoveryTicks;
   private int comboGraceTicks;
   private int queuedAttackAction;
   private boolean hitApplied;
   private int groundPathFailures;
   private boolean flyingPursuit;
   private int landCooldown;

   public HeroBattleStanceGoal(HeroEntity hero) {
      this.hero = hero;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
   }

   public boolean canUse() {
      return this.isBattleModeActive();
   }

   public boolean canContinueToUse() {
      return this.isBattleModeActive();
   }

   public void start() {
      this.retargetCooldown = 0;
      this.attackRecoveryTicks = 0;
      this.comboGraceTicks = 0;
      this.queuedAttackAction = 0;
      this.hitApplied = false;
      this.groundPathFailures = 0;
      this.flyingPursuit = false;
      this.landCooldown = 0;
      this.hero.setFloating(false);
      this.hero.setNoGravity(false);
      this.hero.setAggressive(true);
      this.target = this.pickBattleTarget();
      this.hero.resetBattleCombatState();
   }

   public void stop() {
      this.target = null;
      this.hero.setTarget((LivingEntity)null);
      this.hero.getNavigation().stop();
      this.hitApplied = false;
      this.attackRecoveryTicks = 0;
      this.comboGraceTicks = 0;
      this.queuedAttackAction = 0;
      if (this.flyingPursuit || this.hero.isFloating()) {
         HeroCombatPursuit.land(this.hero);
      }

      this.hero.setSprinting(false);
      this.flyingPursuit = false;
      this.groundPathFailures = 0;
      this.landCooldown = 0;
      this.hero.setAggressive(false);
      this.hero.resetBattleCombatState();
   }

   public void tick() {
      this.hero.tickBattleBufferedAction();
      LivingEntity prioritizedTarget = this.pickBattleTarget();
      if (prioritizedTarget != null) {
         this.target = prioritizedTarget;
      }

      if (this.retargetCooldown > 0) {
         --this.retargetCooldown;
      }

      if (this.attackRecoveryTicks > 0) {
         --this.attackRecoveryTicks;
      }

      if (this.comboGraceTicks > 0) {
         --this.comboGraceTicks;
      }

      if (this.isValidTarget(this.target) && !(this.hero.distanceToSqr(this.target) > (double)576.0F)) {
         if (this.retargetCooldown <= 0) {
            LivingEntity nearbyBetterTarget = this.findNearestHostile();
            if (nearbyBetterTarget != null && nearbyBetterTarget != this.target && this.hero.distanceToSqr(nearbyBetterTarget) + (double)4.0F < this.hero.distanceToSqr(this.target)) {
               this.target = nearbyBetterTarget;
            }

            this.retargetCooldown = 10;
         }
      } else {
         this.target = this.findNearestHostile();
         this.retargetCooldown = 10;
      }

      this.hero.setTarget(this.target);
      this.clearSubmission(this.target);
      if (this.target == null) {
         this.hero.getNavigation().stop();
         this.hitApplied = false;
         this.attackRecoveryTicks = 0;
         this.comboGraceTicks = 0;
         this.queuedAttackAction = 0;
         this.hero.resetBattleActionTimeline();
      } else {
         this.hero.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
         if (this.isPerformingAttack()) {
            this.tickAttackTimeline();
         } else {
            HeroCombatPlanner.CombatTuning tuning = this.getFallbackCombatTuning(this.target);
            HeroCombatPlanner.queuePreferredFollowUp(this.hero, this.target, tuning);
            if (HeroCombatPlanner.canStartMeleeAttack(this.hero, this.target, this.getAttackReachSqr(this.target), 3) && HeroCombatPlanner.prefersAction(this.hero, this.target, tuning, HeroCombatPlanner.PlannedAction.LIGHT_COMBO)) {
               this.hero.getNavigation().stop();
               this.hero.setBattleActionState(0);
               this.hero.setBattleActionTicks(0);
               if (this.attackRecoveryTicks <= 0) {
                  this.startNextAttackAction(this.resolveOpeningAttackAction());
               }
            } else if (this.flyingPursuit) {
               this.tickBattleFlight(this.target, Math.sqrt(this.getAttackReachSqr(this.target)));
            } else if (this.shouldEnterBattleFlight()) {
               this.enterBattleFlight();
            } else {
               this.moveTowardsTarget(HeroCombatPursuit.chaseSpeed(this.hero, this.target));
            }

         }
      }
   }

   private boolean isBattleModeActive() {
      return this.hero.isBattleModeActive() && !(Boolean)this.hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE) && !HeroEpicFightCompat.shouldUseEpicFightCombatAI(this.hero) && !HeroCombatWeaponHelper.isRangedLoadout(this.hero);
   }

   private boolean isPerformingAttack() {
      int action = this.hero.getBattleActionState();
      return action == 2 || action == 3;
   }

   private LivingEntity pickBattleTarget() {
      LivingEntity currentTarget = this.hero.getTarget();
      return this.isValidTarget(currentTarget) ? currentTarget : this.target;
   }

   private void startNextAttackAction(int action) {
      int normalizedAction = action == 3 ? 3 : 2;
      this.hero.setBattleComboStep(normalizedAction == 3 ? 2 : 1);
      this.hero.beginBattleAction(normalizedAction);
      this.hero.swing(InteractionHand.MAIN_HAND);
      this.hero.setSprinting(false);
      this.queuedAttackAction = 0;
      this.comboGraceTicks = 0;
      this.hitApplied = false;
   }

   private void tickAttackTimeline() {
      int action = this.hero.getBattleActionState();
      int ticks = this.hero.getBattleActionTicks() + 1;
      int totalDuration = this.getActionDuration(action);
      int hitFrame = this.getHitFrame(action);
      int chainStartTick = this.getChainStartTick(action, totalDuration, hitFrame);
      double attackReachSqr = this.target != null ? this.getAttackReachSqr(this.target) : (double)0.0F;
      this.hero.setBattleActionTicks(ticks);
      if (this.target != null && this.isValidTarget(this.target) && ticks < hitFrame && !this.hero.hasLineOfSight(this.target)) {
         this.moveTowardsTarget(1.4175000000000002);
      } else if (this.target != null && this.isValidTarget(this.target) && ticks <= hitFrame + 1 && HeroCombatPlanner.predictedDistanceSqr(this.hero, this.target, Math.max(1, hitFrame - ticks + 1), 0.35) > attackReachSqr * 0.82) {
         this.moveTowardsTarget(1.2825);
      } else {
         this.hero.getNavigation().stop();
      }

      if (action == 2 && ticks >= chainStartTick && this.target != null && this.isValidTarget(this.target)) {
         HeroCombatPlanner.CombatTuning tuning = this.getFallbackCombatTuning(this.target);
         HeroCombatPlanner.queuePreferredFollowUp(this.hero, this.target, tuning);
         if (HeroCombatPlanner.canQueueComboFollowUp(this.hero, this.target, attackReachSqr, 3) && this.hero.getBattleBufferedAction() == 2) {
            this.queuedAttackAction = 3;
            this.comboGraceTicks = 4;
         }
      }

      if (!this.hitApplied && ticks >= hitFrame) {
         if (this.target != null && this.isValidTarget(this.target) && HeroCombatPlanner.canStartMeleeAttack(this.hero, this.target, attackReachSqr, 0)) {
            this.hero.doHurtTarget(this.target);
         }

         this.hitApplied = true;
      }

      if (action == 2 && this.queuedAttackAction != 0 && ticks >= Math.max(chainStartTick, totalDuration - 1)) {
         this.startNextAttackAction(this.queuedAttackAction);
      } else {
         if (ticks >= totalDuration) {
            this.hitApplied = false;
            if (this.target != null && this.isValidTarget(this.target)) {
               if (!HeroCombatPlanner.canStartMeleeAttack(this.hero, this.target, attackReachSqr, 1)) {
                  this.hero.setBattleActionState(1);
                  this.hero.setBattleActionTicks(0);
                  this.attackRecoveryTicks = 0;
                  this.queuedAttackAction = 0;
               } else {
                  if (action == 2 && (this.queuedAttackAction != 0 || this.comboGraceTicks > 0 && HeroCombatPlanner.canQueueComboFollowUp(this.hero, this.target, attackReachSqr, 2))) {
                     this.startNextAttackAction(this.queuedAttackAction != 0 ? this.queuedAttackAction : 3);
                     return;
                  }

                  this.hero.resetBattleActionTimeline();
                  this.attackRecoveryTicks = 1;
                  this.comboGraceTicks = 0;
                  this.queuedAttackAction = 0;
               }
            } else {
               this.hero.resetBattleActionTimeline();
               this.attackRecoveryTicks = 0;
               this.comboGraceTicks = 0;
               this.queuedAttackAction = 0;
            }
         }

      }
   }

   private int resolveOpeningAttackAction() {
      if (this.hero.getBattleBufferedAction() == 2 && this.comboGraceTicks > 0) {
         return 3;
      } else {
         return this.hero.getBattleComboStep() == 2 && this.comboGraceTicks > 0 ? 3 : 2;
      }
   }

   private int getChainStartTick(int action, int totalDuration, int hitFrame) {
      HeroCombatPlanner.ActionPhaseSpec phaseSpec = HeroCombatPlanner.getActionProfileForState(action).phaseSpec();
      return Math.min(totalDuration - 1, Math.max(hitFrame + 1, phaseSpec.activeEndTick() + 1));
   }

   private void moveTowardsTarget(double speed) {
      if (this.target != null) {
         this.hero.setBattleActionState(1);
         this.hero.setBattleActionTicks(0);
         boolean pathStarted = this.hero.getNavigation().moveTo(this.target, speed);
         if (pathStarted) {
            this.groundPathFailures = 0;
         } else {
            ++this.groundPathFailures;
            this.hero.getMoveControl().setWantedPosition(this.target.getX(), this.target.getY(), this.target.getZ(), speed);
         }

      }
   }

   private boolean shouldEnterBattleFlight() {
      boolean targetHigh = HeroCombatPursuit.isTargetHighAbove(this.hero, this.target);
      boolean inFluid = HeroCombatPursuit.isHeroInFluid(this.hero);
      return HeroCombatPursuit.shouldTakeFlight(this.hero, this.target, this.groundPathFailures) && (targetHigh || inFluid || this.landCooldown <= 0);
   }

   private void enterBattleFlight() {
      this.flyingPursuit = true;
      this.groundPathFailures = 0;
      HeroCombatPursuit.startFlight(this.hero);
   }

   private void tickBattleFlight(LivingEntity target, double attackRadius) {
      if (HeroCombatPursuit.shouldLand(this.hero, target, attackRadius)) {
         HeroCombatPursuit.land(this.hero);
         this.flyingPursuit = false;
         this.landCooldown = 60;
         this.groundPathFailures = 0;
      } else if (!this.hero.isFloating()) {
         this.flyingPursuit = false;
      } else {
         Vec3 flyTarget = HeroCombatPursuit.flightTarget(this.hero, target, attackRadius);
         HeroCombatPursuit.flyTo(this.hero, flyTarget, (double)1.0F);
      }
   }

   private LivingEntity findNearestHostile() {
      LivingEntity existingTarget = this.pickBattleTarget();
      if (this.isValidTarget(existingTarget) && this.hero.distanceToSqr(existingTarget) <= (double)576.0F) {
         return existingTarget;
      } else {
         List<LivingEntity> targets = this.hero.level().getEntitiesOfClass(LivingEntity.class, this.hero.getBoundingBox().inflate(this.getSearchRange()), (candidate) -> {
            this.clearSubmission(candidate);
            return this.isValidTarget(candidate);
         });
         return targets.stream()
                 .min(Comparator.comparing((LivingEntity candidate) -> !isNonVanillaHostile(candidate)).thenComparingDouble(this.hero::distanceToSqr))
                 .orElse(null);
      }
   }

   private boolean isValidTarget(LivingEntity candidate) {
      return canHeroAttackTarget(this.hero, candidate);
   }

   public static boolean canHeroAttackTarget(HeroEntity hero, LivingEntity candidate) {
      return candidate != null && candidate.isAlive() && !candidate.isRemoved() && !(candidate instanceof HeroEntity) && !(candidate instanceof Player) && (candidate instanceof Enemy || candidate instanceof Monster) && (hero == null || hero.canAttack(candidate));
   }

   private static boolean isNonVanillaHostile(LivingEntity candidate) {
      ResourceLocation entityId = candidate == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(candidate.getType());
      return entityId != null && !"minecraft".equals(entityId.getNamespace());
   }

   private double getSearchRange() {
      return Math.max((double)24.0F, this.hero.getAttributeValue(Attributes.FOLLOW_RANGE));
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
      double reach = Math.max(2.6, (double)(this.hero.getBbWidth() * 2.0F + target.getBbWidth()));
      return reach * reach;
   }

   private HeroCombatPlanner.CombatTuning getFallbackCombatTuning(LivingEntity target) {
      double reach = target != null ? Math.sqrt(this.getAttackReachSqr(target)) : 2.8;
      return HeroCombatPlanner.CombatTuning.comboOnly(reach + 0.45);
   }

   private int getActionDuration(int action) {
      ItemStack stack = this.hero.getMainHandItem();
      Item var4 = stack.getItem();
      if (var4 instanceof PoemOfTheEndItem) {
         PoemOfTheEndItem poem = (PoemOfTheEndItem)var4;
         int var10000;
         switch (poem.getMode(stack)) {
            case 1:
            case 2:
               var10000 = action == 2 ? 10 : 12;
               break;
            case 3:
               var10000 = action == 2 ? 6 : 7;
               break;
            default:
               var10000 = action == 2 ? 8 : 9;
         }

         return var10000;
      } else {
         return action == 2 ? 7 : 8;
      }
   }

   private int getHitFrame(int action) {
      ItemStack stack = this.hero.getMainHandItem();
      Item var4 = stack.getItem();
      if (var4 instanceof PoemOfTheEndItem) {
         PoemOfTheEndItem poem = (PoemOfTheEndItem)var4;
         int var10000;
         switch (poem.getMode(stack)) {
            case 1:
            case 2:
               var10000 = action == 2 ? 4 : 5;
               break;
            case 3:
               var10000 = 3;
               break;
            default:
               var10000 = action == 2 ? 4 : 5;
         }

         return var10000;
      } else {
         return action == 2 ? 3 : 4;
      }
   }
}
