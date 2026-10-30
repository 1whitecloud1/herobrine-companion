package com.whitecloud233.modid.herobrine_companion.compat.mixin;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.util.HeroMenuValidity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让 AW 的原生 Hero 衣柜在 Hero 仍处于玩家已加载区块时保持打开。
 *
 * <p>AW 的 {@code SkinWardrobeMenu} 用「与玩家直线距离 ≤ 8 格」判定衣柜是否有效，
 * Hero 明明还在玩家周边的加载区块里，界面却会被强制关闭。这里把距离判定重定向为
 * {@link HeroMenuValidity#isHeroInPlayerLoadedArea} 的「同已加载区块区域」判定。
 *
 * <p>目标方法名随 AW 版本与运行时映射变化，三个选择器各覆盖一种情形：
 * <ul>
 *   <li>{@code stillValid} —— 开发环境（deobf，mojmap 名）与 AW 3.4.0+ 的 1.21.1
 *       mojmap 运行时；发行版 jar 上由 refmap 重映射为 {@code m_6875_}；</li>
 *   <li>{@code m_6875_} —— 1.20.1 Forge 发行版（SRG 运行时）兜底，防止 refmap
 *       未能解析 @Pseudo 目标；</li>
 *   <li>{@code abi$stillValid} —— AW 3.3+ 把方法体移进 compat 层后的新名字
 *       （AW 自己的名字，不参与 SRG 重混淆，refmap 也不会映射它）。</li>
 * </ul>
 * Mixin 只要求选择器整体命中至少一个方法，未命中的条目会被忽略。
 */
@Pseudo
@Mixin(targets = "moe.plushie.armourers_workshop.core.menu.SkinWardrobeMenu")
public abstract class ArmourersWorkshopHeroWardrobeRangeMixin {

    @Shadow @Final private Player player;

    @Redirect(
            method = {
                    "stillValid(Lnet/minecraft/world/entity/player/Player;)Z",
                    "m_6875_(Lnet/minecraft/world/entity/player/Player;)Z",
                    "abi$stillValid(Lnet/minecraft/world/entity/player/Player;)Z"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;distanceToSqr(DDD)D"))
    private double herobrineCompanion$useLoadedChunkRange(Entity target, double x, double y, double z) {
        if (target instanceof HeroEntity hero) {
            return HeroMenuValidity.isHeroInPlayerLoadedArea(hero, this.player)
                    ? 0.0D
                    : Double.POSITIVE_INFINITY;
        }
        return target.distanceToSqr(x, y, z);
    }
}
