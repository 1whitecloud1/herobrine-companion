package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.compat.accessories.HeroAccessoriesCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.Optional;

public class HeroEquipment {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    };
    private static final EquipmentSlot[] HAND_SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private static ItemStack parseItemStackOrEmpty(HeroEntity hero, CompoundTag tag) {
        if (tag == null || tag.isEmpty() || !tag.contains("id", Tag.TAG_STRING)) {
            return ItemStack.EMPTY;
        }

        return ItemStack.parse(hero.registryAccess(), tag).orElse(ItemStack.EMPTY);
    }

    // ================= [原生装备序列化 1.21.1] =================
    public static ListTag getArmorItemsTag(HeroEntity hero) {
        ListTag tag = new ListTag();
        for (ItemStack stack : hero.getArmorSlots()) {
            if (!stack.isEmpty()) {
                Tag savedTag = stack.saveOptional(hero.registryAccess());
                if (savedTag instanceof CompoundTag ct) tag.add(ct);
            } else {
                tag.add(new CompoundTag());
            }
        }
        return tag;
    }

    public static ListTag getHandItemsTag(HeroEntity hero) {
        ListTag tag = new ListTag();
        for (ItemStack stack : hero.getHandSlots()) {
            if (!stack.isEmpty()) {
                Tag savedTag = stack.saveOptional(hero.registryAccess());
                if (savedTag instanceof CompoundTag ct) tag.add(ct);
            } else {
                tag.add(new CompoundTag());
            }
        }
        return tag;
    }

    public static void loadEquipmentFromTag(HeroEntity hero, ListTag armor, ListTag hands) {
        loadSlots(hero, armor, ARMOR_SLOTS, false);
        loadSlots(hero, hands, HAND_SLOTS, false);
    }

    public static void loadMissingEquipmentFromTag(HeroEntity hero, ListTag armor, ListTag hands) {
        loadSlots(hero, armor, ARMOR_SLOTS, true);
        loadSlots(hero, hands, HAND_SLOTS, true);
    }

    private static void loadSlots(HeroEntity hero, ListTag items, EquipmentSlot[] slots, boolean onlyMissing) {
        if (items == null) return;
        for (int i = 0; i < Math.min(items.size(), slots.length); i++) {
            if (!onlyMissing || hero.getItemBySlot(slots[i]).isEmpty()) {
                hero.setItemSlot(slots[i], parseItemStackOrEmpty(hero, items.getCompound(i)));
            }
        }
    }

    /** 无法确认旧实体的主次时，只补空槽，保留存活实体已有的装备。 */
    public static void copyMissingEquipment(HeroEntity source, HeroEntity target) {
        copyMissingSlots(source, target, ARMOR_SLOTS);
        copyMissingSlots(source, target, HAND_SLOTS);
        if (target.isCuriosBackSlotEmpty()) {
            CompoundTag back = source.getCuriosBackItemTag();
            if (!back.isEmpty()) target.setCuriosBackItemFromTag(back.copy());
        }
        HeroAccessoriesCompat.copyMissingItems(source, target);
    }

    private static void copyMissingSlots(HeroEntity source, HeroEntity target, EquipmentSlot[] slots) {
        for (EquipmentSlot slot : slots) {
            ItemStack item = source.getItemBySlot(slot);
            if (target.getItemBySlot(slot).isEmpty() && !item.isEmpty()) {
                target.setItemSlot(slot, item.copy());
            }
        }
    }

    // ================= [Curios 背部槽安全序列化] =================
    public static CompoundTag getCuriosBackItemTag(HeroEntity hero) {
        if (ModList.get().isLoaded("curios")) {
            return CuriosSafeInvoker.getBackItemTag(hero);
        }
        return new CompoundTag();
    }

    public static void setCuriosBackItemFromTag(HeroEntity hero, CompoundTag tag) {
        if (ModList.get().isLoaded("curios")) {
            CuriosSafeInvoker.setBackItemFromTag(hero, tag);
        }
    }

    public static boolean isCuriosBackSlotEmpty(HeroEntity hero) {
        if (!ModList.get().isLoaded("curios")) return true;
        return CuriosSafeInvoker.isBackSlotEmpty(hero);
    }

    // 【核心防御机制】安全隔离内部类！
    public static CompoundTag getAccessoriesDataTag(HeroEntity hero) {
        if (!HeroAccessoriesCompat.isLoaded()) {
            return new CompoundTag();
        }
        return HeroAccessoriesCompat.getAccessoriesDataTag(hero);
    }

    public static void setAccessoriesDataFromTag(HeroEntity hero, CompoundTag tag) {
        if (!HeroAccessoriesCompat.isLoaded()) {
            return;
        }
        HeroAccessoriesCompat.setAccessoriesDataFromTag(hero, tag);
    }
    private static class CuriosSafeInvoker {
        static CompoundTag getBackItemTag(HeroEntity hero) {
            CompoundTag tag = new CompoundTag();
            ItemStack stack = com.whitecloud233.herobrine_companion.compat.curios.HeroCuriosCompat.getBackSlotItem(hero);
            if (!stack.isEmpty()) {
                Tag saved = stack.saveOptional(hero.registryAccess());
                if (saved instanceof CompoundTag ct) return ct;
            }
            return tag;
        }

        static void setBackItemFromTag(HeroEntity hero, CompoundTag tag) {
            com.whitecloud233.herobrine_companion.compat.curios.HeroCuriosCompat.setBackSlotItem(hero, parseItemStackOrEmpty(hero, tag));
        }

        static boolean isBackSlotEmpty(HeroEntity hero) {
            return com.whitecloud233.herobrine_companion.compat.curios.HeroCuriosCompat.getBackSlotItem(hero).isEmpty();
        }
    }
}
