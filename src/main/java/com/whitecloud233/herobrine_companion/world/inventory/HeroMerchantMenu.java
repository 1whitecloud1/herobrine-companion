package com.whitecloud233.herobrine_companion.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;

public class HeroMerchantMenu extends MerchantMenu {
    public HeroMerchantMenu(int containerId, Inventory playerInventory) {
        super(containerId, playerInventory);
    }

    public HeroMerchantMenu(int containerId, Inventory playerInventory, Merchant merchant) {
        super(containerId, playerInventory, merchant);
    }

    @Override
    public MenuType<?> getType() {
        return ModMenus.HERO_TRADE_MENU.get();
    }
}