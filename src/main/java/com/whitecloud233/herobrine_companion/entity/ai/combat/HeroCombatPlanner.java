package com.whitecloud233.herobrine_companion.entity.ai.combat;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public final class HeroCombatPlanner {
   private static final double MAX_ATTACK_ANGLE_COS = (double)0.25F;
   private static final double DEFAULT_RANGE_TOLERANCE = 0.45;
   private static final int MAX_TRACKED_ACTION_TICKS = 200;
   private static final int DEFAULT_BUFFER_TICKS = 6;
   private static final double INVALID_ACTION_SCORE = (double)-1000.0F;
   private static final double DEFAULT_HERO_VELOCITY_INFLUENCE = (double)0.5F;
   public static final ActionPhaseSpec SWORD_AUTO1_PHASES = new ActionPhaseSpec(3, 5, 9, 12);
   public static final ActionPhaseSpec SWORD_AUTO2_PHASES = new ActionPhaseSpec(3, 6, 10, 13);
   public static final ActionPhaseSpec DASH_PHASES = new ActionPhaseSpec(4, 7, 11, 15);
   public static final ActionPhaseSpec HEAVY_PHASES = new ActionPhaseSpec(5, 10, 14, 20);
   public static final ActionPhaseSpec AIR_PHASES = new ActionPhaseSpec(3, 6, 9, 13);
   public static final ActionProfile LIGHT1_PROFILE;
   public static final ActionProfile LIGHT2_PROFILE;
   public static final ActionProfile DASH_PROFILE;
   public static final ActionProfile SKILL_PROFILE;
   public static final ActionProfile AIR_PROFILE;
   public static final ActionProfile APPROACH_PROFILE;

   private HeroCombatPlanner() {
   }

   public static void tickEpicFightActionClock(HeroEntity hero) {
      if (hero != null) {
         hero.tickBattleBufferedAction();
         if (!hero.isBattleModeActive()) {
            hero.clearBattleBufferedAction();
         } else {
            if (hero.isBattleTapAction() || hero.isBattleHoldAction() || hero.isBattleReleaseAction()) {
               hero.setBattleActionTicks(Math.min(200, hero.getBattleActionTicks() + 1));
               ActionPhaseSpec phaseSpec = getActionProfileForState(hero, hero.getBattleActionState()).phaseSpec();
               if (phaseSpec.recoveryEndTick() > 0 && hero.getBattleActionTicks() > phaseSpec.recoveryEndTick() + 2) {
                  hero.resetBattleActionTimeline();
               }
            }

         }
      }
   }

   public static CombatContext captureContext(HeroEntity hero, LivingEntity target) {
      Vec3 heroPos = hero != null ? hero.position() : Vec3.ZERO;
      Vec3 targetPos = target != null ? target.position() : heroPos;
      Vec3 toTarget = targetPos.subtract(heroPos);
      double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
      double distance = Math.sqrt(toTarget.lengthSqr());
      double verticalDifference = target != null && hero != null ? target.getY() - hero.getY() : (double)0.0F;
      Vec3 heroVelocity = hero != null ? hero.getDeltaMovement() : Vec3.ZERO;
      Vec3 targetVelocity = target != null ? target.getDeltaMovement() : Vec3.ZERO;
      double heroSpeed = heroVelocity.length();
      double targetSpeed = targetVelocity.length();
      double targetMotionProjection = horizontalDistance > 1.0E-4 ? (targetVelocity.x * toTarget.x + targetVelocity.z * toTarget.z) / horizontalDistance : (double)0.0F;
      double heroFacingDot = computeFacingDot(hero, target, 0);
      double angleToTarget = Math.toDegrees(Math.acos(clamp(heroFacingDot, (double)-1.0F, (double)1.0F)));
      return new CombatContext(hero, target, distance, horizontalDistance, verticalDifference, angleToTarget, heroFacingDot, hero != null && target != null && hero.hasLineOfSight(target), heroSpeed, targetSpeed, targetMotionProjection > 0.015, targetMotionProjection < -0.015, hero != null && hero.onGround(), hero != null && !hero.onGround(), hero != null ? hero.getBattleComboStep() : 0, hero != null ? hero.getBattleActionState() : 0, hero != null ? hero.getBattleActionTicks() : 0, isAttackReplayLocked(hero, 6), getActionPhase(hero));
   }

   public static ActionPhase getActionPhase(HeroEntity hero) {
      if (hero == null) {
         return HeroCombatPlanner.ActionPhase.IDLE;
      } else {
         int actionState = hero.getBattleActionState();
         return actionState != 0 && actionState != 1 ? getActionProfileForState(hero, actionState).phaseSpec().phaseFor(hero.getBattleActionTicks()) : HeroCombatPlanner.ActionPhase.IDLE;
      }
   }

   public static ActionPhase getActionPhase(int actionState, int actionTicks) {
      return actionState != 0 && actionState != 1 ? getActionProfileForState(actionState).phaseSpec().phaseFor(actionTicks) : HeroCombatPlanner.ActionPhase.IDLE;
   }

   public static UtilityScores evaluateUtility(HeroEntity hero, LivingEntity target, CombatTuning tuning) {
      CombatContext context = captureContext(hero, target);
      if (!isUsableTarget(hero, target)) {
         return new UtilityScores((double)-1000.0F, (double)-1000.0F, (double)-1000.0F, (double)-1000.0F, (double)50.0F);
      } else {
         ActionProfile comboProfile = getLightComboProfile(hero);
         ActionProfile dashProfile = getActionProfile(HeroCombatPlanner.PlannedAction.DASH, hero, tuning);
         ActionProfile airProfile = getActionProfile(HeroCombatPlanner.PlannedAction.AIR, hero, tuning);
         ActionProfile skillProfile = getSkillProfile(tuning);
         double comboDistance = Math.sqrt(comboProfile.predictedDistanceSqr(hero, target));
         double dashDistance = Math.sqrt(dashProfile.predictedDistanceSqr(hero, target));
         double skillDistance = Math.sqrt(skillProfile.predictedDistanceSqr(hero, target));
         double comboRangeFit = fitMax(comboDistance, tuning.comboMaxDistance() + 0.7);
         double comboContinuation = hero.isBattleTapAction() ? (double)1.0F : (context.currentComboStep() > 0 ? 0.55 : 0.15);
         double targetStable = (double)1.0F - clamp01(context.targetSpeed() / 0.35);
         boolean comboAllowed = tuning.comboMaxDistance() > (double)0.0F && (comboProfile.predictedInReach(hero, target, tuning.comboMaxDistance()) || canQueueComboFollowUp(hero, target, square(tuning.comboMaxDistance() + (double)0.25F), comboProfile.windupTicks()));
         double comboScore = comboAllowed ? (double)45.0F * comboRangeFit + (double)25.0F * context.angleFit() + (double)30.0F * comboContinuation + (double)15.0F * targetStable + comboProfile.styleScore() - riskPenalty(context) : (double)-1000.0F;
         double dashScore = (double)-1000.0F;
         if (tuning.dashAvailable() && tuning.dashMaxDistance() > (double)0.0F) {
            double mediumRangeFit = fitBetween(dashDistance, tuning.dashMinDistance(), tuning.dashMaxDistance());
            boolean dashAllowed = isDashOpportunity(hero, target, tuning.dashMinDistance(), tuning.dashMaxDistance(), dashProfile.windupTicks());
            dashScore = dashAllowed ? (double)55.0F * mediumRangeFit + (double)35.0F * booleanScore(context.targetMovingAway()) + (double)20.0F * heroForwardPressure(context) + dashProfile.styleScore() - (double)30.0F * booleanScore(dashDistance < Math.max((double)0.0F, tuning.dashMinDistance() - 0.2)) - (double)10.0F * booleanScore(context.inAir()) : (double)-1000.0F;
         }

         double airScore = (double)-1000.0F;
         if (tuning.airAvailable() && tuning.airMaxDistance() > (double)0.0F) {
            double predictedAirRangeFit = fitMax(Math.sqrt(airProfile.predictedDistanceSqr(hero, target)), tuning.airMaxDistance() + 0.6);
            double verticalFit = (double)1.0F - clamp01(Math.abs(context.verticalDifference()) / (double)3.5F);
            boolean airAllowed = canStartAirAttack(hero, target, tuning.airMaxDistance(), airProfile.windupTicks());
            airScore = !airAllowed ? (double)-1000.0F : (double)60.0F * booleanScore(context.inAir() || !context.onGround()) + (double)25.0F * verticalFit + (double)20.0F * predictedAirRangeFit + airProfile.styleScore() - (double)10.0F * booleanScore(!context.hasLineOfSight());
         }

         double skillScore = (double)-1000.0F;
         if (tuning.skillAvailable() && tuning.skillMaxDistance() > (double)0.0F) {
            double skillRangeFit = fitBetween(skillDistance, tuning.skillMinDistance(), tuning.skillMaxDistance());
            boolean skillAllowed = skillDistance >= Math.max((double)0.0F, tuning.skillMinDistance() - 0.45) && skillProfile.predictedInReach(hero, target, tuning.skillMaxDistance());
            skillScore = !skillAllowed ? (double)-1000.0F : (double)40.0F + (double)30.0F * booleanScore(context.targetMovingToward() || targetStable > 0.55) + (double)20.0F * booleanScore((double)target.getHealth() > hero.getAttributeValue(Attributes.ATTACK_DAMAGE) * (double)2.5F) + (double)20.0F * skillRangeFit + skillProfile.styleScore() - (double)40.0F * missRisk(skillDistance, tuning.skillMaxDistance(), context);
         }

         double approachScore = (double)20.0F + (double)28.0F * clamp01((context.distance() - tuning.comboMaxDistance()) / (double)4.0F) + (double)12.0F * booleanScore(!context.hasLineOfSight()) + (double)10.0F * booleanScore(context.verticalDifference() > (double)2.5F);
         comboScore += bufferedActionBonus(hero, HeroCombatPlanner.PlannedAction.LIGHT_COMBO);
         dashScore += bufferedActionBonus(hero, HeroCombatPlanner.PlannedAction.DASH);
         airScore += bufferedActionBonus(hero, HeroCombatPlanner.PlannedAction.AIR);
         skillScore += bufferedActionBonus(hero, HeroCombatPlanner.PlannedAction.SKILL);
         approachScore += bufferedActionBonus(hero, HeroCombatPlanner.PlannedAction.APPROACH);
         return new UtilityScores(comboScore, dashScore, airScore, skillScore, approachScore);
      }
   }

   public static PlannerDecision decideAction(HeroEntity hero, LivingEntity target, CombatTuning tuning) {
      CombatContext context = captureContext(hero, target);
      UtilityScores scores = evaluateUtility(hero, target, tuning);
      PlannedAction action = scores.bestAction();
      ActionProfile profile = getActionProfile(action, hero, tuning);
      return new PlannerDecision(context, tuning, scores, action, profile, scoreFor(scores, action));
   }

   public static PlannedAction chooseAction(HeroEntity hero, LivingEntity target, CombatTuning tuning) {
      return decideAction(hero, target, tuning).action();
   }

   public static void queuePreferredFollowUp(HeroEntity hero, LivingEntity target, CombatTuning tuning) {
      if (hero != null && hero.isBattleModeActive() && shouldPrepareBufferedAction(hero)) {
         PlannerDecision decision = decideAction(hero, target, tuning);
         if (decision.action() == HeroCombatPlanner.PlannedAction.NONE) {
            hero.clearBattleBufferedAction();
         } else {
            hero.queueBattleBufferedAction(decision.action().bufferAction(), decision.profile().bufferTicks());
         }
      }
   }

   public static boolean prefersAction(HeroEntity hero, LivingEntity target, CombatTuning tuning, PlannedAction action) {
      if (hero == null) {
         return false;
      } else {
         if (shouldPrepareBufferedAction(hero)) {
            queuePreferredFollowUp(hero, target, tuning);
         }

         PlannedAction bufferedAction = HeroCombatPlanner.PlannedAction.fromBufferAction(hero.getBattleBufferedAction());
         if (bufferedAction == action) {
            return true;
         } else {
            PlannerDecision decision = decideAction(hero, target, tuning);
            if (decision.is(action)) {
               return true;
            } else {
               double requestedScore = scoreFor(decision.scores(), action);
               if (requestedScore <= (double)-999.0F) {
                  return false;
               } else {
                  double var10000;
                  switch (action.ordinal()) {
                     case 0:
                        var10000 = (double)0.0F;
                        break;
                     case 1:
                        var10000 = (double)100.0F;
                        break;
                     case 2:
                        var10000 = (double)55.0F;
                        break;
                     case 3:
                     case 4:
                     case 5:
                        var10000 = (double)24.0F;
                        break;
                     default:
                        throw new MatchException((String)null, (Throwable)null);
                  }

                  double fallbackMargin = var10000;
                  return requestedScore >= (double)20.0F && requestedScore + fallbackMargin >= decision.score();
               }
            }
         }
      }
   }

   public static boolean shouldPrepareBufferedAction(HeroEntity hero) {
      if (hero != null && hero.isBattleModeActive()) {
         int state = hero.getBattleActionState();
         if (state != 0 && state != 1) {
            return getActionProfileForState(hero, state).phaseSpec().shouldPreBuffer(hero.getBattleActionTicks()) || getActionPhase(hero) == HeroCombatPlanner.ActionPhase.RECOVERY;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static ActionProfile getActionProfileForState(HeroEntity hero, int actionState) {
      if (hero != null) {
         ActionProfile currentProfile = hero.getCurrentBattleActionProfile();
         if (currentProfile != null && actionState != 0 && actionState != 1) {
            return currentProfile;
         }
      }

      return getActionProfileForState(actionState);
   }

   public static ActionProfile getActionProfileForState(int actionState) {
      if (actionState == 3) {
         return LIGHT2_PROFILE;
      } else {
         return actionState != 4 && actionState != 5 ? LIGHT1_PROFILE : SKILL_PROFILE;
      }
   }

   public static ActionProfile getLightComboProfile(HeroEntity hero) {
      if (hero != null) {
         ActionProfile dynamicProfile = hero.getBattleComboProfile(hero.getBattleComboStep());
         if (dynamicProfile != null) {
            return dynamicProfile;
         }
      }

      return hero == null || hero.getBattleComboStep() % 2 != 1 && hero.getBattleActionState() != 2 ? LIGHT1_PROFILE : LIGHT2_PROFILE;
   }

   public static ActionProfile getActionProfile(PlannedAction action, HeroEntity hero, CombatTuning tuning) {
      ActionProfile var10000;
      switch (action.ordinal()) {
         case 0:
         case 1:
            var10000 = APPROACH_PROFILE;
            break;
         case 2:
            var10000 = getLightComboProfile(hero);
            break;
         case 3:
            var10000 = hero != null && hero.getBattleDashProfile() != null ? hero.getBattleDashProfile() : DASH_PROFILE;
            break;
         case 4:
            var10000 = hero != null && hero.getBattleAirProfile() != null ? hero.getBattleAirProfile() : AIR_PROFILE;
            break;
         case 5:
            var10000 = getSkillProfile(tuning);
            break;
         default:
            throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   public static ActionProfile getSkillProfile(CombatTuning tuning) {
      int windupTicks = tuning != null ? estimateSkillWindupTicks(tuning.skillMinDistance(), tuning.skillMaxDistance()) : SKILL_PROFILE.windupTicks();
      return new ActionProfile("SKILL", HeroCombatPlanner.PlannedAction.SKILL, windupTicks, (double)0.5F, (double)0.75F, SKILL_PROFILE.styleScore(), SKILL_PROFILE.bufferTicks(), HEAVY_PHASES);
   }

   private static double scoreFor(UtilityScores scores, PlannedAction action) {
      double var10000;
      switch (action.ordinal()) {
         case 0 -> var10000 = (double)-1000.0F;
         case 1 -> var10000 = scores.approachScore();
         case 2 -> var10000 = scores.comboScore();
         case 3 -> var10000 = scores.dashScore();
         case 4 -> var10000 = scores.airScore();
         case 5 -> var10000 = scores.skillScore();
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   public static boolean isAttackReplayLocked(HeroEntity hero, int lockTicks) {
      return hero != null && lockTicks > 0 && (hero.isBattleTapAction() || hero.isBattleHoldAction() || hero.isBattleReleaseAction()) && hero.getBattleActionTicks() > 0 && hero.getBattleActionTicks() < lockTicks;
   }

   public static boolean canStartPredictedMeleeCombo(HeroEntity hero, LivingEntity target, double maxDistance, int windupTicks) {
      if (isUsableTarget(hero, target) && !(maxDistance <= (double)0.0F)) {
         return hero.hasLineOfSight(target) && isFacingPredictedTarget(hero, target, windupTicks) && predictedDistanceSqr(hero, target, windupTicks, (double)0.5F) <= square(maxDistance + 0.45);
      } else {
         return false;
      }
   }

   public static boolean canStartMeleeAttack(HeroEntity hero, LivingEntity target, double reachSqr, int windupTicks) {
      if (isUsableTarget(hero, target) && !(reachSqr <= (double)0.0F) && hero.hasLineOfSight(target)) {
         double reach = Math.sqrt(reachSqr);
         return isFacingPredictedTarget(hero, target, windupTicks) && predictedDistanceSqr(hero, target, windupTicks, (double)0.5F) <= square(reach + 0.45);
      } else {
         return false;
      }
   }

   public static boolean canQueueComboFollowUp(HeroEntity hero, LivingEntity target, double reachSqr, int windupTicks) {
      if (isUsableTarget(hero, target) && !(reachSqr <= (double)0.0F)) {
         double reach = Math.sqrt(reachSqr);
         return hero.hasLineOfSight(target) && isFacingPredictedTarget(hero, target, windupTicks) && predictedDistanceSqr(hero, target, windupTicks, (double)0.5F) <= square(reach + (double)0.75F);
      } else {
         return false;
      }
   }

   public static boolean isDashOpportunity(HeroEntity hero, LivingEntity target, double minDistance, double maxDistance, int windupTicks) {
      if (isUsableTarget(hero, target) && !(maxDistance <= (double)0.0F) && hero.hasLineOfSight(target)) {
         double predictedDistanceSqr = predictedDistanceSqr(hero, target, windupTicks, (double)0.5F);
         double relaxedMin = Math.max((double)0.0F, minDistance - 0.35);
         double relaxedMax = maxDistance + 0.65;
         if (!(predictedDistanceSqr < square(relaxedMin)) && !(predictedDistanceSqr > square(relaxedMax))) {
            Vec3 toTarget = target.position().subtract(hero.position());
            Vec3 targetVelocity = target.getDeltaMovement();
            double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
            double targetMovingAway = (double)0.0F;
            if (horizontalDistance > 1.0E-4) {
               targetMovingAway = (targetVelocity.x * toTarget.x + targetVelocity.z * toTarget.z) / horizontalDistance;
            }

            boolean targetEscaping = targetMovingAway > 0.015;
            boolean heroAlreadyPressing = hero.getDeltaMovement().horizontalDistanceSqr() > 0.018;
            boolean comfortableRange = predictedDistanceSqr > square(minDistance + 0.45);
            return isFacingPredictedTarget(hero, target, windupTicks) && (targetEscaping || heroAlreadyPressing || comfortableRange);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean canStartAirAttack(HeroEntity hero, LivingEntity target, double maxDistance, int windupTicks) {
      if (isUsableTarget(hero, target) && !(maxDistance <= (double)0.0F) && !hero.isPassenger() && !hero.isInWater() && !hero.isInLava()) {
         if (!hero.isFloating() && !(hero.getDeltaMovement().y >= (double)0.25F)) {
            double verticalDelta = Math.abs(target.getY() - hero.getY());
            return verticalDelta <= (double)3.5F && hero.hasLineOfSight(target) && isFacingPredictedTarget(hero, target, windupTicks) && predictedDistanceSqr(hero, target, windupTicks, (double)0.5F) <= square(maxDistance + 0.6);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static double predictedDistanceSqr(HeroEntity hero, LivingEntity target, int ticksAhead, double heroVelocityInfluence) {
      if (hero != null && target != null) {
         double ticks = (double)Math.max(0, ticksAhead);
         Vec3 predictedHero = hero.position().add(hero.getDeltaMovement().scale(ticks * Math.max((double)0.0F, heroVelocityInfluence)));
         Vec3 predictedTarget = target.position().add(target.getDeltaMovement().scale(ticks));
         return predictedHero.distanceToSqr(predictedTarget);
      } else {
         return Double.MAX_VALUE;
      }
   }

   public static double predictedDistance(HeroEntity hero, LivingEntity target, int windupTicks) {
      return Math.sqrt(predictedDistanceSqr(hero, target, windupTicks, (double)0.5F));
   }

   private static double heroForwardPressure(CombatContext context) {
      return context.heroSpeed() > 0.08 && context.targetMovingAway() ? (double)1.0F : clamp01(context.heroSpeed() / 0.28);
   }

   private static double bufferedActionBonus(HeroEntity hero, PlannedAction action) {
      if (hero == null) {
         return (double)0.0F;
      } else {
         PlannedAction buffered = HeroCombatPlanner.PlannedAction.fromBufferAction(hero.getBattleBufferedAction());
         return buffered == action ? (double)18.0F : (double)0.0F;
      }
   }

   private static double riskPenalty(CombatContext context) {
      return (double)10.0F * clamp01(Math.abs(context.verticalDifference()) / (double)4.0F) + (double)8.0F * booleanScore(!context.hasLineOfSight()) + (double)6.0F * booleanScore(context.animationLocked());
   }

   private static double missRisk(double predictedDistance, double maxDistance, CombatContext context) {
      return clamp01((predictedDistance - maxDistance) / Math.max((double)1.0F, maxDistance + 0.4)) + 0.35 * booleanScore(!context.hasLineOfSight()) + (double)0.25F * clamp01(context.targetSpeed() / 0.4);
   }

   private static int estimateSkillWindupTicks(double minDistance, double maxDistance) {
      double range = maxDistance - minDistance;
      if (!(range >= (double)6.0F) && !(maxDistance >= (double)8.0F)) {
         return !(range >= (double)4.0F) && !(maxDistance >= (double)6.5F) ? 8 : 10;
      } else {
         return 12;
      }
   }

   private static boolean isFacingPredictedTarget(HeroEntity hero, LivingEntity target, int ticksAhead) {
      return computeFacingDot(hero, target, ticksAhead) >= (double)0.25F;
   }

   private static double computeFacingDot(HeroEntity hero, LivingEntity target, int ticksAhead) {
      if (hero != null && target != null) {
         Vec3 predictedTarget = target.position().add(target.getDeltaMovement().scale((double)Math.max(0, ticksAhead)));
         Vec3 toTarget = predictedTarget.subtract(hero.position());
         Vec3 horizontalToTarget = new Vec3(toTarget.x, (double)0.0F, toTarget.z);
         if (horizontalToTarget.lengthSqr() < 1.0E-4) {
            return (double)1.0F;
         } else {
            Vec3 look = hero.getLookAngle();
            Vec3 horizontalLook = new Vec3(look.x, (double)0.0F, look.z);
            return horizontalLook.lengthSqr() < 1.0E-4 ? (double)1.0F : horizontalLook.normalize().dot(horizontalToTarget.normalize());
         }
      } else {
         return (double)1.0F;
      }
   }

   private static boolean isUsableTarget(HeroEntity hero, LivingEntity target) {
      return hero != null && target != null && hero.isAlive() && target.isAlive() && !target.isRemoved();
   }

   private static double fitMax(double distance, double maxDistance) {
      return maxDistance <= (double)0.0F ? (double)0.0F : (double)1.0F - clamp01(distance / maxDistance);
   }

   private static double fitBetween(double distance, double minDistance, double maxDistance) {
      if (!(maxDistance <= (double)0.0F) && !(maxDistance < minDistance)) {
         if (distance < minDistance) {
            return (double)1.0F - clamp01((minDistance - distance) / Math.max(0.6, minDistance + 0.35));
         } else if (distance > maxDistance) {
            return (double)1.0F - clamp01((distance - maxDistance) / Math.max(0.6, maxDistance + 0.35));
         } else {
            double center = (minDistance + maxDistance) * (double)0.5F;
            double halfRange = Math.max((double)0.5F, (maxDistance - minDistance) * (double)0.5F);
            return (double)1.0F - clamp01(Math.abs(distance - center) / halfRange);
         }
      } else {
         return (double)0.0F;
      }
   }

   private static double booleanScore(boolean value) {
      return value ? (double)1.0F : (double)0.0F;
   }

   private static double clamp01(double value) {
      return clamp(value, (double)0.0F, (double)1.0F);
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   private static double square(double value) {
      return value * value;
   }

   static {
      LIGHT1_PROFILE = new ActionProfile("LIGHT1", HeroCombatPlanner.PlannedAction.LIGHT_COMBO, 4, 0.55, 0.45, (double)4.0F, 6, SWORD_AUTO1_PHASES);
      LIGHT2_PROFILE = new ActionProfile("LIGHT2", HeroCombatPlanner.PlannedAction.LIGHT_COMBO, 5, 0.6, 0.55, (double)6.0F, 6, SWORD_AUTO2_PHASES);
      DASH_PROFILE = new ActionProfile("DASH", HeroCombatPlanner.PlannedAction.DASH, 6, (double)0.75F, 0.65, (double)8.0F, 6, DASH_PHASES);
      SKILL_PROFILE = new ActionProfile("SKILL", HeroCombatPlanner.PlannedAction.SKILL, 10, 0.45, 0.6, (double)10.0F, 8, HEAVY_PHASES);
      AIR_PROFILE = new ActionProfile("AIR", HeroCombatPlanner.PlannedAction.AIR, 5, (double)0.5F, 0.6, (double)7.0F, 6, AIR_PHASES);
      APPROACH_PROFILE = new ActionProfile("APPROACH", HeroCombatPlanner.PlannedAction.APPROACH, 0, (double)0.5F, (double)0.0F, (double)0.0F, 4, new ActionPhaseSpec(0, 0, 0, 0));
   }

   public static enum ActionPhase {
      IDLE,
      STARTUP,
      ACTIVE,
      CHAIN,
      RECOVERY,
      LOCKED;
   }

   public static enum PlannedAction {
      NONE(0),
      APPROACH(1),
      LIGHT_COMBO(2),
      DASH(3),
      AIR(4),
      SKILL(5);

      private final int bufferAction;

      private PlannedAction(int bufferAction) {
         this.bufferAction = bufferAction;
      }

      public int bufferAction() {
         return this.bufferAction;
      }

      public static PlannedAction fromBufferAction(int bufferAction) {
         for(PlannedAction action : values()) {
            if (action.bufferAction == bufferAction) {
               return action;
            }
         }

         return NONE;
      }
   }

   public static record ActionPhaseSpec(int startupEndTick, int activeEndTick, int chainEndTick, int recoveryEndTick) {
      public ActionPhase phaseFor(int actionTicks) {
         int ticks = Math.max(0, actionTicks);
         if (this.recoveryEndTick <= 0) {
            return HeroCombatPlanner.ActionPhase.IDLE;
         } else if (ticks <= this.startupEndTick) {
            return HeroCombatPlanner.ActionPhase.STARTUP;
         } else if (ticks <= this.activeEndTick) {
            return HeroCombatPlanner.ActionPhase.ACTIVE;
         } else if (ticks <= this.chainEndTick) {
            return HeroCombatPlanner.ActionPhase.CHAIN;
         } else {
            return ticks <= this.recoveryEndTick ? HeroCombatPlanner.ActionPhase.RECOVERY : HeroCombatPlanner.ActionPhase.LOCKED;
         }
      }

      public boolean shouldPreBuffer(int actionTicks) {
         ActionPhase phase = this.phaseFor(actionTicks);
         return phase == HeroCombatPlanner.ActionPhase.ACTIVE || phase == HeroCombatPlanner.ActionPhase.CHAIN;
      }
   }

   public static record ActionProfile(String name, PlannedAction plannedAction, int windupTicks, double heroVelocityInfluence, double tolerance, double styleScore, int bufferTicks, ActionPhaseSpec phaseSpec) {
      public double predictedDistanceSqr(HeroEntity hero, LivingEntity target) {
         return HeroCombatPlanner.predictedDistanceSqr(hero, target, this.windupTicks, this.heroVelocityInfluence);
      }

      public boolean predictedInReach(HeroEntity hero, LivingEntity target, double reach) {
         return HeroCombatPlanner.isUsableTarget(hero, target) && hero.hasLineOfSight(target) && HeroCombatPlanner.isFacingPredictedTarget(hero, target, this.windupTicks) && this.predictedDistanceSqr(hero, target) <= HeroCombatPlanner.square(reach + this.tolerance);
      }
   }

   public static record CombatContext(HeroEntity hero, LivingEntity target, double distance, double horizontalDistance, double verticalDifference, double angleToTarget, double facingDot, boolean hasLineOfSight, double heroSpeed, double targetSpeed, boolean targetMovingAway, boolean targetMovingToward, boolean onGround, boolean inAir, int currentComboStep, int currentActionState, int currentActionTicks, boolean animationLocked, ActionPhase actionPhase) {
      public double angleFit() {
         return HeroCombatPlanner.clamp01((this.facingDot - (double)0.25F) / (double)0.75F);
      }
   }

   public static record CombatTuning(double comboMaxDistance, double dashMinDistance, double dashMaxDistance, double airMaxDistance, double skillMinDistance, double skillMaxDistance, boolean dashAvailable, boolean airAvailable, boolean skillAvailable) {
      public static CombatTuning comboOnly(double comboMaxDistance) {
         return new CombatTuning(comboMaxDistance, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F, false, false, false);
      }
   }

   public static record UtilityScores(double comboScore, double dashScore, double airScore, double skillScore, double approachScore) {
      public PlannedAction bestAction() {
         PlannedAction best = HeroCombatPlanner.PlannedAction.APPROACH;
         double bestScore = this.approachScore;
         if (this.comboScore > bestScore) {
            bestScore = this.comboScore;
            best = HeroCombatPlanner.PlannedAction.LIGHT_COMBO;
         }

         if (this.dashScore > bestScore) {
            bestScore = this.dashScore;
            best = HeroCombatPlanner.PlannedAction.DASH;
         }

         if (this.airScore > bestScore) {
            bestScore = this.airScore;
            best = HeroCombatPlanner.PlannedAction.AIR;
         }

         if (this.skillScore > bestScore) {
            best = HeroCombatPlanner.PlannedAction.SKILL;
         }

         return best;
      }
   }

   public static record PlannerDecision(CombatContext context, CombatTuning tuning, UtilityScores scores, PlannedAction action, ActionProfile profile, double score) {
      public boolean is(PlannedAction expectedAction) {
         return this.action == expectedAction;
      }
   }
}
