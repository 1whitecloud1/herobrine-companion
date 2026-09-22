package com.whitecloud233.herobrine_companion.entity.gift;

import java.util.HashMap;
import java.util.Map;

/**
 * 多阶段口味学习 —— Bedrock hero_player_offer_taste.py 的纯逻辑移植。
 *
 * <p>单一职责:口味信号数学。每物品状态 {'p','n','s','t'} 存于档案的
 * favoriteSignals / tabooSignals 映射;本类不感知持久化,由 HeroGiftProfile 存取,
 * 由 HeroOfferService 在每次递交后调用 {@link #updateMemory} 采样。
 */
public final class HeroGiftTaste {

    private HeroGiftTaste() {
    }

    /** 半衰期窗口(tick)。 */
    public static final int DECAY_WINDOW_TICKS = 12000;
    public static final float POS_CAP = 12.0F;
    public static final float NEG_CAP = 12.0F;
    public static final float MIN_EVIDENCE = 0.8F;

    /** 单物品信号状态。 */
    public record Signal(float p, float n, int s, int t) {
        public static Signal neutral() {
            return new Signal(0.0F, 0.0F, 0, 0);
        }
    }

    private static final Map<Integer, String> STAGES = new HashMap<>();

    static {
        STAGES.put(-4, "abhorred");
        STAGES.put(-3, "taboo");
        STAGES.put(-2, "wary");
        STAGES.put(-1, "cool");
        STAGES.put(0, "plain");
        STAGES.put(1, "noticed");
        STAGES.put(2, "familiar");
        STAGES.put(3, "trusted");
        STAGES.put(4, "kindred");
    }

    /** 兼容旧档:整数计数 → 信号(遗留 plain 计数迁移)。 */
    public static Signal normalizeSignal(Object raw) {
        if (raw instanceof Signal signal) {
            return signal;
        }
        if (raw instanceof Map<?, ?> map) {
            float p = toFloat(map.get("p"));
            float n = toFloat(map.get("n"));
            int s = toInt(map.get("s"));
            int t = toInt(map.get("t"));
            return new Signal(p, n, s, t);
        }
        int count = Math.max(0, toInt(raw));
        return new Signal(count, 0.0F, count, 0);
    }

    static float decay(float value, int lastTick, int tick) {
        if (value <= 0.0F || lastTick <= 0) {
            return value;
        }
        int steps = Math.max(0, (tick - lastTick) / DECAY_WINDOW_TICKS);
        if (steps <= 0) {
            return value;
        }
        return (float) (value * Math.pow(0.5, steps));
    }

    static int levelFrom(float p, float n) {
        float total = p + n;
        if (total < MIN_EVIDENCE) {
            return 0;
        }
        float diff = p - n;
        if (diff >= 6.0F) {
            return 4;
        }
        if (diff >= 3.0F) {
            return 3;
        }
        if (diff >= 1.5F) {
            return 2;
        }
        if (diff >= 0.6F) {
            return 1;
        }
        if (diff <= -6.0F) {
            return -4;
        }
        if (diff <= -3.0F) {
            return -3;
        }
        if (diff <= -1.5F) {
            return -2;
        }
        if (diff <= -0.6F) {
            return -1;
        }
        return 0;
    }

    /** 当前口味等级(-4..+4),应用半衰衰减。 */
    public static int levelOf(Map<String, Signal> favorites, Map<String, Signal> taboos,
                              String itemName, int tick) {
        if (itemName == null || itemName.isEmpty()) {
            return 0;
        }
        Signal fav = normalizeSignal(favorites == null ? null : favorites.get(itemName));
        Signal tab = normalizeSignal(taboos == null ? null : taboos.get(itemName));
        float p = decay(fav.p(), fav.t(), tick);
        float n = decay(tab.n(), tab.t(), tick);
        return levelFrom(p, n);
    }

    public static String stageName(int level) {
        return STAGES.getOrDefault(level, "plain");
    }

    /** 一次判定 → 正负证据权重(Bedrock _sample_weights)。 */
    static float[] sampleWeights(HeroGiftOffer offer, HeroGiftResult result) {
        float p = 0.0F;
        float n = 0.0F;
        if (result.accepted()) {
            p = 0.7F;
            if (offer.recentCare() > 0 || "repair".equals(offer.category())) {
                p += 0.4F;
            }
            if (offer.isFood() && offer.playerHungerLow()) {
                p += 0.4F;
            }
            if (offer.isFood() && offer.nearFire() && offer.isNight()) {
                p += 0.3F;
            }
            HeroGiftResult.Score score = result.score();
            if (score.memory() >= 6 || score.rift() >= 8) {
                p += 0.2F;
            }
            if (score.pollution() >= 10) {
                n += 0.8F;
            }
            if (offer.secretHit()) {
                // 心照时刻:额外证据
                p += 0.5F;
            }
        } else {
            n = 1.0F;
            String code = result.code();
            if ("judged".equals(code)) {
                n += 1.0F;
            } else if ("rejected_suspicious".equals(code)) {
                n += 0.5F;
            } else if ("rejected_taboo".equals(code)) {
                n += 0.6F;
            }
            if (offer.recentVillageHarm() > 0) {
                n += 0.3F;
            }
        }
        if (offer.repeatCount() >= 3) {
            n += 0.3F;
        }
        return new float[]{p, n};
    }

    /** 记录一次判定为口味样本(原地修改两张信号表)。 */
    public static void updateMemory(Map<String, Signal> favoriteSignals,
                                    Map<String, Signal> tabooSignals,
                                    HeroGiftOffer offer, HeroGiftResult result, int tick) {
        String itemName = offer.itemName();
        if ("empty".equals(offer.kind()) || itemName == null || itemName.isEmpty()) {
            return;
        }
        float[] weights = sampleWeights(offer, result);
        float p = weights[0];
        float n = weights[1];
        if (p <= 0.0F && n <= 0.0F) {
            return;
        }
        Signal fav = normalizeSignal(favoriteSignals.get(itemName));
        Signal tab = normalizeSignal(tabooSignals.get(itemName));
        fav = new Signal(Math.min(POS_CAP, decay(fav.p(), fav.t(), tick) + p),
                fav.n(), fav.s() + 1, tick);
        tab = new Signal(tab.p(), Math.min(NEG_CAP, decay(tab.n(), tab.t(), tick) + n),
                tab.s() + 1, tick);
        favoriteSignals.put(itemName, fav);
        tabooSignals.put(itemName, tab);
    }

    /** 口味记忆摘要(供 UI 载荷)。 */
    public record MemorySummary(int knownCount, int favoriteCount, int waryCount, int tabooCount) {
    }

    public static MemorySummary memorySummary(Map<String, Signal> favoriteSignals,
                                              Map<String, Signal> tabooSignals, int tick) {
        int known = 0;
        int favorites = 0;
        int wary = 0;
        int taboo = 0;
        java.util.Set<String> names = new java.util.HashSet<>();
        if (favoriteSignals != null) {
            names.addAll(favoriteSignals.keySet());
        }
        if (tabooSignals != null) {
            names.addAll(tabooSignals.keySet());
        }
        for (String itemName : names) {
            int level = levelOf(favoriteSignals, tabooSignals, itemName, tick);
            if (level >= 1) {
                known++;
            }
            if (level >= 3) {
                favorites++;
            }
            if (level <= -2) {
                wary++;
            }
            if (level <= -3) {
                taboo++;
            }
        }
        return new MemorySummary(known, favorites, wary, taboo);
    }

    private static float toFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (Exception e) {
            return 0.0F;
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }
}