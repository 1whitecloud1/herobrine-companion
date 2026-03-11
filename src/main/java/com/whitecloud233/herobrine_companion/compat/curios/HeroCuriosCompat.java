package com.whitecloud233.herobrine_companion.compat.curios;

import com.whitecloud233.herobrine_companion.compat.ArmourerWorkshop.HeroAWCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

public class HeroCuriosCompat {

    public static Slot createCurioSlot(HeroEntity hero, String curioId, int index, int x, int y) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(hero).orElse(null);
        if (handler != null) {
            var stacksHandler = handler.getCurios().get(curioId);
            if (stacksHandler != null) {
                // 1.21.1 NeoForge: 转型为 NeoForge 的 IItemHandlerModifiable
                IItemHandlerModifiable backend = (IItemHandlerModifiable) stacksHandler.getStacks();

                if (index >= backend.getSlots()) return null;

                // 1.21.1 NeoForge: 使用 NeoForge 的 SlotItemHandler
                return new SlotItemHandler(backend, index, x, y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        // 1. 先检查是否是时装工坊的物品
                        if (HeroAWCompat.isLoaded() && HeroAWCompat.isAwItem(stack)) {
                            return true;
                        }
                        // 2. 否则走 Curios 默认的标签检查
                        return super.mayPlace(stack);
                    }
                };
            }
        }
        return null;
    }

    public static ItemStack getBackSlotItem(HeroEntity hero) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(hero).orElse(null);
        if (handler != null) {
            var stacksHandler = handler.getCurios().get("back");
            if (stacksHandler != null) {
                return stacksHandler.getStacks().getStackInSlot(0);
            }
        }
        return ItemStack.EMPTY;
    }

    public static void setBackSlotItem(HeroEntity hero, ItemStack stack) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(hero).orElse(null);
        if (handler != null) {
            var stacksHandler = handler.getCurios().get("back");
            if (stacksHandler != null) {
                ((IItemHandlerModifiable) stacksHandler.getStacks()).setStackInSlot(0, stack);
            }
        }
    }
}