package com.whitecloud233.modid.herobrine_companion.world.inventory;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.world.inventory.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.EntityArmorInvWrapper;
import net.minecraftforge.items.wrapper.EntityHandsInvWrapper;
import net.minecraftforge.fml.ModList;

public class HeroWardrobeMenu extends AbstractContainerMenu {

    private final HeroEntity hero;

    public HeroWardrobeMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (HeroEntity) playerInv.player.level().getEntity(extraData.readInt()));
    }

    public HeroWardrobeMenu(int containerId, Inventory playerInv, HeroEntity hero) {
        // 将 ModMenuTypes.HERO_WARDROBE 改为 ModMenus.HERO_WARDROBE
        super(ModMenus.HERO_WARDROBE.get(), containerId);
        this.hero = hero;

        // 1. 【核心修正】左侧退回原版护甲槽！这样原版钻石甲才能正确包裹全身
        EntityArmorInvWrapper armorInv = new EntityArmorInvWrapper(this.hero);
        for (int i = 0; i < 4; ++i) {
            this.addSlot(new SlotItemHandler(armorInv, 3 - i, 26, 8 + i * 18) {
                @Override public int getMaxStackSize() { return 1; }
            });
        }

        // 2. 右上角：依然使用 Curios 挂载翅膀 (back)
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
    @Override public boolean stillValid(Player player) { return this.hero != null && this.hero.isAlive() && this.hero.distanceTo(player) < 8.0F; }
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack sourceStack = slot.getItem();
            itemstack = sourceStack.copy();

            // 动态获取当前创世神专属槽位的总数（装了 Curios 模组是 7 个，没装是 6 个）
            boolean hasCurios = net.minecraftforge.fml.ModList.get().isLoaded("curios");
            int heroSlotCount = hasCurios ? 7 : 6;

            // 玩家背包区段的起始和结束索引
            int invStart = heroSlotCount;
            int hotbarStart = invStart + 27;
            int invEnd = hotbarStart + 9;

            if (index < heroSlotCount) {
                // 1. 【脱下】从创世神装备栏 (Shift点击) -> 玩家背包
                if (!this.moveItemStackTo(sourceStack, invStart, invEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 2. 【穿上】从玩家背包 (Shift点击) -> 创世神装备栏
                boolean movedToHero = false;

                // 核心逻辑：按顺序遍历创世神的所有槽位 (头->胸->腿->脚->背饰->主手->副手)
                for (int i = 0; i < heroSlotCount; i++) {
                    Slot targetSlot = this.slots.get(i);
                    // 仅当目标槽位为空，且物品种类符合该槽位要求（原版护甲和 Curios 会自动进行严格判定）时才放入
                    if (!targetSlot.hasItem() && targetSlot.mayPlace(sourceStack)) {
                        if (this.moveItemStackTo(sourceStack, i, i + 1, false)) {
                            movedToHero = true;
                            break; // 只要穿上了一件，就停止循环
                        }
                    }
                }

                // 3. 【整理背包】如果不符合任何装备条件（或者装备满了），则在玩家的主背包和快捷栏之间互传
                if (!movedToHero) {
                    if (index >= invStart && index < hotbarStart) {
                        // 玩家主背包 -> 快捷栏
                        if (!this.moveItemStackTo(sourceStack, hotbarStart, invEnd, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (index >= hotbarStart && index < invEnd) {
                        // 玩家快捷栏 -> 主背包
                        if (!this.moveItemStackTo(sourceStack, invStart, hotbarStart, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                }
            }

            // 同步物品数量状态
            if (sourceStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            // 如果转移前后数量没变，说明转移失败（例如目标槽满了）
            if (sourceStack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, sourceStack);
        }

        return itemstack;
    }
    // 【核心防御机制】安全隔离内部类
    private static class CuriosSafeInvoker {
        static void addCurioSlot(HeroWardrobeMenu menu, HeroEntity hero) {
            Slot backSlot = com.whitecloud233.modid.herobrine_companion.compat.curios.HeroCuriosCompat.createCurioSlot(hero, "back", 0, 134, 8);
            if (backSlot != null) {
                menu.addSlot(backSlot);
            }
        }
    }
}