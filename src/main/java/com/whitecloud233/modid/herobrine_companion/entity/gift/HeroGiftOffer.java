package com.whitecloud233.modid.herobrine_companion.entity.gift;

/**
 * 一次赠礼的完整输入快照(纯数据载体)。
 *
 * <p>由 HeroOfferService 从目录分类、行为上下文、口味/请求/秘密状态组装;
 * HeroGiftEvaluator 只读它并产出 {@link HeroGiftResult}。字段语义与 Bedrock
 * hero_player_offer_service._build_offer / hero_player_offer_evaluator 完全一致。
 */
public record HeroGiftOffer(
        String itemName,
        String kind,          // gift / food / empty
        String category,
        int count,
        int auxValue,
        boolean isNamed,
        boolean isFood,
        boolean playerHungerLow,
        boolean nearFire,
        boolean isNight,
        int repeatCount,
        int emptyOfferCount,
        int premiumCount,
        int zenithCount,
        int tasteLevel,
        // 行为上下文(2400 tick 窗口内事件数)
        int recentViolence,
        int recentCare,
        int recentVillageHarm,
        // 分类基础分
        int memory,
        int warmth,
        int restraint,
        int rift,
        int pollution,
        int suspicion,
        int valueTier,
        boolean requestAsk,
        boolean requestMatch,
        boolean secretHit
) {

    /** 空手递交(用于 empty 流程)。 */
    public static HeroGiftOffer empty(int emptyOfferCount) {
        return new HeroGiftOffer("", "empty", "empty", 0, 0, false, false, false,
                false, false, 1, emptyOfferCount, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                false, false, false);
    }
}