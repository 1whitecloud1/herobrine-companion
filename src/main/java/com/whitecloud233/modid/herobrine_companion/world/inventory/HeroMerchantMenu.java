package com.whitecloud233.modid.herobrine_companion.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;

public class HeroMerchantMenu extends MerchantMenu {
    private final Merchant trader;

    public HeroMerchantMenu(int containerId, Inventory playerInventory) {
        super(containerId, playerInventory);
        this.trader = null;
    }

    public HeroMerchantMenu(int containerId, Inventory playerInventory, Merchant merchant) {
        super(containerId, playerInventory, merchant);
        this.trader = merchant;
    }

    @Override
    public MenuType<?> getType() {
        // [恢复] 使用你的自定义菜单类型
        return ModMenus.HERO_TRADE_MENU.get();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.trader != null) {
            this.trader.setTradingPlayer(null);
        }
    }
}