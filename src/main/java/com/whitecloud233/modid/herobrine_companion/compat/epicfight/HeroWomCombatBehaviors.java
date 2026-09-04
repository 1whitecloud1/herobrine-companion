package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

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
 * <p>续接门槛使用专门的 {@link HeroEpicFightPatch#canContinueWomCombo}：不要求
 * {@code onGround()}（WOM 连招动画自带跳跃位移，恢复尾帧切入时英雄常在空气中），
 * 只校验战斗状态与连段序号，保证 {@code tryProceed} 能在空中无缝接上下一段。</p>
 *
 * <p>WOM 的连招动画（如 {@code agony_auto_1~4}）本身就是 Epic Fight 攻击动画，
 * 由 Herobrine 播放时会同步触发 WOM 动画自带的命中判定与特效事件。</p>
 */
public final class HeroWomCombatBehaviors {
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
            combos = CapabilityItem.getBasicAutoAttackMotion();
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
                // 续接段不能用起手段的 canStartWomCombo（含 onGround 地面判定）：
                // WOM 连招动画自带位移/跳跃，恢复尾帧切入时英雄常在空气中，
                // 续接失败会摧毁整条链，导致永远只打第一段。
                behavior.custom(mobPatch -> patch.canContinueWomCombo(mobPatch, comboIndex, comboSize));
            }
            comboSeries.nextBehavior(behavior);
        }
        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        builder.newBehaviorSeries(comboSeries);
        return builder;
    }
}
