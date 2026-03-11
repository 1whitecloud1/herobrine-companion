package com.whitecloud233.herobrine_companion.world.inventory;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.world.inventory.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.wrapper.EntityArmorInvWrapper;
import net.neoforged.neoforge.items.wrapper.EntityHandsInvWrapper;

public class HeroWardrobeMenu extends AbstractContainerMenu {

    private final HeroEntity hero;

    // 1.21.1: 菜单数据传输使用了带有注册表上下文的 RegistryFriendlyByteBuf
    public HeroWardrobeMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInv, (HeroEntity) playerInv.player.level().getEntity(extraData.readInt()));
    }

    public HeroWardrobeMenu(int containerId, Inventory playerInv, HeroEntity hero) {
        super(ModMenus.HERO_WARDROBE_MENU.get(), containerId);
        this.hero = hero;

        // 1. 【核心修正】左侧退回原版护甲槽
        EntityArmorInvWrapper armorInv = new EntityArmorInvWrapper(this.hero);
        for (int i = 0; i < 4; ++i) {
            this.addSlot(new SlotItemHandler(armorInv, 3 - i, 26, 8 + i * 18) {
                @Override public int getMaxStackSize() { return 1; }
            });
        }

        // 2. 右上角：使用 Curios 挂载翅膀 (back)
        if (ModList.get().isLoaded("curios")) {
            CuriosSafeInvoker.addCurioSlot(this, this.hero);
        }

        // 3. 右下角：原版双手武器槽
        EntityHandsInvWrapper handsInv = new EntityHandsInvWrapper(this.hero);
        this.addSlot(new SlotItemHandler(handsInv, 0, 134, 26) {
            @Override public int getMaxStackSize() { return 1; }
        });
        this.addSlot(new SlotItemHandler(handsInv, 1, 134, 44) {
            @Override public int getMaxStackSize() { return 1; }
        });

        // --- 下方：玩家背包与快捷栏 ---
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    public HeroEntity getHero() { return this.hero; }

    @Override
    public boolean stillValid(Player player) {
        return this.hero != null && this.hero.isAlive() && this.hero.distanceTo(player) < 8.0F;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack sourceStack = slot.getItem();
            itemstack = sourceStack.copy();

            // 1.21.1: 更换为 NeoForge 的 ModList
            boolean hasCurios = ModList.get().isLoaded("curios");
            int heroSlotCount = hasCurios ? 7 : 6;

            int invStart = heroSlotCount;
            int hotbarStart = invStart + 27;
            int invEnd = hotbarStart + 9;

            if (index < heroSlotCount) {
                if (!this.moveItemStackTo(sourceStack, invStart, invEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                boolean movedToHero = false;

                for (int i = 0; i < heroSlotCount; i++) {
                    Slot targetSlot = this.slots.get(i);
                    if (!targetSlot.hasItem() && targetSlot.mayPlace(sourceStack)) {
                        if (this.moveItemStackTo(sourceStack, i, i + 1, false)) {
                            movedToHero = true;
                            break;
                        }
                    }
                }

                if (!movedToHero) {
                    if (index >= invStart && index < hotbarStart) {
                        if (!this.moveItemStackTo(sourceStack, hotbarStart, invEnd, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (index >= hotbarStart && index < invEnd) {
                        if (!this.moveItemStackTo(sourceStack, invStart, hotbarStart, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                }
            }

            if (sourceStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (sourceStack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, sourceStack);
        }

        return itemstack;
    }

    private static class CuriosSafeInvoker {
        static void addCurioSlot(HeroWardrobeMenu menu, HeroEntity hero) {
            Slot backSlot = com.whitecloud233.herobrine_companion.compat.curios.HeroCuriosCompat.createCurioSlot(hero, "back", 0, 134, 8);
            if (backSlot != null) {
                menu.addSlot(backSlot);
            }
        }
    }
}