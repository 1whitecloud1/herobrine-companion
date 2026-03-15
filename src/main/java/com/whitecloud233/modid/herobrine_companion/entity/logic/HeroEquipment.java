package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

public class HeroEquipment {

    // ================= [原生装备序列化] =================
    public static ListTag getArmorItemsTag(HeroEntity hero) {
        ListTag tag = new ListTag();
        for (ItemStack stack : hero.getArmorSlots()) tag.add(stack.save(new CompoundTag()));
        return tag;
    }

    public static ListTag getHandItemsTag(HeroEntity hero) {
        ListTag tag = new ListTag();
        for (ItemStack stack : hero.getHandSlots()) tag.add(stack.save(new CompoundTag()));
        return tag;
    }

    public static void loadEquipmentFromTag(HeroEntity hero, ListTag armor, ListTag hands) {
        if (armor != null && !armor.isEmpty()) {
            for(int i = 0; i < armor.size(); ++i) {
                hero.setItemSlot(EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, i), ItemStack.of(armor.getCompound(i)));
            }
        }
        if (hands != null && !hands.isEmpty()) {
            for(int i = 0; i < hands.size(); ++i) {
                hero.setItemSlot(EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.HAND, i), ItemStack.of(hands.getCompound(i)));
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
        if (tag == null || tag.isEmpty()) return;
        if (ModList.get().isLoaded("curios")) {
            CuriosSafeInvoker.setBackItemFromTag(hero, tag);
        }
    }

    public static boolean isCuriosBackSlotEmpty(HeroEntity hero) {
        if (!ModList.get().isLoaded("curios")) return true;
        return CuriosSafeInvoker.isBackSlotEmpty(hero);
    }

    // 【核心防御机制】安全隔离内部类！
    private static class CuriosSafeInvoker {
        static CompoundTag getBackItemTag(HeroEntity hero) {
            CompoundTag tag = new CompoundTag();
            ItemStack stack = com.whitecloud233.modid.herobrine_companion.compat.curios.HeroCuriosCompat.getBackSlotItem(hero);
            if (!stack.isEmpty()) stack.save(tag);
            return tag;
        }

        static void setBackItemFromTag(HeroEntity hero, CompoundTag tag) {
            ItemStack stack = ItemStack.of(tag);
            com.whitecloud233.modid.herobrine_companion.compat.curios.HeroCuriosCompat.setBackSlotItem(hero, stack);
        }

        static boolean isBackSlotEmpty(HeroEntity hero) {
            return com.whitecloud233.modid.herobrine_companion.compat.curios.HeroCuriosCompat.getBackSlotItem(hero).isEmpty();
        }
    }
}