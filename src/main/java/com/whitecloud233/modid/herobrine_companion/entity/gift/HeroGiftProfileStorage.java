package com.whitecloud233.modid.herobrine_companion.entity.gift;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;

/**
 * 赠礼档案持久化 —— 纯序列化/反序列化 + 玩家维度存取挂点。
 *
 * <p>单一职责:CompoundTag ↔ {@link HeroGiftProfile}/{@link HeroPlayerGiftProfile} 的
 * 双向转换,以及从 HeroWorldData.PlayerProfile.giftProfile 读写的静态入口。
 * 英雄侧档案由 HeroDataHandler.saveGiftProfile/loadGiftProfile 在实体存档里承载。
 */
public final class HeroGiftProfileStorage {

    private HeroGiftProfileStorage() {
    }

    // ------------------------------------------------------------------
    // Hero 侧档案序列化
    // ------------------------------------------------------------------

    public static CompoundTag saveHeroProfile(HeroGiftProfile p) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("TotalGifts", p.totalGiftCount);
        tag.putInt("TotalFood", p.totalFoodCount);
        tag.putInt("LastOfferTick", p.lastOfferTick);
        tag.putString("LastOfferItem", p.lastOfferItemName);
        tag.putInt("RepeatOffers", p.repeatOfferCount);
        tag.putInt("TrustTaste", p.trustTaste);
        tag.putInt("MemoryTaste", p.memoryTaste);
        tag.putInt("WarmthTaste", p.warmthTaste);
        tag.putInt("RestraintTaste", p.restraintTaste);
        tag.putInt("PollutionTaste", p.pollutionTaste);
        tag.putString("LastReaction", p.lastReaction);
        tag.putString("LastReactionCode", p.lastReactionCode);
        tag.putInt("EmptyOffers", p.emptyOfferCount);
        tag.putInt("PremiumOffers", p.premiumOfferCount);
        tag.putInt("ZenithOffers", p.zenithOfferCount);
        tag.putString("LastBirthdayKey", p.lastBirthdayKey);
        tag.putInt("BirthdayCount", p.birthdayCount);
        tag.putString("HintFocus", p.hintFocus);
        tag.put("FavoriteSignals", saveSignals(p.favoriteSignals));
        tag.put("TabooSignals", saveSignals(p.tabooSignals));
        tag.put("AcceptedHistory", saveHistory(p.acceptedHistory));
        tag.put("RejectedHistory", saveHistory(p.rejectedHistory));
        tag.put("SecretFavorites", saveStringMap(p.secretFavorites));
        tag.put("DiscoveredFavorites", saveStringList(p.discoveredFavorites));
        tag.put("HintedFavorites", saveStringList(p.hintedFavorites));
        tag.put("HintProgress", saveHintProgress(p.hintProgress));
        tag.put("BirthdayYears", saveStringList(p.birthdayYears));
        if (p.activeRequest != null) {
            CompoundTag r = new CompoundTag();
            r.putString("Item", p.activeRequest.itemName());
            r.putString("Category", p.activeRequest.category());
            r.putString("Label", p.activeRequest.label());
            r.putInt("Created", p.activeRequest.createdTick());
            r.putInt("Expire", p.activeRequest.expireTick());
            tag.put("ActiveRequest", r);
        }
        if (p.pendingReturn != null) {
            CompoundTag pr = new CompoundTag();
            pr.putString("Type", p.pendingReturn.type());
            pr.putString("Item", p.pendingReturn.itemName());
            pr.putInt("Count", p.pendingReturn.count());
            pr.putInt("Aux", p.pendingReturn.aux());
            if (p.pendingReturn.customName() != null) {
                pr.putString("CustomName", p.pendingReturn.customName());
            }
            pr.putBoolean("Birthday", p.pendingReturn.birthday());
            pr.putInt("ReadyTick", p.pendingReturn.readyTick());
            pr.putInt("ExpireTick", p.pendingReturn.expireTick());
            if (p.pendingReturn.hintPos() != null) {
                pr.putString("HintPos", p.pendingReturn.hintPos());
            }
            pr.putInt("CleanupTick", p.pendingReturn.cleanupTick());
            tag.put("PendingReturn", pr);
        }
        return tag;
    }

    public static HeroGiftProfile loadHeroProfile(CompoundTag tag) {
        HeroGiftProfile p = new HeroGiftProfile();
        if (tag == null) {
            return p;
        }
        p.totalGiftCount = tag.getInt("TotalGifts");
        p.totalFoodCount = tag.getInt("TotalFood");
        p.lastOfferTick = tag.getInt("LastOfferTick");
        p.lastOfferItemName = tag.getString("LastOfferItem");
        p.repeatOfferCount = tag.getInt("RepeatOffers");
        p.trustTaste = tag.getInt("TrustTaste");
        p.memoryTaste = tag.getInt("MemoryTaste");
        p.warmthTaste = tag.getInt("WarmthTaste");
        p.restraintTaste = tag.getInt("RestraintTaste");
        p.pollutionTaste = tag.getInt("PollutionTaste");
        p.lastReaction = tag.getString("LastReaction");
        p.lastReactionCode = tag.getString("LastReactionCode");
        p.emptyOfferCount = tag.getInt("EmptyOffers");
        p.premiumOfferCount = tag.getInt("PremiumOffers");
        p.zenithOfferCount = tag.getInt("ZenithOffers");
        p.lastBirthdayKey = tag.getString("LastBirthdayKey");
        p.birthdayCount = tag.getInt("BirthdayCount");
        p.hintFocus = tag.getString("HintFocus");
        loadSignals(tag.getCompound("FavoriteSignals"), p.favoriteSignals);
        loadSignals(tag.getCompound("TabooSignals"), p.tabooSignals);
        loadHistory(tag.getList("AcceptedHistory", Tag.TAG_STRING), p.acceptedHistory);
        loadHistory(tag.getList("RejectedHistory", Tag.TAG_STRING), p.rejectedHistory);
        loadStringMap(tag.getCompound("SecretFavorites"), p.secretFavorites);
        loadStringList(tag.getList("DiscoveredFavorites", Tag.TAG_STRING), p.discoveredFavorites);
        loadStringList(tag.getList("HintedFavorites", Tag.TAG_STRING), p.hintedFavorites);
        loadHintProgress(tag.getCompound("HintProgress"), p.hintProgress);
        loadStringList(tag.getList("BirthdayYears", Tag.TAG_STRING), p.birthdayYears);
        if (tag.contains("ActiveRequest", Tag.TAG_COMPOUND)) {
            CompoundTag r = tag.getCompound("ActiveRequest");
            p.activeRequest = new HeroGiftProfile.ActiveRequest(
                    r.getString("Item"), r.getString("Category"), r.getString("Label"),
                    r.getInt("Created"), r.getInt("Expire"));
        }
        if (tag.contains("PendingReturn", Tag.TAG_COMPOUND)) {
            CompoundTag pr = tag.getCompound("PendingReturn");
            p.pendingReturn = new HeroGiftProfile.PendingReturn(
                    pr.getString("Type"),
                    pr.getString("Item"), pr.getInt("Count"), pr.getInt("Aux"),
                    pr.contains("CustomName") ? pr.getString("CustomName") : null,
                    pr.getBoolean("Birthday"),
                    pr.getInt("ReadyTick"), pr.getInt("ExpireTick"),
                    pr.contains("HintPos") ? pr.getString("HintPos") : null,
                    pr.getInt("CleanupTick"));
        }
        p.normalize();
        return p;
    }

    // ------------------------------------------------------------------
    // 玩家侧档案序列化
    // ------------------------------------------------------------------

    public static CompoundTag savePlayerProfile(HeroPlayerGiftProfile p) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("CookedGiven", p.recentCookedFoodGiven);
        tag.putInt("RawGiven", p.recentRawFoodGiven);
        tag.putInt("RottenGiven", p.recentRottenFoodGiven);
        tag.putInt("NamedGiven", p.recentNamedGiftGiven);
        tag.putInt("KillsBeforeGift", p.recentKillCountBeforeGift);
        tag.putInt("VillageHarmBeforeGift", p.recentVillageHarmBeforeGift);
        tag.putInt("LastSharedMealTick", p.lastSharedMealTick);
        tag.putInt("LastAcceptedOfferTick", p.lastAcceptedOfferTick);
        tag.putInt("LastFoodEatenTick", p.lastFoodEatenTick);
        tag.putString("LastFoodEatenItem", p.lastFoodEatenItemName);
        tag.put("Ledger", saveHistory(p.giftMemoryLedger));
        return tag;
    }

    public static HeroPlayerGiftProfile loadPlayerProfile(CompoundTag tag) {
        HeroPlayerGiftProfile p = new HeroPlayerGiftProfile();
        if (tag == null) {
            return p;
        }
        p.recentCookedFoodGiven = tag.getInt("CookedGiven");
        p.recentRawFoodGiven = tag.getInt("RawGiven");
        p.recentRottenFoodGiven = tag.getInt("RottenGiven");
        p.recentNamedGiftGiven = tag.getInt("NamedGiven");
        p.recentKillCountBeforeGift = tag.getInt("KillsBeforeGift");
        p.recentVillageHarmBeforeGift = tag.getInt("VillageHarmBeforeGift");
        p.lastSharedMealTick = tag.getInt("LastSharedMealTick");
        p.lastAcceptedOfferTick = tag.getInt("LastAcceptedOfferTick");
        p.lastFoodEatenTick = tag.getInt("LastFoodEatenTick");
        p.lastFoodEatenItemName = tag.getString("LastFoodEatenItem");
        loadHistory(tag.getList("Ledger", Tag.TAG_STRING), p.giftMemoryLedger);
        p.normalize();
        return p;
    }

    // ------------------------------------------------------------------
    // 玩家维度存取挂点(HeroWorldData.PlayerProfile.giftProfile)
    // ------------------------------------------------------------------

    public static HeroPlayerGiftProfile loadPlayerProfile(ServerLevel level, UUID playerUuid) {
        CompoundTag tag = com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData
                .get(level).getGiftProfile(playerUuid);
        return loadPlayerProfile(tag);
    }

    public static void storePlayerProfile(ServerLevel level, UUID playerUuid, HeroPlayerGiftProfile profile) {
        com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData
                .get(level).setGiftProfile(playerUuid, savePlayerProfile(profile));
    }

    // ------------------------------------------------------------------
    // 内部转换
    // ------------------------------------------------------------------

    private static CompoundTag saveSignals(Map<String, HeroGiftTaste.Signal> signals) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, HeroGiftTaste.Signal> e : signals.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putFloat("p", e.getValue().p());
            s.putFloat("n", e.getValue().n());
            s.putInt("s", e.getValue().s());
            s.putInt("t", e.getValue().t());
            tag.put(e.getKey(), s);
        }
        return tag;
    }

    private static void loadSignals(CompoundTag tag, Map<String, HeroGiftTaste.Signal> out) {
        for (String key : tag.getAllKeys()) {
            CompoundTag s = tag.getCompound(key);
            out.put(key, new HeroGiftTaste.Signal(
                    s.getFloat("p"), s.getFloat("n"), s.getInt("s"), s.getInt("t")));
        }
    }

    private static ListTag saveHistory(java.util.List<HeroGiftProfile.HistoryEntry> history) {
        ListTag list = new ListTag();
        for (HeroGiftProfile.HistoryEntry e : history) {
            list.add(StringTag.valueOf(e.tick() + "|" + e.itemName() + "|" + e.kind()
                    + "|" + e.category() + "|" + e.result()));
        }
        return list;
    }

    private static void loadHistory(ListTag list, java.util.List<HeroGiftProfile.HistoryEntry> out) {
        for (int i = 0; i < list.size(); i++) {
            String[] parts = list.getString(i).split("\\|", -1);
            if (parts.length < 5) {
                continue;
            }
            try {
                out.add(new HeroGiftProfile.HistoryEntry(
                        Integer.parseInt(parts[0]), parts[1], parts[2], parts[3], parts[4]));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static CompoundTag saveStringMap(Map<String, String> map) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, String> e : map.entrySet()) {
            tag.putString(e.getKey(), e.getValue());
        }
        return tag;
    }

    private static void loadStringMap(CompoundTag tag, Map<String, String> out) {
        for (String key : tag.getAllKeys()) {
            out.put(key, tag.getString(key));
        }
    }

    private static ListTag saveStringList(java.util.List<String> list) {
        ListTag tag = new ListTag();
        for (String s : list) {
            tag.add(StringTag.valueOf(s));
        }
        return tag;
    }

    private static void loadStringList(ListTag list, java.util.List<String> out) {
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
    }

    private static CompoundTag saveHintProgress(Map<String, HeroGiftSecret.HintState> progress) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, HeroGiftSecret.HintState> e : progress.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.put("Bits", saveStringList(e.getValue().bits()));
            s.put("MissedSlots", saveStringList(e.getValue().missedSlots()));
            s.putInt("Streak", e.getValue().streak());
            tag.put(e.getKey(), s);
        }
        return tag;
    }

    private static void loadHintProgress(CompoundTag tag, Map<String, HeroGiftSecret.HintState> out) {
        for (String bucket : tag.getAllKeys()) {
            CompoundTag s = tag.getCompound(bucket);
            java.util.List<String> bits = new java.util.ArrayList<>();
            java.util.List<String> missed = new java.util.ArrayList<>();
            loadStringList(s.getList("Bits", Tag.TAG_STRING), bits);
            loadStringList(s.getList("MissedSlots", Tag.TAG_STRING), missed);
            out.put(bucket, new HeroGiftSecret.HintState(bits, missed, s.getInt("Streak")));
        }
    }
}