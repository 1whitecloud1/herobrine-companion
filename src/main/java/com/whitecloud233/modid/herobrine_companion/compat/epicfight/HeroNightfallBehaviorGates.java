package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.effect.HeroBloodCurseStore;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroCombatPlanner;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * 行为判定层（单一职责）：一系列"这条招式系列现在能不能起手/接续"的谓词。
 * 把夜幕的输入驱动分支（冲刺/跳跃/长按/招架成功/叠层）翻译成怪物侧条件：
 * - 冲刺招式 = 移速/距离判定
 * - 空中招式 = 离地判定
 * - 反击 = 目标正在攻击时判定（等效夜幕"招架成功"）
 * - 血疫收割 = 目标血疫层数达标时判定
 */
final class HeroNightfallBehaviorGates {
    static final int MIN_BLOOD_CURSE_FOR_HARVEST = 3;
    /** 悬停兜底近战命中间隔（tick），避免兜底伤害无间隔连打 */
    private static final int HOVER_FALLBACK_COOLDOWN_TICKS = 40;
    private static final String HOVER_FALLBACK_KEY = "__hover_fallback_melee__";

    private HeroNightfallBehaviorGates() {
    }

    static boolean canStartDefaultCombo(HumanoidMobPatch<?> mobPatch, HeroNightfallProfile profile, int comboIndex, double comboRange) {
        HeroEntity hero = getHero(mobPatch);
        LivingEntity target = getTrackedTarget(hero);
        if (hero == null || target == null) {
            return hero == null;
        }

        // 地面连段：浮空/跳跃中不接，留给空中攻击（起飞追击时 Hero 悬空，只有空中招式可选）
        if (!hero.onGround()) {
            // 悬停兜底：浮空追击中没有空中招式可用（武器缺 airAttack 技能系列）时，
            // 贴近目标周期性直接结算一次近战伤害，避免 Hero 悬停在树顶/高处目标旁干瞪眼
            maybeFallbackHoverMelee(hero, target, comboRange);
            HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "ground", "comboBlocked=airborne");
            return false;
        }

        // 连段是"贴身必打"的保底行为：不再依赖工具分偏好（否则技能分高时连段被饿死、技能冷却期卡住），
        // 只要贴身 + 面向即可起手；技能系列仍由 prefersAction(SKILL) 自己把关。
        if (!hero.isBattleModeActive() || hero.isBattleHoldAction() || hero.isBattleReleaseAction()) {
            HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "state",
                    "comboBlocked=state battle=" + hero.isBattleModeActive()
                            + ",hold=" + hero.isBattleHoldAction() + ",release=" + hero.isBattleReleaseAction()
                            + ",action=" + hero.getBattleActionState() + ",shock=" + hero.shockTicks);
            return false;
        }
        if (!hero.hasLineOfSight(target)) {
            HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "los", "comboBlocked=los");
            return false;
        }

        // 连段范围放宽：实测 Hero 常在目标 6 格左右（技能射程内）停驻，若门太窄会"只放技能、放完发呆"。
        // 预测距离已含追击速度，起手瞬间 Hero 会前压补刀，故 +1.2 是安全的。
        double reach = Math.max(comboRange, profile.attackRadius() + 0.45D) + 1.2D;
        int windupTicks = 4 + Math.min(comboIndex, 3);
        double predictedDistanceSqr = HeroCombatPlanner.predictedDistanceSqr(hero, target, windupTicks, 0.5D);
        if (predictedDistanceSqr > reach * reach) {
            HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "range",
                    "comboBlocked=range dist=" + String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(predictedDistanceSqr))
                            + ",reach=" + String.format(java.util.Locale.ROOT, "%.2f", reach));
            return false;
        }

        // 软朝向：允许 EF 技能动画收尾时略微偏角仍可接连段，避免"打完技能回不了身"的呆滞
        if (!isSoftlyFacingTarget(hero, target, windupTicks)) {
            HeroEpicFightDebugLog.repeatedEvent(hero, "comboGate", "facing", "comboBlocked=facing");
            return false;
        }
        return true;
    }

    private static boolean isSoftlyFacingTarget(HeroEntity hero, LivingEntity target, int ticksAhead) {
        if (hero == null || target == null) {
            return true;
        }
        net.minecraft.world.phys.Vec3 predictedTarget = target.position().add(target.getDeltaMovement().scale(Math.max(0, ticksAhead)));
        net.minecraft.world.phys.Vec3 toTarget = predictedTarget.subtract(hero.position());
        net.minecraft.world.phys.Vec3 horizontalToTarget = new net.minecraft.world.phys.Vec3(toTarget.x, 0.0D, toTarget.z);
        if (horizontalToTarget.lengthSqr() < 1.0E-4D) {
            return true;
        }
        net.minecraft.world.phys.Vec3 look = hero.getLookAngle();
        net.minecraft.world.phys.Vec3 horizontalLook = new net.minecraft.world.phys.Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-4D) {
            return true;
        }
        return horizontalLook.normalize().dot(horizontalToTarget.normalize()) >= -0.1D;
    }

    static boolean canContinueDefaultCombo(HumanoidMobPatch<?> mobPatch, HeroNightfallProfile profile, int comboIndex, double comboRange) {
        HeroEntity hero = getHero(mobPatch);
        LivingEntity target = getTrackedTarget(hero);
        if (hero == null || target == null) {
            return hero == null;
        }

        // 接续也放宽到与起手一致：命中后目标被击退/略微偏角时仍然衔接，避免"打一下断一下"
        if (!hero.onGround()) {
            return false;
        }
        if (!hero.isBattleModeActive() || hero.isBattleHoldAction() || hero.isBattleReleaseAction()) {
            return false;
        }
        if (!hero.hasLineOfSight(target)) {
            return false;
        }

        double followUpReach = Math.max(comboRange, profile.attackRadius() + 0.75D) + 1.2D;
        int windupTicks = 3 + Math.min(comboIndex, 2);
        if (HeroCombatPlanner.predictedDistanceSqr(hero, target, windupTicks, 0.5D) > followUpReach * followUpReach) {
            return false;
        }
        return isSoftlyFacingTarget(hero, target, windupTicks);
    }

    static boolean canStartSkillSeries(HumanoidMobPatch<?> mobPatch, HeroNightfallSkillSeries skillSeries) {
        if (skillSeries.airAttack()) {
            return canStartAirAttack(mobPatch, skillSeries.maxDistance());
        }

        HeroEntity hero = getHero(mobPatch);
        if (hero == null) {
            return true;
        }

        // 地面技能：浮空/跳跃中不接，留给空中攻击（起飞追击时只有空中招式可选）
        if (!hero.onGround()) {
            return false;
        }

        LivingEntity target = getTrackedTarget(hero);
        if (target == null || !hero.isBattleModeActive() || hero.isBattleHoldAction() || hero.isBattleReleaseAction()) {
            return false;
        }

        HeroNightfallProfile profile = HeroNightfallProfiles.resolve(hero.getMainHandItem());
        HeroCombatPlanner.CombatTuning tuning = profile != null ? getNightfallCombatTuning(profile) : HeroCombatPlanner.CombatTuning.comboOnly(skillSeries.maxDistance());

        if (isProtectedComboStartup(hero) || HeroCombatPlanner.isAttackReplayLocked(hero, 7)) {
            return false;
        }

        int windupTicks = estimateSkillWindupTicks(skillSeries);
        double predictedDistanceSqr = HeroCombatPlanner.predictedDistanceSqr(hero, target, windupTicks, 0.35D);
        double relaxedMinDistance = Math.max(0.0D, skillSeries.minDistance() - 0.35D);
        if (predictedDistanceSqr < relaxedMinDistance * relaxedMinDistance) {
            return false;
        }

        return HeroCombatPlanner.canStartPredictedMeleeCombo(hero, target, skillSeries.maxDistance(), windupTicks)
                && HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.SKILL);
    }

    static boolean canContinueSkillSeries(HumanoidMobPatch<?> mobPatch) {
        HeroEntity hero = getHero(mobPatch);
        if (hero == null) {
            return true;
        }

        LivingEntity target = getTrackedTarget(hero);
        return target != null && hero.isBattleModeActive();
    }

    static boolean canStartAirAttack(HumanoidMobPatch<?> mobPatch, double maxDistance) {
        HeroEntity hero = getHero(mobPatch);
        if (hero == null || !hero.isAlive() || hero.isPassenger() || hero.isInWater() || hero.isInLava()) {
            return false;
        }

        LivingEntity target = getTrackedTarget(hero);
        HeroNightfallProfile profile = HeroNightfallProfiles.resolve(hero.getMainHandItem());
        HeroCombatPlanner.CombatTuning tuning = profile != null ? getNightfallCombatTuning(profile) : HeroCombatPlanner.CombatTuning.comboOnly(maxDistance);
        // 起飞后 Hero 悬空：地面连段/技能都不可选，空中攻击是唯一行为，无需再经工具分偏好把关
        boolean floating = hero.isFloating();
        return target != null
                && (floating || hero.onGround() || hero.getDeltaMovement().y > -0.7D)
                && !HeroCombatPlanner.isAttackReplayLocked(hero, 8)
                && HeroCombatPlanner.canStartAirAttack(hero, target, maxDistance, 5)
                && (floating || HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.AIR));
    }

    /**
     * 悬停兜底：浮空追击且没有空中招式可用（武器缺 airAttack 技能系列）时，
     * 贴近目标周期性直接结算一次近战伤害，避免 Hero 悬停在树顶/高处目标旁干瞪眼。
     * 有冷却，仅在浮空 + 贴脸 + 视线内触发；本方法只补伤害，不改变"地面连段不可选"的判定。
     */
    private static void maybeFallbackHoverMelee(HeroEntity hero, LivingEntity target, double comboRange) {
        if (hero == null || target == null || !hero.isFloating()
                || hero.isBattleHoldAction() || hero.isBattleReleaseAction()
                || HeroCombatPlanner.isAttackReplayLocked(hero, 8)) {
            return;
        }
        if (!hero.hasLineOfSight(target)) {
            return;
        }
        double reach = Math.max(2.5D, comboRange);
        if (hero.distanceToSqr(target) > reach * reach) {
            return;
        }
        long gameTime = hero.level().getGameTime();
        if (HeroSkillCooldownStore.isOnCooldown(hero, HOVER_FALLBACK_KEY, HOVER_FALLBACK_COOLDOWN_TICKS, gameTime)) {
            return;
        }
        HeroSkillCooldownStore.markUsed(hero, HOVER_FALLBACK_KEY, gameTime);
        hero.doHurtTarget(target);
    }

    /** 反击：目标正在攻击时起手（等效夜幕"招架成功"分支）。 */
    static boolean canStartCounter(HumanoidMobPatch<?> mobPatch, HeroNightfallSkillSeries series) {
        HeroEntity hero = getHero(mobPatch);
        LivingEntity target = getTrackedTarget(hero);
        if (hero == null || target == null || !hero.isBattleModeActive()
                || hero.isBattleHoldAction() || hero.isBattleReleaseAction()) {
            return false;
        }
        if (!isTargetAttacking(hero, target)) {
            return false;
        }
        return canStartSkillSeries(mobPatch, series);
    }

    /** 血疫收割：目标血疫层数达标时起手（等效夜幕"叠层→引爆"分支）。 */
    static boolean canStartBloodHarvest(HumanoidMobPatch<?> mobPatch, HeroNightfallSkillSeries series) {
        HeroEntity hero = getHero(mobPatch);
        LivingEntity target = getTrackedTarget(hero);
        if (hero == null || target == null || !hero.isBattleModeActive()
                || hero.isBattleHoldAction() || hero.isBattleReleaseAction()) {
            return false;
        }
        if (!HeroBloodCurseStore.hasAtLeast(target, MIN_BLOOD_CURSE_FOR_HARVEST)) {
            return false;
        }
        return canStartSkillSeries(mobPatch, series);
    }

    /** 目标是否处于攻击前摇/挥击状态。 */
    static boolean isTargetAttacking(HeroEntity hero, LivingEntity target) {
        if (target == null || !target.isAlive() || hero == null) {
            return false;
        }
        // 原版挥击：getAttackAnim 公开返回当前挥击进度（0~1，非挥击时为 0）
        if (target.getAttackAnim(0.0F) > 0.0F) {
            return true;
        }
        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(target, LivingEntityPatch.class);
        return patch != null && patch.getEntityState().attacking();
    }

    static boolean canStartCrimsonMoonCombo(HumanoidMobPatch<?> mobPatch, HeroNightfallProfile profile, int comboIndex, double comboRange) {
        HeroEntity hero = getHero(mobPatch);
        return (hero == null || hero.getBattleActionState() != HeroEntity.BATTLE_ACTION_HEAVY_HOLD)
                && canStartDefaultCombo(mobPatch, profile, comboIndex, comboRange);
    }

    static boolean canContinueCrimsonMoonCombo(HumanoidMobPatch<?> mobPatch, HeroNightfallProfile profile, int comboIndex, double comboRange) {
        HeroEntity hero = getHero(mobPatch);
        return (hero == null || hero.getBattleActionState() != HeroEntity.BATTLE_ACTION_HEAVY_HOLD)
                && canContinueDefaultCombo(mobPatch, profile, comboIndex, comboRange);
    }

    static boolean canStartCrimsonMoonHarvest(HumanoidMobPatch<?> mobPatch, HeroNightfallSkillSeries harvest) {
        HeroEntity hero = getHero(mobPatch);
        return (hero == null || hero.getBattleActionState() != HeroEntity.BATTLE_ACTION_HEAVY_HOLD)
                && canStartBloodHarvest(mobPatch, harvest);
    }

    static boolean canStartCrimsonMoonRelease(HumanoidMobPatch<?> mobPatch, HeroNightfallSkillSeries scarletEnd) {
        HeroEntity hero = getHero(mobPatch);
        if (hero == null || hero.getBattleActionState() != HeroEntity.BATTLE_ACTION_HEAVY_HOLD || hero.getBattleActionTicks() < 6) {
            return false;
        }

        LivingEntity target = getTrackedTarget(hero);
        return target != null
                && HeroCombatPlanner.canStartPredictedMeleeCombo(hero, target, scarletEnd.maxDistance(), estimateSkillWindupTicks(scarletEnd));
    }

    static boolean isProtectedComboStartup(HeroEntity hero) {
        return hero.isBattleTapAction() && hero.getBattleActionTicks() > 0 && hero.getBattleActionTicks() <= 5;
    }

    static int estimateSkillWindupTicks(HeroNightfallSkillSeries skillSeries) {
        double range = skillSeries.maxDistance() - skillSeries.minDistance();
        if (range >= 5.0D) {
            return 8;
        }
        if (range >= 3.0D) {
            return 6;
        }
        return 5;
    }

    static HeroCombatPlanner.CombatTuning getNightfallCombatTuning(HeroNightfallProfile profile) {
        double comboMaxDistance = Math.max(3.6D, profile.attackRadius() + 0.6D);
        double airMaxDistance = 0.0D;
        double skillMinDistance = Double.MAX_VALUE;
        double skillMaxDistance = 0.0D;
        boolean airAvailable = false;
        boolean skillAvailable = false;

        for (HeroNightfallSkillSeries skillSeries : profile.skillSeries()) {
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
            skillMinDistance = 0.0D;
        }

        return new HeroCombatPlanner.CombatTuning(
                comboMaxDistance,
                0.0D,
                0.0D,
                airMaxDistance,
                skillMinDistance,
                skillMaxDistance,
                false,
                airAvailable,
                skillAvailable
        );
    }

    @Nullable
    static HeroEntity getHero(HumanoidMobPatch<?> mobPatch) {
        return mobPatch instanceof HeroEpicFightPatch heroPatch ? heroPatch.getOriginal() : null;
    }

    @Nullable
    static LivingEntity getTrackedTarget(HeroEntity hero) {
        if (hero == null) {
            return null;
        }
        LivingEntity target = hero.getTarget();
        return target != null && target.isAlive() && !target.isRemoved() ? target : null;
    }
}
