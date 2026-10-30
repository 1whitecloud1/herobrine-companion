package com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BooleanSupplier;

/** Keeps cookware effects on the cook while delivering returned items to the owner. */
final class KitchenActorInteraction {
    private KitchenActorInteraction() {
    }

    static boolean perform(HeroEntity hero, ServerPlayer owner, BooleanSupplier action) {
        boolean handWasEmpty = hero.getMainHandItem().isEmpty();
        Collection<ItemEntity> returnedItems = new ArrayList<>();
        Collection<ItemEntity> previousCapture = hero.captureDrops(returnedItems);
        try {
            return action.getAsBoolean();
        } finally {
            hero.captureDrops(previousCapture);
            // Cookery puts the first returned item into an empty hand; subsequent items
            // use spawnAtLocation. Capture both paths without touching equipped items.
            if (handWasEmpty && !hero.getMainHandItem().isEmpty()) {
                ItemStack returned = hero.getMainHandItem().copy();
                hero.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                ItemHandlerHelper.giveItemToPlayer(owner, returned);
            }
            for (ItemEntity item : returnedItems) {
                ItemHandlerHelper.giveItemToPlayer(owner, item.getItem().copy());
                item.discard();
            }
            owner.getInventory().setChanged();
        }
    }
}
