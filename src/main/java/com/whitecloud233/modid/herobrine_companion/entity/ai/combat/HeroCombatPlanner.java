package com.whitecloud233.modid.herobrine_companion.entity.ai.combat;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class HeroCombatPlanner {
    private static final double MAX_ATTACK_ANGLE_COS = 0.25D;
    private static final double DEFAULT_RANGE_TOLERANCE = 0.45D;
    private static final int MAX_TRACKED_ACTION_TICKS = 200;
    private static final int DEFAULT_BUFFER_TICKS = 6;
    private static final double INVALID_ACTION_SCORE = -1000.0D;
    private static final double DEFAULT_HERO_VELOCITY_INFLUENCE = 0.5D;

    public static final ActionPhaseSpec SWORD_AUTO1_PHASES = new ActionPhaseSpec(3, 5, 9, 12);
    public static final ActionPhaseSpec SWORD_AUTO2_PHASES = new ActionPhaseSpec(3, 6, 10, 13);
    public static final ActionPhaseSpec DASH_PHASES = new ActionPhaseSpec(4, 7, 11, 15);
    public static final ActionPhaseSpec HEAVY_PHASES = new ActionPhaseSpec(5, 10, 14, 20);
    public static final ActionPhaseSpec AIR_PHASES = new ActionPhaseSpec(3, 6, 9, 13);

    public static final ActionProfile LIGHT1_PROFILE = new ActionProfile("LIGHT1", PlannedAction.LIGHT_COMBO, 4, 0.55D, DEFAULT_RANGE_TOLERANCE, 4.0D, DEFAULT_BUFFER_TICKS, SWORD_AUTO1_PHASES);
    public static final ActionProfile LIGHT2_PROFILE = new ActionProfile("LIGHT2", PlannedAction.LIGHT_COMBO, 5, 0.6D, DEFAULT_RANGE_TOLERANCE + 0.1D, 6.0D, DEFAULT_BUFFER_TICKS, SWORD_AUTO2_PHASES);
    public static final ActionProfile DASH_PROFILE = new ActionProfile("DASH", PlannedAction.DASH, 6, 0.75D, 0.65D, 8.0D, DEFAULT_BUFFER_TICKS, DASH_PHASES);
    public static final ActionProfile SKILL_PROFILE = new ActionProfile("SKILL", PlannedAction.SKILL, 10, 0.45D, 0.6D, 10.0D, DEFAULT_BUFFER_TICKS + 2, HEAVY_PHASES);
    public static final ActionProfile AIR_PROFILE = new ActionProfile("AIR", PlannedAction.AIR, 5, 0.5D, 0.6D, 7.0D, DEFAULT_BUFFER_TICKS, AIR_PHASES);
    public static final ActionProfile APPROACH_PROFILE = new ActionProfile("APPROACH", PlannedAction.APPROACH, 0, DEFAULT_HERO_VELOCITY_INFLUENCE, 0.0D, 0.0D, 4, new ActionPhaseSpec(0, 0, 0, 0));

    private HeroCombatPlanner() {
    }

    public enum ActionPhase {
        IDLE,
        STARTUP,
        ACTIVE,
        CHAIN,
        RECOVERY,
        LOCKED
    }

    public enum PlannedAction {
        NONE(HeroEntity.BATTLE_BUFFER_NONE),
        APPROACH(HeroEntity.BATTLE_BUFFER_APPROACH),
        LIGHT_COMBO(HeroEntity.BATTLE_BUFFER_LIGHT),
        DASH(HeroEntity.BATTLE_BUFFER_DASH),
        AIR(HeroEntity.BATTLE_BUFFER_AIR),
        SKILL(HeroEntity.BATTLE_BUFFER_SKILL);

        private final int bufferAction;

        PlannedAction(int bufferAction) {
            this.bufferAction = bufferAction;
        }

        public int bufferAction() {
            return this.bufferAction;
        }

        public static PlannedAction fromBufferAction(int bufferAction) {
            for (PlannedAction action : values()) {
                if (action.bufferAction == bufferAction) {
                    return action;
                }
            }
            return NONE;
        }
    }

    public record ActionPhaseSpec(int startupEndTick, int activeEndTick, int chainEndTick, int recoveryEndTick) {
        public ActionPhase phaseFor(int actionTicks) {
            int ticks = Math.max(0, actionTicks);
            if (this.recoveryEndTick <= 0) {
                return ActionPhase.IDLE;
            }
            if (ticks <= this.startupEndTick) {
                return ActionPhase.STARTUP;
            }
            if (ticks <= this.activeEndTick) {
                return ActionPhase.ACTIVE;
            }
            if (ticks <= this.chainEndTick) {
                return ActionPhase.CHAIN;
            }
            if (ticks <= this.recoveryEndTick) {
                return ActionPhase.RECOVERY;
            }
            return ActionPhase.LOCKED;
        }

        public boolean shouldPreBuffer(int actionTicks) {
            ActionPhase phase = this.phaseFor(actionTicks);
            return phase == ActionPhase.ACTIVE || phase == ActionPhase.CHAIN;
        }
    }

    public record ActionProfile(
            String name,
            PlannedAction plannedAction,
            int windupTicks,
            double heroVelocityInfluence,
            double tolerance,
            double styleScore,
            int bufferTicks,
            ActionPhaseSpec phaseSpec
    ) {
        public double predictedDistanceSqr(HeroEntity hero, LivingEntity target) {
            return HeroCombatPlanner.predictedDistanceSqr(hero, target, this.windupTicks, this.heroVelocityInfluence);
        }

        public boolean predictedInReach(HeroEntity hero, LivingEntity target, double reach) {
            return isUsableTarget(hero, target)
                    && hero.hasLineOfSight(target)
                    && isFacingPredictedTarget(hero, target, this.windupTicks)
                    && this.predictedDistanceSqr(hero, target) <= square(reach + this.tolerance);
        }
    }

    public record CombatContext(
            HeroEntity hero,
            LivingEntity target,
            double distance,
            double horizontalDistance,
            double verticalDifference,
            double angleToTarget,
            double facingDot,
            boolean hasLineOfSight,
            double heroSpeed,
            double targetSpeed,
            boolean targetMovingAway,
            boolean targetMovingToward,
            boolean onGround,
            boolean inAir,
            int currentComboStep,
            int currentActionState,
            int currentActionTicks,
            boolean animationLocked,
            ActionPhase actionPhase
    ) {
        public double angleFit() {
            return clamp01((this.facingDot - MAX_ATTACK_ANGLE_COS) / (1.0D - MAX_ATTACK_ANGLE_COS));
        }
    }

    public record CombatTuning(
            double comboMaxDistance,
            double dashMinDistance,
            double dashMaxDistance,
            double airMaxDistance,
            double skillMinDistance,
            double skillMaxDistance,
            boolean dashAvailable,
            boolean airAvailable,
            boolean skillAvailable
    ) {
        public static CombatTuning comboOnly(double comboMaxDistance) {
            return new CombatTuning(comboMaxDistance, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, false, false, false);
        }
    }

    public record UtilityScores(
            double comboScore,
            double dashScore,
            double airScore,
            double skillScore,
            double approachScore
    ) {
        public PlannedAction bestAction() {
            PlannedAction best = PlannedAction.APPROACH;
            double bestScore = this.approachScore;

            if (this.comboScore > bestScore) {
                bestScore = this.comboScore;
                best = PlannedAction.LIGHT_COMBO;
            }
            if (this.dashScore > bestScore) {
                bestScore = this.dashScore;
                best = PlannedAction.DASH;
            }
            if (this.airScore > bestScore) {
                bestScore = this.airScore;
                best = PlannedAction.AIR;
            }
            if (this.skillScore > bestScore) {
                best = PlannedAction.SKILL;
            }

            return best;
        }
    }

    public record PlannerDecision(
            CombatContext context,
            CombatTuning tuning,
            UtilityScores scores,
            PlannedAction action,
            ActionProfile profile,
            double score
    ) {
        public boolean is(PlannedAction expectedAction) {
            return this.action == expectedAction;
        }
    }

    public static void tickEpicFightActionClock(HeroEntity hero) {
        if (hero == null) {
            return;
        }

        hero.tickBattleBufferedAction();
        if (!hero.isBattleModeActive()) {
            hero.clearBattleBufferedAction();
            return;
        }

        if (hero.isBattleTapAction() || hero.isBattleHoldAction() || hero.isBattleReleaseAction()) {
            hero.setBattleActionTicks(Math.min(MAX_TRACKED_ACTION_TICKS, hero.getBattleActionTicks() + 1));
        }
    }

    public static CombatContext captureContext(HeroEntity hero, LivingEntity target) {
        Vec3 heroPos = hero != null ? hero.position() : Vec3.ZERO;
        Vec3 targetPos = target != null ? target.position() : heroPos;
        Vec3 toTarget = targetPos.subtract(heroPos);
        double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        double distance = Math.sqrt(toTarget.lengthSqr());
        double verticalDifference = target != null && hero != null ? target.getY() - hero.getY() : 0.0D;
        Vec3 heroVelocity = hero != null ? hero.getDeltaMovement() : Vec3.ZERO;
        Vec3 targetVelocity = target != null ? target.getDeltaMovement() : Vec3.ZERO;
        double heroSpeed = heroVelocity.length();
        double targetSpeed = targetVelocity.length();
        double targetMotionProjection = horizontalDistance > 1.0E-4D
                ? (targetVelocity.x * toTarget.x + targetVelocity.z * toTarget.z) / horizontalDistance
                : 0.0D;
        double heroFacingDot = computeFacingDot(hero, target, 0);
        double angleToTarget = Math.toDegrees(Math.acos(clamp(heroFacingDot, -1.0D, 1.0D)));

        return new CombatContext(
                hero,
                target,
                distance,
                horizontalDistance,
                verticalDifference,
                angleToTarget,
                heroFacingDot,
                hero != null && target != null && hero.hasLineOfSight(target),
                heroSpeed,
                targetSpeed,
                targetMotionProjection > 0.015D,
                targetMotionProjection < -0.015D,
                hero != null && hero.onGround(),
                hero != null && !hero.onGround(),
                hero != null ? hero.getBattleComboStep() : 0,
                hero != null ? hero.getBattleActionState() : HeroEntity.BATTLE_ACTION_IDLE,
                hero != null ? hero.getBattleActionTicks() : 0,
                isAttackReplayLocked(hero, 6),
                getActionPhase(hero)
        );
    }

    public static ActionPhase getActionPhase(HeroEntity hero) {
        if (hero == null) {
            return ActionPhase.IDLE;
        }
        return getActionPhase(hero.getBattleActionState(), hero.getBattleActionTicks());
    }

    public static ActionPhase getActionPhase(int actionState, int actionTicks) {
        if (actionState == HeroEntity.BATTLE_ACTION_IDLE || actionState == HeroEntity.BATTLE_ACTION_APPROACH) {
            return ActionPhase.IDLE;
        }

        return getActionProfileForState(actionState).phaseSpec().phaseFor(actionTicks);
    }

    public static UtilityScores evaluateUtility(HeroEntity hero, LivingEntity target, CombatTuning tuning) {
        CombatContext context = captureContext(hero, target);
        if (!isUsableTarget(hero, target)) {
            return new UtilityScores(INVALID_ACTION_SCORE, INVALID_ACTION_SCORE, INVALID_ACTION_SCORE, INVALID_ACTION_SCORE, 50.0D);
        }

        ActionProfile comboProfile = getLightComboProfile(hero);
        ActionProfile skillProfile = getSkillProfile(tuning);
        double comboDistance = Math.sqrt(comboProfile.predictedDistanceSqr(hero, target));
        double dashDistance = Math.sqrt(DASH_PROFILE.predictedDistanceSqr(hero, target));
        double skillDistance = Math.sqrt(skillProfile.predictedDistanceSqr(hero, target));
        double comboRangeFit = fitMax(comboDistance, tuning.comboMaxDistance() + 0.7D);
        double comboContinuation = hero.isBattleTapAction() ? 1.0D : (context.currentComboStep() > 0 ? 0.55D : 0.15D);
        double targetStable = 1.0D - clamp01(context.targetSpeed() / 0.35D);
        boolean comboAllowed = tuning.comboMaxDistance() > 0.0D
                && (comboProfile.predictedInReach(hero, target, tuning.comboMaxDistance())
                || canQueueComboFollowUp(hero, target, square(tuning.comboMaxDistance() + 0.25D), comboProfile.windupTicks()));
        double comboScore = comboAllowed
                ? 45.0D * comboRangeFit
                + 25.0D * context.angleFit()
                + 30.0D * comboContinuation
                + 15.0D * targetStable
                + comboProfile.styleScore()
                - riskPenalty(context)
                : INVALID_ACTION_SCORE;

        double dashScore = INVALID_ACTION_SCORE;
        if (tuning.dashAvailable() && tuning.dashMaxDistance() > 0.0D) {
            double mediumRangeFit = fitBetween(dashDistance, tuning.dashMinDistance(), tuning.dashMaxDistance());
            boolean dashAllowed = isDashOpportunity(hero, target, tuning.dashMinDistance(), tuning.dashMaxDistance(), DASH_PROFILE.windupTicks());
            dashScore = dashAllowed
                    ? 55.0D * mediumRangeFit
                    + 35.0D * booleanScore(context.targetMovingAway())
                    + 20.0D * heroForwardPressure(context)
                    + DASH_PROFILE.styleScore()
                    - 30.0D * booleanScore(dashDistance < Math.max(0.0D, tuning.dashMinDistance() - 0.2D))
                    - 10.0D * booleanScore(context.inAir())
                    : INVALID_ACTION_SCORE;
        }

        double airScore = INVALID_ACTION_SCORE;
        if (tuning.airAvailable() && tuning.airMaxDistance() > 0.0D) {
            double predictedAirRangeFit = fitMax(Math.sqrt(AIR_PROFILE.predictedDistanceSqr(hero, target)), tuning.airMaxDistance() + 0.6D);
            double verticalFit = 1.0D - clamp01(Math.abs(context.verticalDifference()) / 3.5D);
            boolean airAllowed = canStartAirAttack(hero, target, tuning.airMaxDistance(), AIR_PROFILE.windupTicks());
            airScore = airAllowed
                    ? 60.0D * booleanScore(context.inAir() || !context.onGround())
                    + 25.0D * verticalFit
                    + 20.0D * predictedAirRangeFit
                    + AIR_PROFILE.styleScore()
                    - 10.0D * booleanScore(!context.hasLineOfSight())
                    : INVALID_ACTION_SCORE;
        }

        double skillScore = INVALID_ACTION_SCORE;
        if (tuning.skillAvailable() && tuning.skillMaxDistance() > 0.0D) {
            double skillRangeFit = fitBetween(skillDistance, tuning.skillMinDistance(), tuning.skillMaxDistance());
            boolean skillAllowed = skillDistance >= Math.max(0.0D, tuning.skillMinDistance() - 0.45D)
                    && skillProfile.predictedInReach(hero, target, tuning.skillMaxDistance());
            skillScore = skillAllowed
                    ? 40.0D
                    + 30.0D * booleanScore(context.targetMovingToward() || targetStable > 0.55D)
                    + 20.0D * booleanScore(target.getHealth() > hero.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 2.5D)
                    + 20.0D * skillRangeFit
                    + skillProfile.styleScore()
                    - 40.0D * missRisk(skillDistance, tuning.skillMaxDistance(), context)
                    : INVALID_ACTION_SCORE;
        }

        double approachScore = 20.0D
                + 28.0D * clamp01((context.distance() - tuning.comboMaxDistance()) / 4.0D)
                + 12.0D * booleanScore(!context.hasLineOfSight())
                + 10.0D * booleanScore(context.verticalDifference() > 2.5D);

        comboScore += bufferedActionBonus(hero, PlannedAction.LIGHT_COMBO);
        dashScore += bufferedActionBonus(hero, PlannedAction.DASH);
        airScore += bufferedActionBonus(hero, PlannedAction.AIR);
        skillScore += bufferedActionBonus(hero, PlannedAction.SKILL);
        approachScore += bufferedActionBonus(hero, PlannedAction.APPROACH);

        return new UtilityScores(comboScore, dashScore, airScore, skillScore, approachScore);
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
        if (hero == null || !hero.isBattleModeActive() || !shouldPrepareBufferedAction(hero)) {
            return;
        }

        PlannerDecision decision = decideAction(hero, target, tuning);
        if (decision.action() == PlannedAction.NONE) {
            hero.clearBattleBufferedAction();
            return;
        }

        hero.queueBattleBufferedAction(decision.action().bufferAction(), decision.profile().bufferTicks());
    }

    public static boolean prefersAction(HeroEntity hero, LivingEntity target, CombatTuning tuning, PlannedAction action) {
        if (hero == null) {
            return false;
        }

        if (shouldPrepareBufferedAction(hero)) {
            queuePreferredFollowUp(hero, target, tuning);
        }

        PlannedAction bufferedAction = PlannedAction.fromBufferAction(hero.getBattleBufferedAction());
        if (bufferedAction == action) {
            return true;
        }

        PlannerDecision decision = decideAction(hero, target, tuning);
        if (decision.is(action)) {
            return true;
        }

        double requestedScore = scoreFor(decision.scores(), action);
        if (requestedScore <= INVALID_ACTION_SCORE + 1.0D) {
            return false;
        }

        double fallbackMargin = switch (action) {
            case LIGHT_COMBO -> 55.0D;
            case APPROACH -> 100.0D;
            case DASH, AIR, SKILL -> 24.0D;
            case NONE -> 0.0D;
        };
        return requestedScore >= 20.0D && requestedScore + fallbackMargin >= decision.score();
    }

    public static boolean shouldPrepareBufferedAction(HeroEntity hero) {
        if (hero == null || !hero.isBattleModeActive()) {
            return false;
        }

        int state = hero.getBattleActionState();
        if (state == HeroEntity.BATTLE_ACTION_IDLE || state == HeroEntity.BATTLE_ACTION_APPROACH) {
            return false;
        }

        return getActionProfileForState(state).phaseSpec().shouldPreBuffer(hero.getBattleActionTicks())
                || getActionPhase(hero) == ActionPhase.RECOVERY;
    }

    public static ActionProfile getActionProfileForState(int actionState) {
        if (actionState == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2) {
            return LIGHT2_PROFILE;
        }
        if (actionState == HeroEntity.BATTLE_ACTION_HEAVY_HOLD || actionState == HeroEntity.BATTLE_ACTION_HEAVY_RELEASE) {
            return SKILL_PROFILE;
        }
        return LIGHT1_PROFILE;
    }

    public static ActionProfile getLightComboProfile(HeroEntity hero) {
        if (hero != null && (hero.getBattleComboStep() % 2 == 1 || hero.getBattleActionState() == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1)) {
            return LIGHT2_PROFILE;
        }
        return LIGHT1_PROFILE;
    }

    public static ActionProfile getActionProfile(PlannedAction action, HeroEntity hero, CombatTuning tuning) {
        return switch (action) {
            case LIGHT_COMBO -> getLightComboProfile(hero);
            case DASH -> DASH_PROFILE;
            case AIR -> AIR_PROFILE;
            case SKILL -> getSkillProfile(tuning);
            case APPROACH, NONE -> APPROACH_PROFILE;
        };
    }

    public static ActionProfile getSkillProfile(CombatTuning tuning) {
        int windupTicks = tuning != null ? estimateSkillWindupTicks(tuning.skillMinDistance(), tuning.skillMaxDistance()) : SKILL_PROFILE.windupTicks();
        return new ActionProfile("SKILL", PlannedAction.SKILL, windupTicks, DEFAULT_HERO_VELOCITY_INFLUENCE, 0.75D, SKILL_PROFILE.styleScore(), SKILL_PROFILE.bufferTicks(), HEAVY_PHASES);
    }

    private static double scoreFor(UtilityScores scores, PlannedAction action) {
        return switch (action) {
            case LIGHT_COMBO -> scores.comboScore();
            case DASH -> scores.dashScore();
            case AIR -> scores.airScore();
            case SKILL -> scores.skillScore();
            case APPROACH -> scores.approachScore();
            case NONE -> INVALID_ACTION_SCORE;
        };
    }

    public static boolean isAttackReplayLocked(HeroEntity hero, int lockTicks) {
        return hero != null
                && lockTicks > 0
                && (hero.isBattleTapAction() || hero.isBattleHoldAction() || hero.isBattleReleaseAction())
                && hero.getBattleActionTicks() > 0
                && hero.getBattleActionTicks() < lockTicks;
    }

    public static boolean canStartPredictedMeleeCombo(HeroEntity hero, LivingEntity target, double maxDistance, int windupTicks) {
        if (!isUsableTarget(hero, target) || maxDistance <= 0.0D) {
            return false;
        }

        return hero.hasLineOfSight(target)
                && isFacingPredictedTarget(hero, target, windupTicks)
                && predictedDistanceSqr(hero, target, windupTicks, DEFAULT_HERO_VELOCITY_INFLUENCE) <= square(maxDistance + DEFAULT_RANGE_TOLERANCE);
    }

    public static boolean canStartMeleeAttack(HeroEntity hero, LivingEntity target, double reachSqr, int windupTicks) {
        if (!isUsableTarget(hero, target) || reachSqr <= 0.0D || !hero.hasLineOfSight(target)) {
            return false;
        }

        double reach = Math.sqrt(reachSqr);
        return isFacingPredictedTarget(hero, target, windupTicks)
                && predictedDistanceSqr(hero, target, windupTicks, DEFAULT_HERO_VELOCITY_INFLUENCE) <= square(reach + DEFAULT_RANGE_TOLERANCE);
    }

    public static boolean canQueueComboFollowUp(HeroEntity hero, LivingEntity target, double reachSqr, int windupTicks) {
        if (!isUsableTarget(hero, target) || reachSqr <= 0.0D) {
            return false;
        }

        double reach = Math.sqrt(reachSqr);
        return hero.hasLineOfSight(target)
                && isFacingPredictedTarget(hero, target, windupTicks)
                && predictedDistanceSqr(hero, target, windupTicks, DEFAULT_HERO_VELOCITY_INFLUENCE) <= square(reach + 0.75D);
    }

    public static boolean isDashOpportunity(HeroEntity hero, LivingEntity target, double minDistance, double maxDistance, int windupTicks) {
        if (!isUsableTarget(hero, target) || maxDistance <= 0.0D || !hero.hasLineOfSight(target)) {
            return false;
        }

        double predictedDistanceSqr = predictedDistanceSqr(hero, target, windupTicks, DEFAULT_HERO_VELOCITY_INFLUENCE);
        double relaxedMin = Math.max(0.0D, minDistance - 0.35D);
        double relaxedMax = maxDistance + 0.65D;
        if (predictedDistanceSqr < square(relaxedMin) || predictedDistanceSqr > square(relaxedMax)) {
            return false;
        }

        Vec3 toTarget = target.position().subtract(hero.position());
        Vec3 targetVelocity = target.getDeltaMovement();
        double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        double targetMovingAway = 0.0D;
        if (horizontalDistance > 1.0E-4D) {
            targetMovingAway = (targetVelocity.x * toTarget.x + targetVelocity.z * toTarget.z) / horizontalDistance;
        }

        boolean targetEscaping = targetMovingAway > 0.015D;
        boolean heroAlreadyPressing = hero.getDeltaMovement().horizontalDistanceSqr() > 0.018D;
        boolean comfortableRange = predictedDistanceSqr > square(minDistance + 0.45D);
        return isFacingPredictedTarget(hero, target, windupTicks) && (targetEscaping || heroAlreadyPressing || comfortableRange);
    }

    public static boolean canStartAirAttack(HeroEntity hero, LivingEntity target, double maxDistance, int windupTicks) {
        if (!isUsableTarget(hero, target) || maxDistance <= 0.0D || hero.isPassenger() || hero.isInWater() || hero.isInLava()) {
            return false;
        }

        if (hero.isFloating() || hero.getDeltaMovement().y >= 0.25D) {
            return false;
        }

        double verticalDelta = Math.abs(target.getY() - hero.getY());
        return verticalDelta <= 3.5D
                && hero.hasLineOfSight(target)
                && isFacingPredictedTarget(hero, target, windupTicks)
                && predictedDistanceSqr(hero, target, windupTicks, DEFAULT_HERO_VELOCITY_INFLUENCE) <= square(maxDistance + 0.6D);
    }

    public static double predictedDistanceSqr(HeroEntity hero, LivingEntity target, int ticksAhead, double heroVelocityInfluence) {
        if (hero == null || target == null) {
            return Double.MAX_VALUE;
        }

        double ticks = Math.max(0, ticksAhead);
        Vec3 predictedHero = hero.position().add(hero.getDeltaMovement().scale(ticks * Math.max(0.0D, heroVelocityInfluence)));
        Vec3 predictedTarget = target.position().add(target.getDeltaMovement().scale(ticks));
        return predictedHero.distanceToSqr(predictedTarget);
    }

    public static double predictedDistance(HeroEntity hero, LivingEntity target, int windupTicks) {
        return Math.sqrt(predictedDistanceSqr(hero, target, windupTicks, DEFAULT_HERO_VELOCITY_INFLUENCE));
    }

    private static double heroForwardPressure(CombatContext context) {
        return context.heroSpeed() > 0.08D && context.targetMovingAway() ? 1.0D : clamp01(context.heroSpeed() / 0.28D);
    }

    private static double bufferedActionBonus(HeroEntity hero, PlannedAction action) {
        if (hero == null) {
            return 0.0D;
        }

        PlannedAction buffered = PlannedAction.fromBufferAction(hero.getBattleBufferedAction());
        return buffered == action ? 18.0D : 0.0D;
    }

    private static double riskPenalty(CombatContext context) {
        return 10.0D * clamp01(Math.abs(context.verticalDifference()) / 4.0D)
                + 8.0D * booleanScore(!context.hasLineOfSight())
                + 6.0D * booleanScore(context.animationLocked());
    }

    private static double missRisk(double predictedDistance, double maxDistance, CombatContext context) {
        return clamp01((predictedDistance - maxDistance) / Math.max(1.0D, maxDistance + 0.4D))
                + 0.35D * booleanScore(!context.hasLineOfSight())
                + 0.25D * clamp01(context.targetSpeed() / 0.4D);
    }

    private static int estimateSkillWindupTicks(double minDistance, double maxDistance) {
        double range = maxDistance - minDistance;
        if (range >= 6.0D || maxDistance >= 8.0D) {
            return 12;
        }
        if (range >= 4.0D || maxDistance >= 6.5D) {
            return 10;
        }
        return 8;
    }

    private static boolean isFacingPredictedTarget(HeroEntity hero, LivingEntity target, int ticksAhead) {
        return computeFacingDot(hero, target, ticksAhead) >= MAX_ATTACK_ANGLE_COS;
    }

    private static double computeFacingDot(HeroEntity hero, LivingEntity target, int ticksAhead) {
        if (hero == null || target == null) {
            return 1.0D;
        }

        Vec3 predictedTarget = target.position().add(target.getDeltaMovement().scale(Math.max(0, ticksAhead)));
        Vec3 toTarget = predictedTarget.subtract(hero.position());
        Vec3 horizontalToTarget = new Vec3(toTarget.x, 0.0D, toTarget.z);
        if (horizontalToTarget.lengthSqr() < 1.0E-4D) {
            return 1.0D;
        }

        Vec3 look = hero.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-4D) {
            return 1.0D;
        }

        return horizontalLook.normalize().dot(horizontalToTarget.normalize());
    }

    private static boolean isUsableTarget(HeroEntity hero, LivingEntity target) {
        return hero != null
                && target != null
                && hero.isAlive()
                && target.isAlive()
                && !target.isRemoved();
    }

    private static double fitMax(double distance, double maxDistance) {
        if (maxDistance <= 0.0D) {
            return 0.0D;
        }

        return 1.0D - clamp01(distance / maxDistance);
    }

    private static double fitBetween(double distance, double minDistance, double maxDistance) {
        if (maxDistance <= 0.0D || maxDistance < minDistance) {
            return 0.0D;
        }
        if (distance < minDistance) {
            return 1.0D - clamp01((minDistance - distance) / Math.max(0.6D, minDistance + 0.35D));
        }
        if (distance > maxDistance) {
            return 1.0D - clamp01((distance - maxDistance) / Math.max(0.6D, maxDistance + 0.35D));
        }

        double center = (minDistance + maxDistance) * 0.5D;
        double halfRange = Math.max(0.5D, (maxDistance - minDistance) * 0.5D);
        return 1.0D - clamp01(Math.abs(distance - center) / halfRange);
    }

    private static double booleanScore(boolean value) {
        return value ? 1.0D : 0.0D;
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0D, 1.0D);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double square(double value) {
        return value * value;
    }
}
