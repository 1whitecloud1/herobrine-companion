package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.Optional;

public class HeroEquipment {

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
        EquipmentSlot[] armorSlots = new EquipmentSlot[]{
                EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
        };

        EquipmentSlot[] handSlots = new EquipmentSlot[]{
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        };

        if (armor != null && !armor.isEmpty()) {
            for(int i = 0; i < armor.size() && i < armorSlots.length; ++i) {
                hero.setItemSlot(armorSlots[i], parseItemStackOrEmpty(hero, armor.getCompound(i)));
            }
        }
        if (hands != null && !hands.isEmpty()) {
            for(int i = 0; i < hands.size() && i < handSlots.length; ++i) {
                hero.setItemSlot(handSlots[i], parseItemStackOrEmpty(hero, hands.getCompound(i)));
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