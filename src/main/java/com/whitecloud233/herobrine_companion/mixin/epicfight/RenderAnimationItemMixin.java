package com.whitecloud233.herobrine_companion.mixin.epicfight;

import com.whitecloud233.herobrine_companion.compat.epicfight.AvalonWeaponAccess;
import com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightDebugLog;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * 修复 Hero 手持 Avalon 武器（爪/刺轮）时武器模型分离。
 *
 * <p>Avalon 的 {@code RenderAnimationItem.renderAnimationItem} 用 {@code entityPatch.getArmature()}
 * 取骨架并按关节 ID 蒙皮武器网格。但 Hero 是 {@code HumanoidMobPatch}，它覆写的 {@code getArmature()}
 * 会绕过 Avalon 注入在 {@code LivingEntityPatch.getArmature()} 上的 mixin，永远返回 Hero 自身骨架
 * （hero_biped_nightfall，56 关节，关节顺序与武器骨架不同）→ 爪刃/轮子按错误关节绑定 → 分离。
 *
 * <p>这里把武器渲染入口处的 {@code getArmature()} 调用重定向为「武器自身骨架」，网格按武器骨架
 * 顺序正确绑定；身体渲染走另一处 getArmature()，不受影响。</p>
 */
@Pseudo
@Mixin(targets = "com.merlin204.avalon.entity.client.renderer.patch.item.RenderAnimationItem", remap = false)
public abstract class RenderAnimationItemMixin {
    @Redirect(
            method = "renderAnimationItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;getArmature()Lyesman/epicfight/api/model/Armature;"
            ),
            require = 0
    )
    private Armature herobrineCompanion$weaponArmatureForHero(LivingEntityPatch<?> entityPatch) {
        if (entityPatch.getOriginal() instanceof HeroEntity hero) {
            ItemStack stack = hero.getMainHandItem();
            if (!stack.isEmpty() && AvalonWeaponAccess.isAvalonItem(stack)) {
                Armature weaponArmature = AvalonWeaponAccess.weaponArmature(stack);
                if (weaponArmature != null) {
                    HeroEpicFightDebugLog.repeatedEvent(hero, "weaponArmatureRedirect", "redirect",
                            "item=" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + ",armatureJoints=" + weaponArmature.getJointNumber());
                    return weaponArmature;
                } else {
                    // mixin 生效但武器骨架尚未加载/反射失败 —— 与「mixin 未生效」区分开
                    HeroEpicFightDebugLog.event(hero, "weaponArmatureRedirect",
                            "weaponArmature=null item=" + BuiltInRegistries.ITEM.getKey(stack.getItem()));
                }
            }
        }
        return entityPatch.getArmature();
    }
}
