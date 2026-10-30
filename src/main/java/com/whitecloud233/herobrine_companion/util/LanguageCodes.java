package com.whitecloud233.herobrine_companion.util;

import java.util.Locale;

/**
 * 语言代码归一化。
 *
 * <p>Minecraft 的 {@code options.languageCode} 在不同平台与版本上可能是
 * {@code en_us}、{@code en-US}、{@code zh_CN} 等形式。本模组要把它同步给服务端、
 * 拼进 AI 提示词、并作为跨聊天记录的分组键，因此必须先归一化到统一形式。</p>
 *
 * <p><b>为什么单独抽出来：</b>这段逻辑原先在客户端事件、客户端提示词、三个网络包
 * 共 5 处各写了一份<b>逐字符相同</b>的实现。客户端负责归一化后发包，服务端收到包
 * 后再归一化一次——两边只要有一处改动不同步，就会出现「客户端认为是 zh_cn、
 * 服务端存成 zh-cn」的分裂状态，而且表现为 AI 用错语言这种难以定位的症状。</p>
 */
public final class LanguageCodes {

    /** 无法识别时的回退值，同时也是 {@code LanguageCodes} 的输出规范形式。 */
    public static final String DEFAULT = "en_us";

    private LanguageCodes() {
    }

    /**
     * 归一化为 {@code 小写下划线} 形式；空值与空白均回退到 {@link #DEFAULT}。
     *
     * @param rawLanguageCode 原始代码，允许为 {@code null}
     * @return 永不为 {@code null}、永不为空的安全形式
     */
    public static String normalize(String rawLanguageCode) {
        if (rawLanguageCode == null) {
            return DEFAULT;
        }
        String normalized = rawLanguageCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.isEmpty() ? DEFAULT : normalized;
    }

    /**
     * 归一化但<b>保留空值语义</b>：输入为 {@code null} 或全空白时返回空串，而不是回退到
     * {@link #DEFAULT}。
     *
     * <p>用于需要区分「未指定语言」与「指定了英文」的场景。例如 AI 回复落款要判断
     * 「玩家是否显式选了语言」，空串代表未选、需要回退到客户端当前语言；
     * 若直接使用 {@link #normalize} 会把它变成 {@code en_us}，从而永远判定为「已选英文」。</p>
     */
    public static String normalizeOptional(String rawLanguageCode) {
        if (rawLanguageCode == null) {
            return "";
        }
        return rawLanguageCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
