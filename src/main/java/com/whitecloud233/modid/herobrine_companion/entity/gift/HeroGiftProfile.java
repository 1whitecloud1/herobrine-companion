package com.whitecloud233.modid.herobrine_companion.entity.gift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 英雄侧赠礼档案(纯数据结构) —— Bedrock hero_player_offer_profile.new_hero_profile
 * 的记录载体。可变字段便于编排层原地更新;持久化由 HeroGiftProfileStorage 负责
 * (序列化 ↔ CompoundTag),本类只定义形状与归一化规则。
 */
public final class HeroGiftProfile {

    // 累计与最近
    public int totalGiftCount;
    public int totalFoodCount;
    public int lastOfferTick;
    public String lastOfferItemName = "";
    public int repeatOfferCount;

    // 口味档案
    public int trustTaste;
    public int memoryTaste;
    public int warmthTaste;
    public int restraintTaste;
    public int pollutionTaste;
    public final Map<String, HeroGiftTaste.Signal> favoriteSignals = new HashMap<>();
    public final Map<String, HeroGiftTaste.Signal> tabooSignals = new HashMap<>();

    // 历史(限长 12)
    public record HistoryEntry(int tick, String itemName, String kind, String category, String result) {
    }

    public final List<HistoryEntry> acceptedHistory = new ArrayList<>();
    public final List<HistoryEntry> rejectedHistory = new ArrayList<>();

    // 最近台词
    public String lastReaction = "";
    public String lastReactionCode = "";

    // 计数
    public int emptyOfferCount;
    public int premiumOfferCount;
    public int zenithOfferCount;

    // 请求
    public record ActiveRequest(String itemName, String category, String label,
                                int createdTick, int expireTick) {
    }

    public ActiveRequest activeRequest;

    // 秘密喜好
    public final Map<String, String> secretFavorites = new HashMap<>();
    public final List<String> discoveredFavorites = new ArrayList<>();
    public final List<String> hintedFavorites = new ArrayList<>();
    public final Map<String, HeroGiftSecret.HintState> hintProgress = new HashMap<>();
    public String hintFocus = "";

    // 生日
    public String lastBirthdayKey = "";
    public int birthdayCount;
    public final List<String> birthdayYears = new ArrayList<>();

    // 待交付(回礼/夜间引路灯)
    public record PendingReturn(
            String type,                // item_return / night_hint / night_hint_active
            String itemName,
            int count,
            int aux,
            String customName,          // 可空(生日纪念物名称)
            boolean birthday,
            int readyTick,
            int expireTick,
            String hintPos,             // "x,y,z",仅 night_hint_active
            int cleanupTick) {
    }

    public PendingReturn pendingReturn;

    // ------------------------------------------------------------------
    // 归一化(载入后调用,修复非法/缺失字段)
    // ------------------------------------------------------------------

    public void normalize() {
        totalGiftCount = norm(totalGiftCount);
        totalFoodCount = norm(totalFoodCount);
        lastOfferTick = norm(lastOfferTick);
        repeatOfferCount = norm(repeatOfferCount);
        trustTaste = norm(trustTaste);
        memoryTaste = norm(memoryTaste);
        warmthTaste = norm(warmthTaste);
        restraintTaste = norm(restraintTaste);
        pollutionTaste = norm(pollutionTaste);
        emptyOfferCount = norm(emptyOfferCount);
        premiumOfferCount = norm(premiumOfferCount);
        zenithOfferCount = norm(zenithOfferCount);
        birthdayCount = norm(birthdayCount);
        trim(acceptedHistory, 12);
        trim(rejectedHistory, 12);
        favoriteSignals.entrySet().removeIf(e -> e.getValue() == null);
        tabooSignals.entrySet().removeIf(e -> e.getValue() == null);
        secretFavorites.replaceAll((k, v) -> v);
        hintFocus = HeroGiftSecret.BUCKETS.contains(hintFocus) ? hintFocus : "";
        birthdayYears.removeIf(y -> y == null || y.isBlank());
        birthdayYears.sort(String::compareTo);
        while (birthdayYears.size() > 8) {
            birthdayYears.remove(0);
        }
        if (activeRequest != null) {
            activeRequest = new ActiveRequest(
                    str(activeRequest.itemName()), str(activeRequest.category()),
                    str(activeRequest.label()), norm(activeRequest.createdTick()),
                    norm(activeRequest.expireTick()));
        }
        for (String bucket : List.copyOf(hintProgress.keySet())) {
            if (!HeroGiftSecret.BUCKETS.contains(bucket) || hintProgress.get(bucket) == null) {
                hintProgress.remove(bucket);
            }
        }
        discoveredFavorites.removeIf(b -> !HeroGiftSecret.BUCKETS.contains(b));
        hintedFavorites.removeIf(b -> !HeroGiftSecret.BUCKETS.contains(b));
        if (discoveredFavorites.size() > 4) {
            discoveredFavorites.subList(4, discoveredFavorites.size()).clear();
        }
        if (hintedFavorites.size() > 4) {
            hintedFavorites.subList(4, hintedFavorites.size()).clear();
        }
        if (pendingReturn != null && pendingReturn.expireTick() <= 0) {
            pendingReturn = null;
        }
    }

    private static int norm(int v) {
        return Math.max(0, v);
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }

    private static void trim(List<?> list, int limit) {
        while (list.size() > limit) {
            list.remove(0);
        }
    }
}