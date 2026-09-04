package com.whitecloud233.herobrine_companion.compat.epicfight;

import net.minecraft.world.item.ItemStack;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

import java.util.List;

/**
 * WOM 武器的 Epic Fight 攻击行为构建器。
 *
 * <p>职责单一：只负责把 {@link HeroWomWeaponCompat} 提供的 WOM 连招动画包装成
 * Herobrine 可执行的 {@link CombatBehaviors} 攻击行为。能力识别/解析在
 * {@link HeroWomWeaponCompat}，玩家式动作状态机在 {@link HeroEpicFightPatch}，
 * 本类不接触渲染与技能树。</p>
 *
 * <p>连招全部放进<b>同一个 BehaviorSeries</b>（与 nightfall 连段相同的结构）：
 * {@code AnimatedAttackGoal.tryProceed} 会在上一段动画的恢复尾帧
 * （{@code canBasicAttack=true} 而 {@code inaction} 未清除）直接把下一段动画切入，
 * 剪掉收招尾巴、不回退待机姿态，段与段之间无缝衔接。若每段各建一个 series，
 * 则必须等动画完全结束后经 {@code selectRandomBehaviorSeries} 重新起手，
 * 每段之间都会退回待机再打，表现为"卡卡的"。</p>
 *
 * <p>WOM 的连招动画（如 {@code agony_auto_1~4}）本身就是 Epic Fight 攻击动画，
 * 由 Herobrine 播放时会同步触发 WOM 动画自带的命中判定与特效事件。</p>
 *
 * <p>除连招系列外，另建一个<b>长按终结技系列</b>：把连招表的最后一段（WOM 的
 * "长按左键"蓄力终结技，如 {@code antitheus_guillotine}）作为独立行为周期性打出。
 * 玩家端长按左键等价于持续推进连招表、最终必然落到这段终结技；本系列让英雄在
 * 连招间隙也能直接祭出这段终结技，无需先打完一整轮连招。</p>
 */
public final class HeroWomCombatBehaviors {
    /** 终结技相对连招的选择权重（连招 100 / 终结技 30，约 23% 起手率）。 */
    private static final float HOLD_ATTACK_WEIGHT = 30.0F;

    /** 终结技冷却（tick）：冷却期间只打普通连招，避免终结技刷屏。 */
    private static final int HOLD_ATTACK_COOLDOWN = 80;

    /** WOM 攻击最小间隔（tick）：打完上一轮后至少间隔 24 tick（1.2 秒）才允许重新起手/选择系列。 */
    public static final int WOM_ATTACK_INTERVAL = 24;

    /** WOM 攻击动画播放速率系数：英雄无攻速属性（基础 1.0），乘 0.7 让连招动画放慢、
     * 段间隔回到 1.20.1 移植版沉稳节奏。epicfight 公式：playSpeed = 1 + (attackSpeed-1)*factor。 */
    public static final float WOM_ATTACK_SPEED_FACTOR = 0.7F;

    private HeroWomCombatBehaviors() {
    }

    /**
     * 为 WOM 武器构建攻击行为；非 WOM 自定义预设武器（含标准类型的 WOM 武器）返回 {@code null}，
     * 由调用方继续走 Epic Fight 数据包/玩家式连招路径。
     */
    public static CombatBehaviors.Builder<HumanoidMobPatch<?>> build(
            HeroEpicFightPatch patch, CapabilityItem capability, ItemStack stack) {
        if (patch == null || !HeroWomWeaponCompat.isSupported(stack) || capability == null || capability.isEmpty()) {
            return null;
        }

        List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> combos =
                HeroWomWeaponCompat.getAutoAttackMotions(capability, patch);
        if (combos.isEmpty()) {
            // GESETZ 等无连招表的自定义能力：与 Epic Fight 玩家端一致，使用基础动作。
            combos = CapabilityItem.getBasicAutoAttackMotions();
        }
        if (combos.isEmpty()) {
            return null;
        }
        final int comboSize = combos.size();

        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> comboSeries =
                CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                        .weight(100.0F)
                        .cooldown(3)
                        .canBeInterrupted(false)
                        .looping(false);
        for (int i = 0; i < combos.size(); i++) {
            int comboIndex = i;
            CombatBehaviors.Behavior.Builder<HumanoidMobPatch<?>> behavior =
                    patch.createWomComboAttackBehavior(combos.get(i), comboIndex, comboSize);
            if (comboIndex == 0) {
                // 起手段：comboStep==0 正常起手；若连招中途被打断（comboStep 停在中间段），
                // 允许从第一段重新起手，避免换目标/受击后永远接不上连招。
                behavior.custom(mobPatch -> patch.canStartWomCombo(mobPatch, comboIndex, comboSize)
                        || patch.canRestartWomCombo(mobPatch, comboSize));
            } else {
                // 后续段：链式 tryProceed 逐段推进，comboStep 精确对应段序。
                // 续接段不要求落地（WOM 连招动画自带位移/跳跃，动画结束时英雄常在空中）。
                behavior.custom(mobPatch -> patch.canContinueWomCombo(mobPatch, comboIndex, comboSize));
            }
            comboSeries.nextBehavior(behavior);
        }
        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        builder.newBehaviorSeries(comboSeries);

        // 长按终结技系列：独立打出连招表的最后一段（蓄力终结技）。
        // 选择门槛经 BehaviorSeries.canBeSelected → 首个行为的 custom 条件，
        // 与连招起手同一套就绪条件；权重低 + 冷却长，只作连招间隙的补充动作。
        AnimationManager.AnimationAccessor<? extends AttackAnimation> holdMotion =
                HeroWomWeaponCompat.getHoldAttackMotion(capability, patch);
        if (holdMotion != null) {
            CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> holdSeries =
                    CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                            .weight(HOLD_ATTACK_WEIGHT)
                            .cooldown(HOLD_ATTACK_COOLDOWN)
                            .canBeInterrupted(false)
                            .looping(false);
            holdSeries.nextBehavior(patch.createWomHoldAttackBehavior(holdMotion)
                    .custom(patch::canUseWomHoldAttack));
            builder.newBehaviorSeries(holdSeries);
        }
        return builder;
    }
}
