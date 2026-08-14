package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.HeroCombatWeaponHelper;
import java.util.EnumSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

final class HeroEpicFightRangedAttackGoal extends Goal {
   private static final float DEFAULT_ATTACK_RADIUS = 14.0F;
   private static final int DEFAULT_BOW_DRAW_TICKS = 20;
   private static final int DEFAULT_GENERIC_DRAW_TICKS = 12;
   private static final int DEFAULT_BOW_COOLDOWN = 20;
   private static final int DEFAULT_CROSSBOW_COOLDOWN = 30;
   private final HeroEntity hero;
   private final HeroEpicFightPatch patch;
   private final double moveSpeed;
   private final float attackRadius;
   private int attackCooldown;
   private int useTicks;

   HeroEpicFightRangedAttackGoal(HeroEpicFightPatch patch, HeroEntity hero, double moveSpeed, float attackRadius) {
      this.patch = patch;
      this.hero = hero;
      this.moveSpeed = moveSpeed;
      this.attackRadius = attackRadius <= 0.0F ? 14.0F : attackRadius;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
   }

   public boolean canUse() {
      LivingEntity target = this.hero.getTarget();
      return this.patch.isRangedWeaponEquipped() && target != null && target.isAlive();
   }

   public boolean canContinueToUse() {
      LivingEntity target = this.hero.getTarget();
      return this.patch.isRangedWeaponEquipped() && (target != null && target.isAlive() || this.hero.isUsingItem());
   }

   public void start() {
      this.attackCooldown = 0;
      this.useTicks = 0;
      this.hero.setAggressive(true);
   }

   public void stop() {
      this.hero.stopUsingItem();
      this.hero.getNavigation().stop();
      this.useTicks = 0;
      this.attackCooldown = 0;
   }

   public void tick() {
      LivingEntity target = this.hero.getTarget();
      if (target != null) {
         ItemStack stack = this.hero.getMainHandItem();
         if (!stack.isEmpty()) {
            boolean canSee = this.hero.hasLineOfSight(target);
            double distanceSqr = this.hero.distanceToSqr(target);
            double attackRadiusSqr = (double)(this.attackRadius * this.attackRadius);
            this.hero.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (distanceSqr <= attackRadiusSqr && canSee) {
               this.hero.getNavigation().stop();
            } else {
               this.hero.getNavigation().moveTo(target, this.moveSpeed);
            }

            if (this.attackCooldown > 0) {
               --this.attackCooldown;
            }

            if (stack.getItem() instanceof CrossbowItem) {
               this.tickCrossbow(target, stack, canSee, distanceSqr <= attackRadiusSqr);
            } else {
               UseAnim useAnim = stack.getUseAnimation();
               if (useAnim == UseAnim.BOW || useAnim == UseAnim.CROSSBOW || HeroEpicFightWeaponProfiles.isRangedLoadout(stack)) {
                  this.tickChargingWeapon(target, stack, canSee, distanceSqr <= attackRadiusSqr, useAnim);
               }

            }
         }
      }
   }

   private void tickCrossbow(LivingEntity target, ItemStack stack, boolean canSee, boolean inRange) {
      if (!this.hero.isUsingItem()) {
         if (this.attackCooldown <= 0 && canSee && inRange) {
            this.hero.startUsingItem(InteractionHand.MAIN_HAND);
            this.useTicks = 0;
         }

      } else {
         ++this.useTicks;
         if (this.useTicks >= CrossbowItem.getChargeDuration(stack, this.hero)) {
            if (canSee && inRange && this.attackCooldown <= 0 && HeroCombatWeaponHelper.fireCrossbow(this.hero, target, stack)) {
               this.attackCooldown = 30;
            } else {
               this.attackCooldown = 10;
            }

            this.hero.stopUsingItem();
            this.useTicks = 0;
         }

      }
   }

   private void tickChargingWeapon(LivingEntity target, ItemStack stack, boolean canSee, boolean inRange, UseAnim useAnim) {
      if (!this.hero.isUsingItem()) {
         if (this.attackCooldown <= 0 && canSee && inRange) {
            this.hero.startUsingItem(InteractionHand.MAIN_HAND);
            this.useTicks = 0;
         }

      } else {
         ++this.useTicks;
         int chargeTicks = useAnim == UseAnim.BOW ? 20 : 12;
         if (this.useTicks >= chargeTicks && canSee) {
            boolean fired = HeroCombatWeaponHelper.fireRangedWeaponAtTarget(this.hero, target, stack, this.useTicks);
            if (!fired) {
               this.hero.releaseUsingItem();
            }

            this.hero.stopUsingItem();
            this.useTicks = 0;
            this.attackCooldown = useAnim == UseAnim.BOW ? 20 : 30;
         }

      }
   }
}
