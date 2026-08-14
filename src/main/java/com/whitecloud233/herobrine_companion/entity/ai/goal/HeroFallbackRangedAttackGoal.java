package com.whitecloud233.herobrine_companion.entity.ai.goal;

import com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.HeroCombatWeaponHelper;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.item.ItemStack;

public class HeroFallbackRangedAttackGoal extends Goal {
   private static final double SEARCH_RANGE = (double)24.0F;
   private static final double MOVE_SPEED = 1.1;
   private final HeroEntity hero;
   private LivingEntity target;
   private int retargetCooldown;
   private int attackCooldown;
   private int useTicks;

   public HeroFallbackRangedAttackGoal(HeroEntity hero) {
      this.hero = hero;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
   }

   public boolean canUse() {
      return this.isBattleModeActive();
   }

   public boolean canContinueToUse() {
      return this.isBattleModeActive() && (this.target != null && this.target.isAlive() || this.hero.isUsingItem());
   }

   public void start() {
      this.target = this.pickBattleTarget();
      this.retargetCooldown = 0;
      this.attackCooldown = 0;
      this.useTicks = 0;
      this.hero.setFloating(false);
      this.hero.setNoGravity(false);
      this.hero.setAggressive(true);
      this.hero.resetBattleCombatState();
   }

   public void stop() {
      this.target = null;
      this.hero.setTarget((LivingEntity)null);
      this.hero.stopUsingItem();
      this.hero.getNavigation().stop();
      this.hero.setAggressive(false);
      this.attackCooldown = 0;
      this.useTicks = 0;
      this.hero.resetBattleCombatState();
   }

   public void tick() {
      LivingEntity prioritizedTarget = this.pickBattleTarget();
      if (prioritizedTarget != null) {
         this.target = prioritizedTarget;
      }

      if (this.retargetCooldown > 0) {
         --this.retargetCooldown;
      }

      if (this.attackCooldown > 0) {
         --this.attackCooldown;
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
         this.hero.stopUsingItem();
         this.hero.getNavigation().stop();
      } else {
         ItemStack stack = this.hero.getMainHandItem();
         if (!HeroCombatWeaponHelper.isRangedLoadout(stack)) {
            this.hero.stopUsingItem();
            this.hero.getNavigation().stop();
         } else {
            boolean canSee = this.hero.hasLineOfSight(this.target);
            float attackRadius = HeroCombatWeaponHelper.getPreferredAttackRadius(stack);
            double attackRadiusSqr = (double)(attackRadius * attackRadius);
            double distanceSqr = this.hero.distanceToSqr(this.target);
            boolean inRange = distanceSqr <= attackRadiusSqr;
            this.hero.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            if (inRange && canSee) {
               this.hero.getNavigation().stop();
            } else {
               boolean pathStarted = this.hero.getNavigation().moveTo(this.target, 1.1);
               if (!pathStarted) {
                  this.hero.getMoveControl().setWantedPosition(this.target.getX(), this.target.getY(), this.target.getZ(), 1.1);
               }
            }

            if (HeroCombatWeaponHelper.isCrossbowWeapon(stack)) {
               this.tickCrossbow(stack, canSee, inRange);
            } else {
               this.tickChargingWeapon(stack, canSee, inRange);
            }
         }
      }
   }

   private void tickCrossbow(ItemStack stack, boolean canSee, boolean inRange) {
      if (!this.hero.isUsingItem()) {
         if (this.attackCooldown <= 0 && canSee && inRange) {
            this.hero.startUsingItem(InteractionHand.MAIN_HAND);
            this.useTicks = 0;
         }

      } else {
         ++this.useTicks;
         if (this.useTicks >= HeroCombatWeaponHelper.getChargeTicks(stack, this.hero)) {
            if (canSee && inRange && this.attackCooldown <= 0 && HeroCombatWeaponHelper.fireCrossbow(this.hero, this.target, stack)) {
               this.attackCooldown = HeroCombatWeaponHelper.getPostShotCooldown(stack);
            } else {
               this.attackCooldown = 10;
            }

            this.hero.stopUsingItem();
            this.useTicks = 0;
         }

      }
   }

   private void tickChargingWeapon(ItemStack stack, boolean canSee, boolean inRange) {
      if (!this.hero.isUsingItem()) {
         if (this.attackCooldown <= 0 && canSee && inRange) {
            this.hero.startUsingItem(InteractionHand.MAIN_HAND);
            this.useTicks = 0;
         }

      } else {
         ++this.useTicks;
         if (this.useTicks >= HeroCombatWeaponHelper.getChargeTicks(stack, this.hero) && canSee) {
            boolean fired = HeroCombatWeaponHelper.fireRangedWeaponAtTarget(this.hero, this.target, stack, this.useTicks);
            if (!fired) {
               this.hero.releaseUsingItem();
               this.hero.swing(InteractionHand.MAIN_HAND);
            }

            this.hero.stopUsingItem();
            this.useTicks = 0;
            this.attackCooldown = HeroCombatWeaponHelper.getPostShotCooldown(stack);
         }

      }
   }

   private boolean isBattleModeActive() {
      return this.hero.isBattleModeActive() && !(Boolean)this.hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE) && !HeroEpicFightCompat.shouldUseEpicFightCombatAI(this.hero) && HeroCombatWeaponHelper.isRangedLoadout(this.hero);
   }

   private LivingEntity pickBattleTarget() {
      LivingEntity currentTarget = this.hero.getTarget();
      return this.isValidTarget(currentTarget) ? currentTarget : this.target;
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
                 .min(Comparator.comparingDouble(this.hero::distanceToSqr))
                 .orElse(null);
      }
   }

   private boolean isValidTarget(LivingEntity candidate) {
      return HeroBattleStanceGoal.canHeroAttackTarget(this.hero, candidate);
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
}
