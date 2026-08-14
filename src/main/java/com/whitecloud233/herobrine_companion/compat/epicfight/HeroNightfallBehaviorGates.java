package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.whitecloud233.herobrine_companion.compat.epicfight.effect.HeroBloodCurseStore;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.combat.HeroCombatPlanner;
import java.util.Locale;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

final class HeroNightfallBehaviorGates {
   static final int MIN_BLOOD_CURSE_FOR_HARVEST = 3;
   private static final int HOVER_FALLBACK_COOLDOWN_TICKS = 40;
   private static final String HOVER_FALLBACK_KEY = "__hover_fallback_melee__";

   private HeroNightfallBehaviorGates() {
   }

   static boolean canStartDefaultCombo(HumanoidMobPatch<?> mobPatch, HeroNightfallProfile profile, int comboIndex, double comboRange) {
      HeroEntity hero = getHero(mobPatch);
      LivingEntity target = getTrackedTarget(hero);
      if (hero != null && target != null) {
         if (Math.abs(target.getY() - hero.getY()) > (double)2.5F) {
            maybeFallbackHoverMelee(hero, target, comboRange);
            HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "ground", "comboBlocked=height");
            return false;
         } else if (hero.isBattleModeActive() && !hero.isBattleHoldAction() && !hero.isBattleReleaseAction()) {
            if (!hero.hasLineOfSight(target)) {
               HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "los", "comboBlocked=los");
               return false;
            } else {
               double reach = Math.max(comboRange, profile.attackRadius() + 0.45) + (double)2.5F;
               int windupTicks = 4 + Math.min(comboIndex, 3);
               double predictedDistanceSqr = HeroCombatPlanner.predictedDistanceSqr(hero, target, windupTicks, (double)0.5F);
               if (predictedDistanceSqr > reach * reach) {
                  String var12 = String.format(Locale.ROOT, "%.2f", Math.sqrt(predictedDistanceSqr));
                  HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "range", "comboBlocked=range dist=" + var12 + ",reach=" + String.format(Locale.ROOT, "%.2f", reach));
                  return false;
               } else if (!isSoftlyFacingTarget(hero, target, windupTicks)) {
                  HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "facing", "comboBlocked=facing");
                  return false;
               } else {
                  return true;
               }
            }
         } else {
            boolean var10003 = hero.isBattleModeActive();
            HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "state", "comboBlocked=state battle=" + var10003 + ",hold=" + hero.isBattleHoldAction() + ",release=" + hero.isBattleReleaseAction() + ",action=" + hero.getBattleActionState() + ",shock=" + hero.shockTicks);
            return false;
         }
      } else {
         return hero == null;
      }
   }

   private static boolean isSoftlyFacingTarget(HeroEntity hero, LivingEntity target, int ticksAhead) {
      if (hero != null && target != null) {
         Vec3 predictedTarget = target.position().add(target.getDeltaMovement().scale((double)Math.max(0, ticksAhead)));
         Vec3 toTarget = predictedTarget.subtract(hero.position());
         Vec3 horizontalToTarget = new Vec3(toTarget.x, (double)0.0F, toTarget.z);
         if (horizontalToTarget.lengthSqr() < 1.0E-4) {
            return true;
         } else {
            Vec3 look = hero.getLookAngle();
            Vec3 horizontalLook = new Vec3(look.x, (double)0.0F, look.z);
            if (horizontalLook.lengthSqr() < 1.0E-4) {
               return true;
            } else {
               return horizontalLook.normalize().dot(horizontalToTarget.normalize()) >= -0.1;
            }
         }
      } else {
         return true;
      }
   }

   static boolean canContinueDefaultCombo(HumanoidMobPatch<?> mobPatch, HeroNightfallProfile profile, int comboIndex, double comboRange) {
      HeroEntity hero = getHero(mobPatch);
      LivingEntity target = getTrackedTarget(hero);
      if (hero != null && target != null) {
         if (Math.abs(target.getY() - hero.getY()) > (double)2.5F) {
            HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "ground-continue", "comboBlocked=height-continue");
            return false;
         } else if (hero.isBattleModeActive() && !hero.isBattleHoldAction() && !hero.isBattleReleaseAction()) {
            if (!hero.hasLineOfSight(target)) {
               HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "los-continue", "comboBlocked=los-continue");
               return false;
            } else {
               double followUpReach = Math.max(comboRange, profile.attackRadius() + (double)0.75F) + (double)2.5F;
               int windupTicks = 3 + Math.min(comboIndex, 2);
               if (HeroCombatPlanner.predictedDistanceSqr(hero, target, windupTicks, (double)0.5F) > followUpReach * followUpReach) {
                  String var10 = String.format(Locale.ROOT, "%.2f", Math.sqrt(HeroCombatPlanner.predictedDistanceSqr(hero, target, windupTicks, (double)0.5F)));
                  HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "range-continue", "comboBlocked=range-continue dist=" + var10 + ",reach=" + String.format(Locale.ROOT, "%.2f", followUpReach));
                  return false;
               } else if (!isSoftlyFacingTarget(hero, target, windupTicks)) {
                  HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "facing-continue", "comboBlocked=facing-continue");
                  return false;
               } else {
                  return true;
               }
            }
         } else {
            boolean var10003 = hero.isBattleModeActive();
            HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "state-continue", "comboBlocked=state-continue battle=" + var10003 + ",hold=" + hero.isBattleHoldAction() + ",release=" + hero.isBattleReleaseAction() + ",action=" + hero.getBattleActionState());
            return false;
         }
      } else {
         return hero == null;
      }
   }

   static boolean canStartSkillSeries(HumanoidMobPatch<?> mobPatch, HeroNightfallSkillSeries skillSeries) {
      if (skillSeries.airAttack()) {
         return canStartAirAttack(mobPatch, skillSeries.maxDistance());
      } else {
         HeroEntity hero = getHero(mobPatch);
         if (hero == null) {
            return true;
         } else if (!hero.onGround()) {
            return false;
         } else {
            LivingEntity target = getTrackedTarget(hero);
            if (target != null && hero.isBattleModeActive() && !hero.isBattleHoldAction() && !hero.isBattleReleaseAction()) {
               HeroNightfallProfile profile = HeroNightfallProfiles.resolve(hero.getMainHandItem());
               HeroCombatPlanner.CombatTuning tuning = profile != null ? getNightfallCombatTuning(profile) : HeroCombatPlanner.CombatTuning.comboOnly(skillSeries.maxDistance());
               if (!isProtectedComboStartup(hero) && !HeroCombatPlanner.isAttackReplayLocked(hero, 7)) {
                  int windupTicks = estimateSkillWindupTicks(skillSeries);
                  double predictedDistanceSqr = HeroCombatPlanner.predictedDistanceSqr(hero, target, windupTicks, 0.35);
                  double relaxedMinDistance = Math.max((double)0.0F, skillSeries.minDistance() - 0.35);
                  if (predictedDistanceSqr < relaxedMinDistance * relaxedMinDistance) {
                     return false;
                  } else {
                     return HeroCombatPlanner.canStartPredictedMeleeCombo(hero, target, skillSeries.maxDistance(), windupTicks) && HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.SKILL);
                  }
               } else {
                  return false;
               }
            } else {
               return false;
            }
         }
      }
   }

   static boolean canContinueSkillSeries(HumanoidMobPatch<?> mobPatch) {
      HeroEntity hero = getHero(mobPatch);
      if (hero == null) {
         return true;
      } else {
         LivingEntity target = getTrackedTarget(hero);
         return target != null && hero.isBattleModeActive();
      }
   }

   static boolean canStartAirAttack(HumanoidMobPatch<?> mobPatch, double maxDistance) {
      HeroEntity hero = getHero(mobPatch);
      if (hero != null && hero.isAlive() && !hero.isPassenger() && !hero.isInWater() && !hero.isInLava()) {
         LivingEntity target = getTrackedTarget(hero);
         HeroNightfallProfile profile = HeroNightfallProfiles.resolve(hero.getMainHandItem());
         HeroCombatPlanner.CombatTuning tuning = profile != null ? getNightfallCombatTuning(profile) : HeroCombatPlanner.CombatTuning.comboOnly(maxDistance);
         boolean floating = hero.isFloating();
         return target != null && (floating || hero.onGround() || hero.getDeltaMovement().y > -0.7) && !HeroCombatPlanner.isAttackReplayLocked(hero, 8) && HeroCombatPlanner.canStartAirAttack(hero, target, maxDistance, 5) && (floating || HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.AIR));
      } else {
         return false;
      }
   }

   private static void maybeFallbackHoverMelee(HeroEntity hero, LivingEntity target, double comboRange) {
      if (hero != null && target != null && hero.isFloating() && !hero.isBattleHoldAction() && !hero.isBattleReleaseAction() && !HeroCombatPlanner.isAttackReplayLocked(hero, 8)) {
         if (hero.hasLineOfSight(target)) {
            double reach = Math.max((double)2.5F, comboRange);
            if (!(hero.distanceToSqr(target) > reach * reach)) {
               long gameTime = hero.level().getGameTime();
               if (!HeroSkillCooldownStore.isOnCooldown(hero, "__hover_fallback_melee__", 40, gameTime)) {
                  HeroSkillCooldownStore.markUsed(hero, "__hover_fallback_melee__", gameTime);
                  hero.doHurtTarget(target);
               }
            }
         }
      }
   }

   static boolean canStartCounter(HumanoidMobPatch<?> mobPatch, HeroNightfallSkillSeries series) {
      HeroEntity hero = getHero(mobPatch);
      LivingEntity target = getTrackedTarget(hero);
      if (hero != null && target != null && hero.isBattleModeActive() && !hero.isBattleHoldAction() && !hero.isBattleReleaseAction()) {
         return !isTargetAttacking(hero, target) ? false : canStartSkillSeries(mobPatch, series);
      } else {
         return false;
      }
   }

   static boolean canStartBloodHarvest(HumanoidMobPatch<?> mobPatch, HeroNightfallSkillSeries series) {
      HeroEntity hero = getHero(mobPatch);
      LivingEntity target = getTrackedTarget(hero);
      if (hero != null && target != null && hero.isBattleModeActive() && !hero.isBattleHoldAction() && !hero.isBattleReleaseAction()) {
         return !HeroBloodCurseStore.hasAtLeast(target, 3) ? false : canStartSkillSeries(mobPatch, series);
      } else {
         return false;
      }
   }

   static boolean isTargetAttacking(HeroEntity hero, LivingEntity target) {
      if (target != null && target.isAlive() && hero != null) {
         if (target.getAttackAnim(0.0F) > 0.0F) {
            return true;
         } else {
            LivingEntityPatch<?> patch = (LivingEntityPatch)EpicFightCapabilities.getEntityPatch(target, LivingEntityPatch.class);
            return patch != null && patch.getEntityState().attacking();
         }
      } else {
         return false;
      }
   }

   static boolean canStartCrimsonMoonCombo(HumanoidMobPatch<?> mobPatch, HeroNightfallProfile profile, int comboIndex, double comboRange) {
      HeroEntity hero = getHero(mobPatch);
      return (hero == null || hero.getBattleActionState() != 4) && canStartDefaultCombo(mobPatch, profile, comboIndex, comboRange);
   }

   static boolean canContinueCrimsonMoonCombo(HumanoidMobPatch<?> mobPatch, HeroNightfallProfile profile, int comboIndex, double comboRange) {
      HeroEntity hero = getHero(mobPatch);
      return (hero == null || hero.getBattleActionState() != 4) && canContinueDefaultCombo(mobPatch, profile, comboIndex, comboRange);
   }

   static boolean canStartCrimsonMoonHarvest(HumanoidMobPatch<?> mobPatch, HeroNightfallSkillSeries harvest) {
      HeroEntity hero = getHero(mobPatch);
      return (hero == null || hero.getBattleActionState() != 4) && canStartBloodHarvest(mobPatch, harvest);
   }

   static boolean canStartCrimsonMoonRelease(HumanoidMobPatch<?> mobPatch, HeroNightfallSkillSeries scarletEnd) {
      HeroEntity hero = getHero(mobPatch);
      if (hero != null && hero.getBattleActionState() == 4 && hero.getBattleActionTicks() >= 6) {
         LivingEntity target = getTrackedTarget(hero);
         return target != null && HeroCombatPlanner.canStartPredictedMeleeCombo(hero, target, scarletEnd.maxDistance(), estimateSkillWindupTicks(scarletEnd));
      } else {
         return false;
      }
   }

   static boolean isProtectedComboStartup(HeroEntity hero) {
      return hero.isBattleTapAction() && hero.getBattleActionTicks() > 0 && hero.getBattleActionTicks() <= 5;
   }

   static int estimateSkillWindupTicks(HeroNightfallSkillSeries skillSeries) {
      double range = skillSeries.maxDistance() - skillSeries.minDistance();
      if (range >= (double)5.0F) {
         return 8;
      } else {
         return range >= (double)3.0F ? 6 : 5;
      }
   }

   static HeroCombatPlanner.CombatTuning getNightfallCombatTuning(HeroNightfallProfile profile) {
      double comboMaxDistance = Math.max(3.6, profile.attackRadius() + 0.6);
      double airMaxDistance = (double)0.0F;
      double skillMinDistance = Double.MAX_VALUE;
      double skillMaxDistance = (double)0.0F;
      boolean airAvailable = false;
      boolean skillAvailable = false;

      for(HeroNightfallSkillSeries skillSeries : profile.skillSeries()) {
         if (skillSeries.airAttack()) {
            airAvailable = true;
            airMaxDistance = Math.max(airMaxDistance, skillSeries.maxDistance());
         } else {
            skillAvailable = true;
            skillMinDistance = Math.min(skillMinDistance, skillSeries.minDistance());
            skillMaxDistance = Math.max(skillMaxDistance, skillSeries.maxDistance());
         }
      }

      if (!skillAvailable) {
         skillMinDistance = (double)0.0F;
      }

      return new HeroCombatPlanner.CombatTuning(comboMaxDistance, (double)0.0F, (double)0.0F, airMaxDistance, skillMinDistance, skillMaxDistance, false, airAvailable, skillAvailable);
   }

   static @Nullable HeroEntity getHero(HumanoidMobPatch<?> mobPatch) {
      HeroEntity var10000;
      if (mobPatch instanceof HeroEpicFightPatch heroPatch) {
         var10000 = (HeroEntity)heroPatch.getOriginal();
      } else {
         var10000 = null;
      }

      return var10000;
   }

   static @Nullable LivingEntity getTrackedTarget(HeroEntity hero) {
      if (hero == null) {
         return null;
      } else {
         LivingEntity target = hero.getTarget();
         return target != null && target.isAlive() && !target.isRemoved() ? target : null;
      }
   }
}
