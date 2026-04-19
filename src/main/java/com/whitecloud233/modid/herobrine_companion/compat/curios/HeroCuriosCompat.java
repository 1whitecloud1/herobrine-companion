package com.whitecloud233.modid.herobrine_companion.compat.curios;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.compat.ArmourerWorkshop.HeroAWCompat;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.SyncHeroCosmeticsPacket;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import top.theillusivec4.curios.api.CuriosApi;

public class HeroCuriosCompat {

    public static Slot createCurioSlot(HeroEntity hero, String curioId, int index, int x, int y) {
        var optionalHandler = CuriosApi.getCuriosInventory(hero).resolve();
        if (optionalHandler.isPresent()) {
            var stacksHandler = optionalHandler.get().getCurios().get(curioId);
            if (stacksHandler != null) {
                var backend = stacksHandler.getStacks();

                if (index >= backend.getSlots()) return null;

                return new SlotItemHandler(backend, index, x, y) {
                    @Override
                    public boolean mayPlace(@NotNull ItemStack stack) {
                        // 1. 先检查是否是时装工坊的物品
                        if (HeroAWCompat.isLoaded() && HeroAWCompat.isAwItem(stack)) {
                            return true;
                        }
                        // 2. 否则走 Curios 默认的标签检查（此时会去查我们在 JSON 里定义的 #curios:back）
                        return super.mayPlace(stack);
                    }

                    // 👇【新增】：监听玩家在 UI 界面中拿放 Curios 饰品
                    @Override
                    public void setChanged() {
                        super.setChanged();
                        hero.isStateDirty = true; // 触发脏标记！
                        if (!hero.level().isClientSide) {
                            PacketHandler.sendToTracking(new SyncHeroCosmeticsPacket(hero), hero);
                        }
                    }
                };
            }
        }
        return null;
    }

    public static ItemStack getBackSlotItem(HeroEntity hero) {
        var optionalHandler = CuriosApi.getCuriosInventory(hero).resolve();
        if (optionalHandler.isPresent()) {
            var stacksHandler = optionalHandler.get().getCurios().get("back");
            if (stacksHandler != null) {
                return stacksHandler.getStacks().getStackInSlot(0);
            }
        }
        return ItemStack.EMPTY;
    }

    public static void setBackSlotItem(HeroEntity hero, ItemStack stack) {
        var optionalHandler = CuriosApi.getCuriosInventory(hero).resolve();
        if (optionalHandler.isPresent()) {
            var stacksHandler = optionalHandler.get().getCurios().get("back");
            if (stacksHandler != null) {
                stacksHandler.getStacks().setStackInSlot(0, stack);
                // 👇【新增】：代码强行修改饰品时也触发脏标记
                hero.isStateDirty = true;
                if (!hero.level().isClientSide) {
                    PacketHandler.sendToTracking(new SyncHeroCosmeticsPacket(hero), hero);
                }
            }
        }
    }
}