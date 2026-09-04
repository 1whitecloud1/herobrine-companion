package com.whitecloud233.modid.herobrine_companion.world.inventory;

import com.whitecloud233.modid.herobrine_companion.compat.accessories.HeroAccessoriesCompat;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroStateManager;
import com.whitecloud233.modid.herobrine_companion.util.HeroMenuValidity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.EntityArmorInvWrapper;
import net.minecraftforge.fml.ModList;

public class HeroWardrobeMenu extends AbstractContainerMenu {

    private static final int ACCESSORY_COLUMNS = 4;
    private static final int BASE_SCREEN_WIDTH = 176;
    private static final int ACCESSORY_SCREEN_WIDTH = 248;
    private static final int BASE_SCREEN_HEIGHT = 166;
    private static final int ACCESSORY_START_X = 154;
    private static final int ACCESSORY_START_Y = 8;

    private final HeroEntity hero;
    private final int accessorySlotCount;
    private final int curioBackSlotIndex;
    private final int mainHandSlotIndex;
    private final int offHandSlotIndex;
    private final int heroSlotCount;
    private final int inventoryOffsetY;

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

                @Override
                public void setChanged() {
                    super.setChanged();
                    markHeroEquipmentDirtyAndBackup(hero);
                }
            });
        }

        // 2. 右上角：依然使用 Curios 挂载翅膀 (back)
        int curioBackSlotIndex = ModList.get().isLoaded("curios") ? CuriosSafeInvoker.addCurioSlot(this, this.hero) : -1;
        this.curioBackSlotIndex = curioBackSlotIndex;
        this.accessorySlotCount = HeroAccessoriesCompat.addSlots(slot -> this.addSlot(slot), this.hero, ACCESSORY_START_X, ACCESSORY_START_Y, ACCESSORY_COLUMNS);
        int accessoryRows = Math.max(3, (int)Math.ceil(this.accessorySlotCount / (double)ACCESSORY_COLUMNS));
        this.inventoryOffsetY = Math.max(0, accessoryRows - 3) * 18;

        // 3. 右下角：显式主手/副手槽，避免 EntityHandsInvWrapper 在换装时产生中间态串槽
        this.addSlot(createHandSlot(this.hero, EquipmentSlot.MAINHAND, 134, 26));
        this.mainHandSlotIndex = this.slots.size() - 1;
        this.addSlot(createHandSlot(this.hero, EquipmentSlot.OFFHAND, 134, 44));
        this.offHandSlotIndex = this.slots.size() - 1;
        this.heroSlotCount = this.slots.size();

        // --- 下方：玩家背包与快捷栏 ---
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + this.inventoryOffsetY + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142 + this.inventoryOffsetY));
        }
    }

    public HeroEntity getHero() { return this.hero; }
    public int getHeroSlotCount() { return this.heroSlotCount; }
    public int getCurioBackSlotIndex() { return this.curioBackSlotIndex; }
    public int getMainHandSlotIndex() { return this.mainHandSlotIndex; }
    public int getOffHandSlotIndex() { return this.offHandSlotIndex; }
    public int getScreenWidth() {
        return this.accessorySlotCount > 0 ? ACCESSORY_SCREEN_WIDTH : BASE_SCREEN_WIDTH;
    }
    public int getScreenHeight() { return BASE_SCREEN_HEIGHT + this.inventoryOffsetY; }
    // 【核心修改】不再按 8 格距离判定菜单有效性：
    // 只要 Herobrine 还存活且在玩家所在的已加载区块区域内，衣柜页面就保持打开，
    // 走远一点（但仍处于同一加载区域）不会被强制关闭。
    @Override public boolean stillValid(Player player) { return HeroMenuValidity.isHeroInPlayerLoadedArea(this.hero, player); }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.hero != null) {
            this.hero.isStateDirty = true;
            if (!this.hero.level().isClientSide) {
                HeroStateManager.backupToGlobal(this.hero);
            }
        }
    }

    private static Slot createHandSlot(HeroEntity hero, EquipmentSlot equipmentSlot, int x, int y) {
        return new SlotItemHandler(new HeroHandItemHandler(hero, equipmentSlot), 0, x, y) {
            @Override public int getMaxStackSize() { return 1; }

            @Override
            public void setChanged() {
                super.setChanged();
                markHeroEquipmentDirtyAndBackup(hero);
            }
        };
    }

    private static void markHeroEquipmentDirtyAndBackup(HeroEntity hero) {
        if (hero == null) {
            return;
        }

        hero.isStateDirty = true;
        if (!hero.level().isClientSide) {
            HeroStateManager.backupToGlobal(hero);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack sourceStack = slot.getItem();
            itemstack = sourceStack.copy();

            // 动态获取当前创世神专属槽位的总数（装了 Curios 模组是 7 个，没装是 6 个）
            int heroSlotCount = this.heroSlotCount;

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
        static int addCurioSlot(HeroWardrobeMenu menu, HeroEntity hero) {
            Slot backSlot = com.whitecloud233.modid.herobrine_companion.compat.curios.HeroCuriosCompat.createCurioSlot(hero, "back", 0, 134, 8);
            if (backSlot != null) {
                int slotIndex = menu.slots.size();
                menu.addSlot(backSlot);
                return slotIndex;
            }
            return -1;
        }
    }

    private static class HeroHandItemHandler implements IItemHandlerModifiable {
        private final HeroEntity hero;
        private final EquipmentSlot equipmentSlot;

        private HeroHandItemHandler(HeroEntity hero, EquipmentSlot equipmentSlot) {
            this.hero = hero;
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            validateSlot(slot);
            return this.hero.getItemBySlot(this.equipmentSlot);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            validateSlot(slot);
            ItemStack normalized = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            if (!normalized.isEmpty()) {
                normalized.setCount(1);
            }
            this.hero.setItemSlot(this.equipmentSlot, normalized);
            markHeroEquipmentDirtyAndBackup(this.hero);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            validateSlot(slot);
            if (stack.isEmpty() || !this.isItemValid(slot, stack) || !this.getStackInSlot(slot).isEmpty()) {
                return stack;
            }

            ItemStack remainder = stack.copy();
            ItemStack inserted = remainder.split(1);
            if (!simulate) {
                this.setStackInSlot(slot, inserted);
            }
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            validateSlot(slot);
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }

            ItemStack existing = this.getStackInSlot(slot);
            if (existing.isEmpty()) {
                return ItemStack.EMPTY;
            }

            ItemStack extracted = existing.copy();
            extracted.setCount(Math.min(amount, extracted.getCount()));
            if (!simulate) {
                ItemStack remaining = existing.copy();
                remaining.shrink(extracted.getCount());
                this.setStackInSlot(slot, remaining);
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            validateSlot(slot);
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            validateSlot(slot);
            return true;
        }

        private static void validateSlot(int slot) {
            if (slot != 0) {
                throw new IndexOutOfBoundsException("Hero hand slot index out of range: " + slot);
            }
        }
    }
}
