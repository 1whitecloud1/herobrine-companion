package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import org.jetbrains.annotations.Nullable;
import yesman.epicfight.api.animation.AnimationPlayer;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能触发 tick（单一职责）：每 tick 把 Hero 当前播放的动画匹配回档案里的技能系列，
 * 在触发时间点把特效交给 {@link HeroNightfallSkillEffects} 门面执行，并同步 Hero 的战斗动作状态。
 */
final class HeroNightfallSkillTicker {
    private static final Map<Integer, Integer> IDLE_STALL_TICKS = new ConcurrentHashMap<>();

    private HeroNightfallSkillTicker() {
    }

    static void tickSkillEffects(HeroEpicFightPatch patch, HeroEntity hero) {
        if (patch == null || hero == null || hero.level().isClientSide) {
            return;
        }

        // P6：强制技能会话推进（动画结束 → 若工具自动进的战斗态则退出）。
        HeroSkillSessionTracker.tick(hero, readCurrentAnimation(patch));

        HeroNightfallProfile profile = HeroNightfallProfiles.resolve(hero.getMainHandItem());
        if (profile == null) {
            return;
        }

        AnimationPlayer player = patch.getAnimator() != null ? patch.getAnimator().getPlayerFor(null) : null;
        if (player == null || player.isEmpty()) {
            reportIdleStall(hero);
            syncBattleActionState(hero, profile, null, null);
            return;
        }
        clearIdleStall(hero);

        AssetAccessor<? extends StaticAnimation> currentAnimation = player.getRealAnimation();
        if (currentAnimation == null || currentAnimation.isEmpty()) {
            syncBattleActionState(hero, profile, null, null);
            return;
        }

        HeroNightfallSkillSeries matchedSkillSeries = findMatchingSkillSeries(profile, currentAnimation);
        int comboIndex = findMatchingComboIndex(profile, currentAnimation);
        syncBattleActionState(hero, profile, currentAnimation, matchedSkillSeries);

        // 命中技能系列 → 该系列特效，否则回退档案 skillEffect；
        // 命中连段普攻 → 只应用档案 comboEffect（如赤月普攻叠血疫），其余武器为 null 不变
        HeroNightfallSkillSpec effect = null;
        boolean alignToWeaponJoint = false;
        if (matchedSkillSeries != null) {
            effect = matchedSkillSeries.effectSpec() != null ? matchedSkillSeries.effectSpec() : profile.skillEffect();
            alignToWeaponJoint = matchedSkillSeries.anchorToWeaponJoint();
        } else if (comboIndex >= 0) {
            effect = profile.comboEffect();
        }
        if (effect == null) {
            return;
        }

        // 只在跨越触发时间点的那一帧结算，避免每 tick 重复触发
        if (player.getPrevElapsedTime() >= effect.triggerTime() || player.getElapsedTime() < effect.triggerTime()) {
            return;
        }

        HeroNightfallSkillEffects.apply(patch, hero, effect, alignToWeaponJoint);
    }

    /** 读取当前播放动画；无动画/为空返回 null（P6 会话推进用）。 */
    @Nullable
    private static AssetAccessor<? extends StaticAnimation> readCurrentAnimation(HeroEpicFightPatch patch) {
        AnimationPlayer player = patch != null && patch.getAnimator() != null ? patch.getAnimator().getPlayerFor(null) : null;
        if (player == null || player.isEmpty()) {
            return null;
        }
        AssetAccessor<? extends StaticAnimation> animation = player.getRealAnimation();
        return animation == null || animation.isEmpty() ? null : animation;
    }

    private static void syncBattleActionState(HeroEntity hero,
                                              HeroNightfallProfile profile,
                                              @Nullable AssetAccessor<? extends StaticAnimation> currentAnimation,
                                              @Nullable HeroNightfallSkillSeries matchedSkillSeries) {
        if (hero == null) {
            return;
        }

        int desiredState = -1;
        if (matchedSkillSeries != null && matchedSkillSeries.actionState() >= 0) {
            desiredState = matchedSkillSeries.actionState();
        } else if (currentAnimation != null) {
            int comboIndex = findMatchingComboIndex(profile, currentAnimation);
            if (comboIndex >= 0) {
                desiredState = resolveComboActionState(comboIndex);
            }
        }

        if (desiredState < 0) {
            if (hero.getBattleActionState() == HeroEntity.BATTLE_ACTION_HEAVY_HOLD) {
                int waitingTicks = hero.getBattleActionTicks() + 1;
                hero.setBattleActionTicks(waitingTicks);
                if (waitingTicks <= 8) {
                    return;
                }
            }

            if (isTrackedAttackState(hero.getBattleActionState())) {
                hero.resetBattleActionTimeline();
            }
            return;
        }

        if (hero.getBattleActionState() != desiredState) {
            hero.setBattleActionState(desiredState);
            hero.setBattleActionTicks(0);
        }
    }

    private static int findMatchingComboIndex(HeroNightfallProfile profile, AssetAccessor<? extends StaticAnimation> currentAnimation) {
        for (int comboIndex = 0; comboIndex < profile.comboAnimations().size(); comboIndex++) {
            if (profile.comboAnimations().get(comboIndex).matches(currentAnimation)) {
                return comboIndex;
            }
        }
        return -1;
    }

    @Nullable
    private static HeroNightfallSkillSeries findMatchingSkillSeries(HeroNightfallProfile profile, AssetAccessor<? extends StaticAnimation> currentAnimation) {
        for (HeroNightfallSkillSeries skillSeries : profile.skillSeries()) {
            if (skillSeries.matches(currentAnimation)) {
                return skillSeries;
            }
        }
        return null;
    }

    private static int resolveComboActionState(int comboIndex) {
        return comboIndex % 2 == 0 ? HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1 : HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2;
    }

    private static boolean isTrackedAttackState(int actionState) {
        return actionState == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1
                || actionState == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2
                || actionState == HeroEntity.BATTLE_ACTION_HEAVY_HOLD
                || actionState == HeroEntity.BATTLE_ACTION_HEAVY_RELEASE;
    }

    /** 发呆检测：战斗 + 有目标 + 动画长时间为空 → 行为执行器没有选中任何行为（selectRandomBehaviorSeries 返回 null）。 */
    private static void reportIdleStall(HeroEntity hero) {
        boolean hasTarget = hero.getTarget() != null && hero.getTarget().isAlive();
        if (!hero.isBattleModeActive() || !hasTarget) {
            IDLE_STALL_TICKS.remove(hero.getId());
            return;
        }

        int idleTicks = IDLE_STALL_TICKS.merge(hero.getId(), 1, Integer::sum);
        if (idleTicks > 40 && (idleTicks & (idleTicks - 1)) == 0) {
            HeroEpicFightDebugLog.event(hero, "nightfallStall",
                    "idle-stalled ticks=" + idleTicks + ",action=" + hero.getBattleActionState()
                            + ",combo=" + hero.getBattleComboStep() + ",targetAlive=" + hasTarget);
        }
    }

    private static void clearIdleStall(HeroEntity hero) {
        IDLE_STALL_TICKS.remove(hero.getId());
    }
}
