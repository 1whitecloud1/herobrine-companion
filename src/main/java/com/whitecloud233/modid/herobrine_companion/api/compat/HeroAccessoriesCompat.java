package com.whitecloud233.modid.herobrine_companion.api.compat;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.Slot;
import java.util.function.Consumer;

/** Bundled optional integration API. Callers never need third-party types. */
public final class HeroAccessoriesCompat {
    private static final String TARGET = "accessories.HeroAccessoriesCompat";

    private HeroAccessoriesCompat() { }

    public static boolean isLoaded() {
        Object result = OptionalCompat.call(TARGET, "isLoaded", new Class<?>[]{});
        return Boolean.TRUE.equals(result);
    }

    public static int addSlots(Consumer<Slot> slotConsumer, HeroEntity hero, int startX, int startY, int columns) {
        Object result = OptionalCompat.call(TARGET, "addSlots", new Class<?>[]{Consumer.class, HeroEntity.class, int.class, int.class, int.class}, slotConsumer, hero, startX, startY, columns);
        return result instanceof Integer count ? count : 0;
    }

    public static CompoundTag getAccessoriesDataTag(HeroEntity hero) {
        Object result = OptionalCompat.call(TARGET, "getAccessoriesDataTag", new Class<?>[]{HeroEntity.class}, hero);
        return result instanceof CompoundTag tag ? tag : new CompoundTag();
    }

    public static void setAccessoriesDataFromTag(HeroEntity hero, CompoundTag tag) {
        OptionalCompat.call(TARGET, "setAccessoriesDataFromTag", new Class<?>[]{HeroEntity.class, CompoundTag.class}, hero, tag);
    }

    public static void copyMissingItems(HeroEntity source, HeroEntity target) {
        if (!isLoaded()) {
            if (source != null && target != null && target.getAccessoriesDataTag().isEmpty()) {
                target.setAccessoriesDataFromTag(source.getAccessoriesDataTag().copy());
            }
            return;
        }
        OptionalCompat.call(TARGET, "copyMissingItems", new Class<?>[]{HeroEntity.class, HeroEntity.class}, source, target);
    }

    public static void loadMissingItemsFromTag(HeroEntity hero, CompoundTag tag) {
        if (!isLoaded()) {
            if (hero != null && tag != null && hero.getAccessoriesDataTag().isEmpty()) {
                hero.setAccessoriesDataFromTag(tag.copy());
            }
            return;
        }
        OptionalCompat.call(TARGET, "loadMissingItemsFromTag", new Class<?>[]{HeroEntity.class, CompoundTag.class}, hero, tag);
    }

    public static String describeEquippedItems(HeroEntity hero) {
        Object result = OptionalCompat.call(TARGET, "describeEquippedItems", new Class<?>[]{HeroEntity.class}, hero);
        return result instanceof String text ? text : "";
    }

}
