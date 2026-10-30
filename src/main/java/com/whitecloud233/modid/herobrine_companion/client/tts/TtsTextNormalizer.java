package com.whitecloud233.modid.herobrine_companion.client.tts;

import com.whitecloud233.modid.herobrine_companion.client.service.AIReplyGuard;

import java.util.regex.Pattern;

/**
 * 把 LLM 回复洗成"能念出来"的纯文本。
 *
 * <p>聊天框里的回复可能带思考块、颜色代码、Markdown 强调符或换行；这些念出来是噪音。
 * 本类只做文本净化与截断，不关心引擎，保持单一职责。</p>
 */
public final class TtsTextNormalizer {

    private static final Pattern COLOR_CODE = Pattern.compile("§[0-9a-fk-orA-FK-OR]");
    private static final Pattern ANGLE_TAG = Pattern.compile("<[^<>]{0,64}>");
    private static final Pattern MARKDOWN_MARK = Pattern.compile("[*`~]+|#{1,6}\\s*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** 句末标点：超长时优先在这些字符处截断，避免把一句话切成两半。 */
    private static final String SENTENCE_END = "。！？；…!?;.";

    private TtsTextNormalizer() {
    }

    /**
     * 净化并截断文本；没有任何可念内容时返回空串（调用方据此跳过本句）。
     */
    public static String normalize(String rawText, int maxChars) {
        if (rawText == null) {
            return "";
        }
        int limit = maxChars <= 0 ? TtsSettings.DEFAULT_MAX_CHARS : maxChars;
        String text = AIReplyGuard.stripThinkingBlocks(rawText);
        text = COLOR_CODE.matcher(text).replaceAll("");
        text = ANGLE_TAG.matcher(text).replaceAll(" ");
        text = MARKDOWN_MARK.matcher(text).replaceAll("");
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        if (text.isEmpty()) {
            return "";
        }
        return truncate(text, limit);
    }

    /** 超过上限时在最近的句末标点处截断；找不到就硬截并补省略号。 */
    private static String truncate(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        String head = text.substring(0, limit);
        int boundary = -1;
        for (int index = head.length() - 1; index >= limit / 2; index--) {
            if (SENTENCE_END.indexOf(head.charAt(index)) >= 0) {
                boundary = index + 1;
                break;
            }
        }
        String cut = boundary > 0 ? head.substring(0, boundary) : head;
        return cut.trim();
    }
}
