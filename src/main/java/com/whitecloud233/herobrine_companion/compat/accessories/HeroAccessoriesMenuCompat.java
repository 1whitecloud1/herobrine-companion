package com.whitecloud233.herobrine_companion.compat.accessories;

import com.mojang.datafixers.util.Pair;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroStateManager;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.SyncHeroCosmeticsPacket;
import io.wispforest.accessories.menu.EquipmentSlotBasedContainer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Shared-side equipment binding for Accessories' original menu when viewing Herobrine. */
public final class HeroAccessoriesMenuCompat {

    private HeroAccessoriesMenuCompat() {
    }

    public static void bindEquipmentSlots(AbstractContainerMenu menu, Inventory playerInventory, HeroEntity hero) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot original = menu.slots.get(i);
            if (original.container != playerInventory) {
                continue;
            }
            // Inventory indices 0..35 remain the player's storage and hotbar.
            EquipmentSlot equipmentSlot = switch (original.getContainerSlot()) {
                case 36 -> EquipmentSlot.FEET;
                case 37 -> EquipmentSlot.LEGS;
                case 38 -> EquipmentSlot.CHEST;
                case 39 -> EquipmentSlot.HEAD;
                case 40 -> EquipmentSlot.OFFHAND;
                default -> null;
            };
            if (equipmentSlot == null) {
                continue;
            }
            Slot replacement = createEquipmentSlot(hero, equipmentSlot, original);
            replacement.index = original.index;
            menu.slots.set(i, replacement);
        }
    }

    private static Slot createEquipmentSlot(HeroEntity hero, EquipmentSlot equipmentSlot, Slot original) {
        Container container = EquipmentSlotBasedContainer.of(equipmentSlot, hero);
        Pair<ResourceLocation, ResourceLocation> emptyIcon = original.getNoItemIcon();
        boolean offhand = equipmentSlot == EquipmentSlot.OFFHAND;
        return new Slot(container, 0, original.x, original.y) {
            @Override
            public int getMaxStackSize() {
                return offhand ? super.getMaxStackSize() : 1;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return offhand ? super.mayPlace(stack) : stack.canEquip(equipmentSlot, hero);
            }

            @Override
            public boolean mayPickup(Player player) {
                // 1.21.1 uses enchantment effects instead of the former binding-curse helper.
                return (offhand || player.isCreative()
                        || !EnchantmentHelper.has(this.getItem(), EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE))
                        && super.mayPickup(player);
            }

            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return emptyIcon;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                equipmentChanged(hero);
            }
        };
    }

    private static void equipmentChanged(HeroEntity hero) {
        hero.markEquipmentDirty();
        if (!hero.level().isClientSide) {
            HeroStateManager.backupToGlobal(hero);
            PacketHandler.sendToTracking(new SyncHeroCosmeticsPacket(hero), hero);
        }
    }
}
