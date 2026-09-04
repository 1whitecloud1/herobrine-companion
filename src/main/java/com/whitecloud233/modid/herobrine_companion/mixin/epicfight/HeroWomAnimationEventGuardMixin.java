package com.whitecloud233.modid.herobrine_companion.mixin.epicfight;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yesman.epicfight.api.animation.property.AnimationEvent;
import yesman.epicfight.api.animation.property.AnimationParameters;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * 动画事件守卫：允许非玩家实体（如 Herobrine）播放玩家向的动画。
 *
 * <p>WOM（Weapons of Miracles）2.0.171 的部分动画事件把动画实体无条件
 * {@code (Player) patch.getOriginal()} 强转（例如 {@code ANTITHEUS_IDLE} 在
 * 6s/8.6s 的烟花音效事件）。Herobrine 是 {@code HumanoidMobPatch}，播放这些
 * 动画时事件触发即抛 {@code ClassCastException} 导致实体 tick 崩溃。</p>
 *
 * <p>本 mixin 把 {@code AnimationEvent.Event.fire} 的调用包一层 try/catch：
 * 仅当动画实体不是玩家、且抛出的是 {@code ClassCastException} 时静默跳过该事件；
 * 玩家播放时异常照常抛出（不掩盖真实 bug）。动画本身、攻击判定与不涉及玩家转型的
 * 事件（音效/粒子等）不受影响。</p>
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.api.animation.property.AnimationEvent", remap = false)
public abstract class HeroWomAnimationEventGuardMixin {
    @Redirect(
            method = {"execute", "executeWithNewParams"},
            at = @At(
                    value = "INVOKE",
                    target = "Lyesman/epicfight/api/animation/property/AnimationEvent$Event;fire(Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;Lyesman/epicfight/api/asset/AssetAccessor;Lyesman/epicfight/api/animation/property/AnimationParameters;)V"
            ),
            require = 0
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void herobrineCompanion$guardPlayerOnlyAnimationEvents(AnimationEvent.Event event,
                                                                   LivingEntityPatch<?> entitypatch,
                                                                   AssetAccessor<? extends StaticAnimation> animation,
                                                                   AnimationParameters<?, ?, ?, ?, ?, ?, ?, ?, ?, ?> params) {
        try {
            event.fire(entitypatch, animation, params);
        } catch (ClassCastException exception) {
            if (entitypatch.getOriginal() instanceof Player) {
                throw exception;
            }
        }
    }
}
