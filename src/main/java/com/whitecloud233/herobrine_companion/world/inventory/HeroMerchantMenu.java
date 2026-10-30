package com.whitecloud233.herobrine_companion.world.inventory;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.util.HeroMenuValidity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;

public class HeroMerchantMenu extends MerchantMenu {
    private final Merchant trader;
    private int syncedTrustLevel;

    // 菜单数据按 16 位传输；拆成两项以保留完整的信任值，包括负数和较大的数值。
    private final ContainerData trustData = new ContainerData() {
        @Override
        public int get(int index) {
            return (getTrustLevel() >>> (index * 16)) & 0xFFFF;
        }

        @Override
        public void set(int index, int value) {
            int shift = index * 16;
            syncedTrustLevel = (syncedTrustLevel & ~(0xFFFF << shift)) | ((value & 0xFFFF) << shift);
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public HeroMerchantMenu(int containerId, Inventory playerInventory) {
        super(containerId, playerInventory);
        this.trader = null;
        this.addDataSlots(this.trustData);
    }

    public HeroMerchantMenu(int containerId, Inventory playerInventory, Merchant merchant) {
        super(containerId, playerInventory, merchant);
        this.trader = merchant;
        this.addDataSlots(this.trustData);
    }

    public int getTrustLevel() {
        return this.trader instanceof HeroEntity hero ? hero.getTrustLevel() : this.syncedTrustLevel;
    }

    @Override
    public MenuType<?> getType() {
        return ModMenus.HERO_TRADE_MENU.get();
    }

    // 【核心修改】覆盖原版 MerchantMenu.stillValid（原版只校验
    // trader.getTradingPlayer() == player，交易状态稍有变化页面就会被服务端强制关闭）。
    // 这里改为：只要 Herobrine 还存活且在玩家所在的已加载区块区域内，交易页面就保持打开，
    // 走远一点（但仍处于同一加载区域）不会被强制关闭。
    // 客户端菜单的 trader 是原版 ClientSideMerchant，走 super 逻辑，行为不变。
    @Override
    public boolean stillValid(Player player) {
        if (this.trader instanceof HeroEntity hero) {
            return HeroMenuValidity.isHeroInPlayerLoadedArea(hero, player);
        }
        return super.stillValid(player);
    }
}
