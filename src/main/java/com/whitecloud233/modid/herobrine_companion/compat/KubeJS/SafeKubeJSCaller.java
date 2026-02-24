package com.whitecloud233.modid.herobrine_companion.compat.KubeJS;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.compat.KubeJS.HerobrineCompanionKubeJSPlugin;
import net.minecraft.world.item.trading.MerchantOffers;

public class SafeKubeJSCaller {
    // 隔离的方法
    public static void fireEvent(MerchantOffers offers, HeroEntity hero) {
        HerobrineCompanionKubeJSPlugin.fireTradeEvent(offers, hero);
    }
}
