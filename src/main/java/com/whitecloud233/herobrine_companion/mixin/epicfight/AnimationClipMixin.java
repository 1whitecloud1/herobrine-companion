package com.whitecloud233.herobrine_companion.mixin.epicfight;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 渲染线程死循环守卫。
 *
 * <p>根因：{@code ConcurrentLinkAnimation.getPoseByTime} 里
 * {@code elapsed % totalTime}，当被混合动画的 {@code AnimationClip.clipTime} 为 0 时
 * 得 NaN；NaN 传入 {@code AnimationClip.getPoseInTime(time)} 后，内部二分查找里
 * {@code bakedTimes[i] <= time} 与 {@code > time} 对 NaN 恒为 false，begin/end 永不更新
 * → while 死循环 → 渲染线程 100% CPU 转圈 → 游戏画面冻结（无崩溃报告）。
 *
 * <p>把 NaN 时间修正为 0.0F 即可让二分查找正常终止。典型触发：Hero 从空闲/空动画
 * （EMPTY clipTime=0）或从一把武器（如赤月）过渡到另一把武器的动作时。
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.api.animation.AnimationClip", remap = false)
public abstract class AnimationClipMixin {
    @ModifyVariable(method = "getPoseInTime", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float herobrineCompanion$sanitizeNaNTime(float time) {
        return Float.isNaN(time) ? 0.0F : time;
    }
}
