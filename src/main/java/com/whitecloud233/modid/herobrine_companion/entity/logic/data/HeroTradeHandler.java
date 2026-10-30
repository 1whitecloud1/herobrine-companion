package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.event.HeroTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import javax.annotation.Nullable;

public class HeroTradeHandler {

    private MerchantOffers offers;
    private long revision = -1;

    public MerchantOffers getOffers(HeroEntity hero) {
        if (this.offers == null || !hero.level().isClientSide && revision != HeroTrades.revision()) {
            this.offers = HeroTrades.getOffers(hero);
            revision = HeroTrades.revision();
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
        MerchantOffers previous = this.offers;
        boolean preserveUses = revision == HeroTrades.revision();
        this.offers = null;
        MerchantOffers refreshed = getOffers(hero);
        if (preserveUses && previous != null) {
            for (int i = 0; i < Math.min(previous.size(), refreshed.size()); i++) {
                MerchantOffer old = previous.get(i);
                MerchantOffer next = refreshed.get(i);
                if (net.minecraft.world.item.ItemStack.matches(old.getBaseCostA(), next.getBaseCostA())
                        && net.minecraft.world.item.ItemStack.matches(old.getCostB(), next.getCostB())
                        && net.minecraft.world.item.ItemStack.matches(old.getResult(), next.getResult())) {
                    refreshed.set(i, new MerchantOffer(next.getBaseCostA(), next.getCostB(), next.getResult(),
                            old.getUses(), next.getMaxUses(), next.getXp(), next.getPriceMultiplier(), old.getDemand()));
                }
            }
        }
        if (hero.getTradingPlayer() != null) {
            hero.getTradingPlayer().sendMerchantOffers(
                    getContainerId(hero),
                    getOffers(hero),
                    0,
                    0,
                    false,
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
