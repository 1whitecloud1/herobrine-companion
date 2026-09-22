package com.whitecloud233.herobrine_companion.entity.gift;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 赠礼台词池 —— Bedrock hero_player_offer_lines.py 的完整移植(80 池 / 568 条)。
 *
 * <p>单一职责:池选择(等价 python `_pool_key`)、键生成(`_translate_key`)、
 * 玩家级"不连发同一句"抽取(等价 `_pick_index` + `_last_pick`)。文案本体全部在语言文件
 * `message.herobrine_companion.offer_&lt;pool&gt;_&lt;n&gt;`(zh_cn / en_us),
 * 由 gen-offer-lines.ps1 从 Bedrock `texts/zh_CN.lang` 同步。
 *
 * <p>池优先级(与 Bedrock 完全一致):
 * <ol>
 *   <li><b>mood</b> 池(mood_grumpy / mood_delighted)覆盖一切;</li>
 *   <li>类别池 `code_category`(Bedrock 写作 `code@category`);</li>
 *   <li>口味池 `code_taste_favorite` / `code_taste_wary`;</li>
 *   <li>基础池 `code`;</li>
 *   <li>兜底池 accepted。</li>
 * </ol>
 */
public final class HeroGiftLines {

    private HeroGiftLines() {
    }

    public static final String KEY_PREFIX = "message.herobrine_companion.offer_";
    private static final String FALLBACK_POOL = "accepted";
    private static final String TASTE_FAVORITE = "taste_favorite";
    private static final String TASTE_WARY = "taste_wary";

    /** (player, pool) → 上一次抽到的下标(等价 Bedrock `_last_pick`)。 */
    private static final Map<UUID, Map<String, Integer>> LAST_PICK = new ConcurrentHashMap<>();

    /** 一条台词:翻译键 + 服务端解析文本 + 池信息(便于调试/记录)。 */
    public record Line(String key, String text, String poolKey, int variantIndex) {
    }

    // ------------------------------------------------------------------
    // 池选择
    // ------------------------------------------------------------------

    /** 口味等级 → 口味池标签;中性返回 null(等价 python `_taste_tag`)。 */
    public static String tasteTag(int tasteLevel) {
        if (tasteLevel >= 3) {
            return TASTE_FAVORITE;
        }
        if (tasteLevel <= -2) {
            return TASTE_WARY;
        }
        return null;
    }

    /** 结果码 + 上下文 → 池键(Bedrock 的 `@` 在此写作 `_`,与语言键一致)。 */
    public static String poolKey(String code, String category, int tasteLevel, String mood) {
        String base = code == null ? "" : code;
        if (mood != null && !mood.isBlank()) {
            String moodPool = "mood_" + mood;
            if (HeroGiftLinePools.POOL_SIZES.containsKey(moodPool)) {
                return moodPool;
            }
        }
        if (category != null && !category.isBlank() && !base.isBlank()) {
            String scoped = base + "_" + category;
            if (HeroGiftLinePools.POOL_SIZES.containsKey(scoped)) {
                return scoped;
            }
        }
        String tag = tasteTag(tasteLevel);
        if (tag != null && !base.isBlank()) {
            String scoped = base + "_" + tag;
            if (HeroGiftLinePools.POOL_SIZES.containsKey(scoped)) {
                return scoped;
            }
        }
        if (HeroGiftLinePools.POOL_SIZES.containsKey(base)) {
            return base;
        }
        return FALLBACK_POOL;
    }

    /** 池键 + 序号(1 起)→ 翻译键(等价 python `_translate_key`)。 */
    public static String translateKey(String poolKey, int variantIndex) {
        return KEY_PREFIX + poolKey + "_" + variantIndex;
    }

    public static int poolSize(String poolKey) {
        return HeroGiftLinePools.POOL_SIZES.getOrDefault(poolKey, 1);
    }

    // ------------------------------------------------------------------
    // 抽取
    // ------------------------------------------------------------------

    /** 按上下文抽一条台词(无玩家上下文时不做不连发记忆)。 */
    public static Line pick(String code, String category, int tasteLevel, String mood,
                            RandomSource random, UUID playerUuid) {
        String pool = poolKey(code, category, tasteLevel, mood);
        int size = poolSize(pool);
        int index = pickIndex(pool, size, random, playerUuid);
        String key = translateKey(pool, index + 1);
        String text = Component.translatable(key).getString();
        if (text == null || text.isBlank() || text.equals(key)) {
            text = fallbackText(pool);
        }
        return new Line(key, text, pool, index + 1);
    }

    /** 抽取下标,避免与上一次同池重复(等价 python `_pick_index`)。 */
    private static int pickIndex(String pool, int size, RandomSource random, UUID playerUuid) {
        if (size <= 1) {
            return 0;
        }
        if (playerUuid == null) {
            return random.nextInt(size);
        }
        Map<String, Integer> memory = LAST_PICK.computeIfAbsent(playerUuid, k -> new HashMap<>());
        int index = random.nextInt(size);
        Integer last = memory.get(pool);
        if (last != null && last == index) {
            index = (index + 1 + random.nextInt(size - 1)) % size;
        }
        memory.put(pool, index);
        return index;
    }

    /** 玩家离线时清理不连发记忆(等价 Bedrock `release_player`)。 */
    public static void releasePlayer(UUID playerUuid) {
        if (playerUuid != null) {
            LAST_PICK.remove(playerUuid);
        }
    }

    private static String fallbackText(String pool) {
        return switch (pool) {
            case "judged" -> "收回去。我不会替你的贪婪保存证据。";
            case "rejected_suspicious", "rejected_taboo", "rejected_repeat" -> "这东西……你还记得它是从哪来的吗？";
            case "remembered" -> "我会记住它。";
            default -> "……";
        };
    }
}