package com.whitecloud233.modid.herobrine_companion.entity.gift;

import net.minecraft.util.RandomSource;

/**
 * 赠礼心情掷骰 —— Bedrock hero_player_offer_mood.py 的纯逻辑移植。
 *
 * <p>单一职责:只决定"一次已接受的判定"是否叠上罕见的 grumpy/delighted 情绪层。
 * 概率由评分与递交上下文塑造;冷却由 HeroOfferService 执行(播放级 24000 tick)。
 */
public final class HeroGiftMood {

    private HeroGiftMood() {
    }

    public static final float GRUMPY_BASE = 0.02F;
    public static final float GRUMPY_MAX = 0.06F;
    public static final float DELIGHT_BASE = 0.015F;
    public static final float DELIGHT_MAX = 0.06F;
    public static final int COOLDOWN_TICKS = 24000;

    /**
     * @return "grumpy" / "delighted" / null
     */
    public static String rollMood(HeroGiftResult result, HeroGiftOffer offer, RandomSource random) {
        if (result == null || !result.accepted()) {
            return null;
        }
        HeroGiftResult.Score score = result.score();
        int tasteLevel = offer.tasteLevel();

        float grumpy = GRUMPY_BASE;
        if (score.pollution() >= 10) {
            grumpy += 0.01F;
        }
        if (tasteLevel <= -2) {
            grumpy += 0.01F;
        }
        if (offer.recentVillageHarm() > 0) {
            grumpy += 0.01F;
        }
        grumpy = Math.min(GRUMPY_MAX, grumpy);

        float delight = DELIGHT_BASE;
        if (tasteLevel >= 3) {
            delight *= 2;
        }
        if (tasteLevel >= 4) {
            delight *= 3;
        }
        if (offer.recentCare() > 0) {
            delight += 0.01F;
        }
        delight = Math.min(DELIGHT_MAX, delight);

        float roll = random.nextFloat();
        if (roll < grumpy) {
            return "grumpy";
        }
        if (roll < grumpy + delight) {
            return "delighted";
        }
        return null;
    }
}