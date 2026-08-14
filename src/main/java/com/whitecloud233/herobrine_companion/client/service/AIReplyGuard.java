package com.whitecloud233.herobrine_companion.client.service;

import com.whitecloud233.herobrine_companion.util.LegacyFormattingText;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 回复的<b>文本净化与防重复</b>：清洗 HTML/格式码、语言解析、以及基于"最近回复"的重复检测。
 *
 * <p>单一职责：决定"这句回复干不干净、跟之前像不像"。持有 {@link #RECENT_REPLIES} 这个
 * 有状态缓存（原来藏在 {@code AIService} 的静态字段里），并负责回复的本地化兜底标注
 * （如"这次没有实际改变世界"）。不发起 LLM 调用、不执行任何动作。</p>
 */
public final class AIReplyGuard {

    private static final int MAX_RECENT_REPLIES = 6;
    private static final Map<UUID, Deque<String>> RECENT_REPLIES = new ConcurrentHashMap<>();

    private AIReplyGuard() {
    }

    /** 清空某个玩家的最近回复缓存（换人/清空历史时调用）。 */
    static void clearTransientHistory(UUID playerUUID) {
        if (playerUUID == null) {
            return;
        }
        RECENT_REPLIES.remove(playerUUID);
    }

    /** 记忆一条最近回复，供后续重复检测使用。 */
    static void rememberRecentReply(UUID playerUUID, String reply) {
        if (playerUUID == null || reply == null || reply.isBlank()) {
            return;
        }

        String normalized = normalizeForRepeatCheck(reply);
        if (normalized.isEmpty()) {
            return;
        }

        RECENT_REPLIES.compute(playerUUID, (uuid, existing) -> {
            Deque<String> deque = existing == null ? new ArrayDeque<>() : existing;
            deque.addLast(normalized);
            while (deque.size() > MAX_RECENT_REPLIES) {
                deque.removeFirst();
            }
            return deque;
        });
    }

    static boolean shouldRegenerateForRepetition(UUID playerUUID, String reply) {
        if (playerUUID == null || reply == null || reply.isBlank()) {
            return false;
        }

        Deque<String> recentReplies = RECENT_REPLIES.get(playerUUID);
        if (recentReplies == null || recentReplies.isEmpty()) {
            return false;
        }

        String normalizedReply = normalizeForRepeatCheck(reply);
        if (normalizedReply.isEmpty()) {
            return false;
        }

        for (String previous : recentReplies) {
            if (isLikelyRepeatedReply(previous, normalizedReply)) {
                return true;
            }
        }
        return false;
    }

    static boolean isLikelyRepeatedReply(String previous, String current) {
        if (previous == null || current == null || previous.isEmpty() || current.isEmpty()) {
            return false;
        }
        if (previous.equals(current)) {
            return true;
        }
        if (previous.length() >= 10 && current.length() >= 10 && (previous.contains(current) || current.contains(previous))) {
            return true;
        }

        Set<String> previousTokens = tokenizeForRepeatCheck(previous);
        Set<String> currentTokens = tokenizeForRepeatCheck(current);
        if (previousTokens.isEmpty() || currentTokens.isEmpty()) {
            return false;
        }

        Set<String> intersection = new HashSet<>(previousTokens);
        intersection.retainAll(currentTokens);
        Set<String> union = new HashSet<>(previousTokens);
        union.addAll(currentTokens);
        double similarity = union.isEmpty() ? 0.0D : (double) intersection.size() / (double) union.size();
        return similarity >= 0.82D;
    }

    private static String normalizeForRepeatCheck(String text) {
        return LegacyFormattingText.stripCodes(text).toLowerCase(Locale.ROOT)
                .replaceAll("§.", "")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("[\\p{Punct}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Set<String> tokenizeForRepeatCheck(String text) {
        String normalized = normalizeForRepeatCheck(text);
        if (normalized.isEmpty()) {
            return Set.of();
        }

        Set<String> tokens = new HashSet<>();
        for (String token : normalized.split(" ")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    static String sanitizeLocalizedText(String localizedText, String fallbackText) {
        String sanitized = LegacyFormattingText.normalize((localizedText == null ? "" : localizedText)
                .replaceAll("<[^>]*>", "")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim());
        return sanitized.isEmpty() ? LegacyFormattingText.normalize(fallbackText) : sanitized;
    }

    static String sanitizeActorDialogueText(String text, String fallbackText) {
        String sanitized = sanitizeLocalizedText(text, fallbackText)
                .replaceAll("^[\"'`]+|[\"'`]+$", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (sanitized.isEmpty()) {
            return fallbackText == null || fallbackText.isBlank() ? "..." : fallbackText.trim();
        }
        return sanitized;
    }

    static String resolveOutputLanguageCode(String outputLanguageCode) {
        String normalized = normalizeLanguageCode(outputLanguageCode);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.options == null ? "en_us" : normalizeLanguageCode(mc.options.languageCode);
    }

    private static String normalizeLanguageCode(String rawLanguageCode) {
        if (rawLanguageCode == null) {
            return "";
        }
        return rawLanguageCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    static String appendNoActionNotice(String reply, String outputLanguageCode) {
        String languageCode = resolveOutputLanguageCode(outputLanguageCode);
        if (languageCode.startsWith("zh")) {
            return reply + "\n§7（这次没有实际改变世界。）";
        }
        return reply + "\n§7(No world change was actually performed.)";
    }

    static String appendNoComputerActionNotice(String reply, String outputLanguageCode) {
        String languageCode = resolveOutputLanguageCode(outputLanguageCode);
        if (languageCode.startsWith("zh")) {
            return reply + "\n§7（这次没有执行任何本机操作。）";
        }
        return reply + "\n§7(No local computer action was performed.)";
    }
}
