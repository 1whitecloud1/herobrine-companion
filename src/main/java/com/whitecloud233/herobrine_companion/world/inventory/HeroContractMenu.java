package com.whitecloud233.herobrine_companion.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
public class HeroContractMenu extends AbstractContainerMenu {
    public final Container container;

    public HeroContractMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(2));
    }

    public HeroContractMenu(int containerId, Inventory playerInventory, Container container) {
        super(ModMenus.HERO_CONTRACT_MENU.get(), containerId);
        this.container = container;
        checkContainerSize(container, 2);
        container.startOpen(playerInventory.player);

        // Input Slots (Centered)
        // Slot 0: Nether Star (Left)
        this.addSlot(new Slot(container, 0, 60, 25) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() == Items.NETHER_STAR;
            }

            @Override
            public int getMaxStackSize() {
                return 1; // Limit slot to 1 item
            }
        });

        // Slot 1: Dragon Breath (Right)
        this.addSlot(new Slot(container, 1, 100, 25) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() == Items.DRAGON_BREATH;
            }

            @Override
            public int getMaxStackSize() {
                return 1; // Limit slot to 1 item
            }
        });

        // Player Inventory (Standard position)
        // Y offset: 84 (standard) -> We might need to push it down if our custom UI is tall.
        // Let's use a standard layout where the top part is ~80 pixels tall.
        int inventoryY = 84;

        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, inventoryY + i * 18));
            }
        }

        // Player Hotbar
        int hotbarY = 142;
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(playerInventory, k, 8 + k * 18, hotbarY));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (index < 2) {
                if (!this.moveItemStackTo(itemstack1, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, 2, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.container);
        this.container.stopOpen(player);
    }
}
