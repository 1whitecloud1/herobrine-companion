package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.event.HeroTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import javax.annotation.Nullable;

public class HeroTradeHandler {

    private MerchantOffers offers;

    public MerchantOffers getOffers(HeroEntity hero) {
        if (this.offers == null) {
            this.offers = HeroTrades.getOffers(hero);
        }
        return this.offers;
    }

    public void notifyTrade(HeroEntity hero, MerchantOffer offer) {
        hero.ambientSoundTime = -hero.getAmbientSoundInterval();
        HeroTrades.onTrade(hero, offer);
    }

    public void overrideOffers(@Nullable MerchantOffers offers) {
        this.offers = offers;
    }

    public void resetOffers(HeroEntity hero) {
        this.offers = null;
        if (hero.getTradingPlayer() != null) {
            hero.getTradingPlayer().sendMerchantOffers(
                    getContainerId(hero),
                    getOffers(hero),
                    0,
                    hero.getVillagerXp(),
                    hero.showProgressBar(),
                    hero.canRestock()
            );
        }
    }

    private int getContainerId(HeroEntity hero) {
        return (hero.getTradingPlayer() != null && hero.getTradingPlayer().containerMenu != null)
                ? hero.getTradingPlayer().containerMenu.containerId
                : 0;
    }
}
