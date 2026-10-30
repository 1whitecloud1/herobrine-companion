package com.whitecloud233.herobrine_companion.api.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Bundled optional integration API. Callers never need third-party types. */
public final class HeroAWCompat {
    private static final String TARGET = "ArmourerWorkshop.HeroAWCompat";

    private HeroAWCompat() { }

    public static boolean isLoaded() {
        Object result = OptionalCompat.call(TARGET, "isLoaded", new Class<?>[]{});
        return Boolean.TRUE.equals(result);
    }

    public static boolean isAwItem(ItemStack stack) {
        Object result = OptionalCompat.call(TARGET, "isAwItem", new Class<?>[]{ItemStack.class}, stack);
        return Boolean.TRUE.equals(result);
    }

    public static boolean openWardrobeFor(Entity entity) {
        Object result = OptionalCompat.call(TARGET, "openWardrobeFor", new Class<?>[]{Entity.class}, entity);
        return Boolean.TRUE.equals(result);
    }

}
