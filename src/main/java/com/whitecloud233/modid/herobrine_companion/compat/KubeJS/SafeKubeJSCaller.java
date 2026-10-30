package com.whitecloud233.modid.herobrine_companion.compat.KubeJS;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.item.trading.MerchantOffers;

public final class SafeKubeJSCaller {
    private SafeKubeJSCaller() { }
    public static void fireEvent(MerchantOffers offers, HeroEntity hero) {
        HerobrineCompanionKubeJSPlugin.fireTradeEvent(offers, hero);
    }
    public static void reloadRewards() { HerobrineCompanionKubeJSPlugin.reloadRewards(); }
}
