package com.whitecloud233.modid.herobrine_companion.entity.gift;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家侧赠礼档案(纯数据结构) —— Bedrock new_player_profile 的记录载体。
 *
 * <p>与英雄档案分离存放(玩家维度),供请求池过滤、共享餐、行为画像使用。
 * 持久化由 HeroGiftProfileStorage 负责。
 */
public final class HeroPlayerGiftProfile {

    public int recentCookedFoodGiven;
    public int recentRawFoodGiven;
    public int recentRottenFoodGiven;
    public int recentNamedGiftGiven;
    public int recentKillCountBeforeGift;
    public int recentVillageHarmBeforeGift;
    public int lastSharedMealTick;
    public int lastAcceptedOfferTick;
    public int lastFoodEatenTick;
    public String lastFoodEatenItemName = "";
    public final List<HeroGiftProfile.HistoryEntry> giftMemoryLedger = new ArrayList<>();

    public void normalize() {
        recentCookedFoodGiven = Math.max(0, recentCookedFoodGiven);
        recentRawFoodGiven = Math.max(0, recentRawFoodGiven);
        recentRottenFoodGiven = Math.max(0, recentRottenFoodGiven);
        recentNamedGiftGiven = Math.max(0, recentNamedGiftGiven);
        recentKillCountBeforeGift = Math.max(0, recentKillCountBeforeGift);
        recentVillageHarmBeforeGift = Math.max(0, recentVillageHarmBeforeGift);
        lastSharedMealTick = Math.max(0, lastSharedMealTick);
        lastAcceptedOfferTick = Math.max(0, lastAcceptedOfferTick);
        lastFoodEatenTick = Math.max(0, lastFoodEatenTick);
        while (giftMemoryLedger.size() > 16) {
            giftMemoryLedger.remove(0);
        }
    }
}