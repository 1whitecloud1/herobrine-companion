package com.whitecloud233.herobrine_companion.compat.kubejs;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.item.trading.MerchantOffers;

public class SafeKubeJSCaller {
    // 隔离的方法
    public static void fireEvent(MerchantOffers offers, HeroEntity hero) {
        HerobrineCompanionKubeJSPlugin.fireTradeEvent(offers, hero);
    }
}
