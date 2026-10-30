package com.whitecloud233.modid.herobrine_companion.compat.accessories;

import com.mojang.datafixers.util.Pair;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroStateManager;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.SyncHeroCosmeticsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.EntityArmorInvWrapper;
import net.minecraftforge.items.wrapper.EntityHandsInvWrapper;

/** Binds the native Accessories menu's equipment to its Herobrine target on both sides. */
public final class HeroAccessoriesMenuCompat {

    private HeroAccessoriesMenuCompat() {
    }

    public static void bindEquipmentSlots(AbstractContainerMenu menu, Inventory playerInventory, HeroEntity hero) {
        var armor = new EntityArmorInvWrapper(hero);
        var hands = new EntityHandsInvWrapper(hero);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot original = menu.slots.get(i);
            int inventoryIndex = original.getContainerSlot();
            // Keep the player's storage and hotbar; only replace armor (36..39) and offhand (40).
            if (original.container != playerInventory || inventoryIndex < 36 || inventoryIndex > 40) {
                continue;
            }
            EquipmentSlot equipmentSlot = inventoryIndex == 40 ? EquipmentSlot.OFFHAND
                    : EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, inventoryIndex - 36);
            IItemHandler handler = equipmentSlot.getType() == EquipmentSlot.Type.ARMOR ? armor : hands;
            Slot replacement = new HeroEquipmentSlot(hero, equipmentSlot, handler, original);
            replacement.index = original.index;
            menu.slots.set(i, replacement);
        }
    }

    private static final class HeroEquipmentSlot extends SlotItemHandler {
        private final HeroEntity hero;
        private final EquipmentSlot equipmentSlot;
        private final Pair<ResourceLocation, ResourceLocation> emptyIcon;

        private HeroEquipmentSlot(HeroEntity hero, EquipmentSlot equipmentSlot, IItemHandler handler, Slot original) {
            super(handler, equipmentSlot.getIndex(), original.x, original.y);
            this.hero = hero;
            this.equipmentSlot = equipmentSlot;
            this.emptyIcon = original.getNoItemIcon();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.equipmentSlot == EquipmentSlot.OFFHAND
                    ? super.mayPlace(stack) : stack.canEquip(this.equipmentSlot, this.hero);
        }

        @Override
        public boolean mayPickup(Player player) {
            return (this.equipmentSlot == EquipmentSlot.OFFHAND || player.isCreative()
                    || !EnchantmentHelper.hasBindingCurse(this.getItem())) && super.mayPickup(player);
        }

        @Override
        public int getMaxStackSize() {
            return this.equipmentSlot == EquipmentSlot.OFFHAND ? 64 : 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            // SlotItemHandler's default probes capacity by temporarily clearing the equipment.
            return Math.min(this.getMaxStackSize(), stack.getMaxStackSize());
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return this.emptyIcon;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            this.hero.markEquipmentDirty();
            if (!this.hero.level().isClientSide) {
                HeroStateManager.backupToGlobal(this.hero);
                PacketHandler.sendToTracking(new SyncHeroCosmeticsPacket(this.hero), this.hero);
            }
        }
    }
}
