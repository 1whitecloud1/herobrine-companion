package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.model.Armature;

import java.lang.reflect.Method;

/**
 * Avalon（IAvalonAnimationItem）武器反射访问（单一职责）。
 *
 * <p>Hero 是 HumanoidMobPatch，其覆写的 {@code getArmature()} 会绕过 Avalon 注入在
 * {@code LivingEntityPatch.getArmature()} 上的 mixin，导致武器网格永远按 Hero 骨架
 * （hero_biped_nightfall，56 关节，顺序与武器骨架不同）蒙皮 → 爪/轮模型分离。
 * 本类用反射读 Avalon 接口，复刻 mixin 逻辑：武器渲染阶段返回武器自身骨架。</p>
 */
public final class AvalonWeaponAccess {
    private static final String AVALON_ITEM_INTERFACE = "com.merlin204.avalon.item.animationitem.IAvalonAnimationItem";

    private static Class<?> avalonItemInterface;
    private static Method useAnimationArmatureMethod;
    private static Method getArmatureMethod;
    private static Method assetGetMethod;

    private AvalonWeaponAccess() {
    }

    public static boolean isAvalonItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && ensureLoaded() && avalonItemInterface.isAssignableFrom(stack.getItem().getClass());
    }

    public static boolean shouldUseWeaponArmature(ItemStack stack, int entityId) {
        if (!isAvalonItem(stack)) {
            return false;
        }
        try {
            return (Boolean) useAnimationArmatureMethod.invoke(stack.getItem(), entityId);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @Nullable
    public static Armature weaponArmature(ItemStack stack) {
        if (!isAvalonItem(stack)) {
            return null;
        }
        try {
            Object accessor = getArmatureMethod.invoke(stack.getItem());
            if (accessor == null) {
                return null;
            }
            Object loaded = assetGetMethod.invoke(accessor);
            return loaded instanceof Armature armature ? armature : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean ensureLoaded() {
        if (avalonItemInterface != null) {
            return true;
        }
        try {
            avalonItemInterface = Class.forName(AVALON_ITEM_INTERFACE, false, AvalonWeaponAccess.class.getClassLoader());
            useAnimationArmatureMethod = avalonItemInterface.getMethod("useAnimationArmature", int.class);
            getArmatureMethod = avalonItemInterface.getMethod("getArmature");
            assetGetMethod = AssetAccessor.class.getMethod("get");
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
