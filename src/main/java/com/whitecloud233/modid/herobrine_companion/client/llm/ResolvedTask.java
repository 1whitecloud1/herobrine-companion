package com.whitecloud233.modid.herobrine_companion.client.llm;

/**
 * 一次任务解析出的调用设置：primary（主）+ fallback（可选备用）。
 * fallback 为 null 或不可用时表示无备用。
 */
public record ResolvedTask(LlmSettings primary, LlmSettings fallback) {
    public ResolvedTask {
        if (primary == null) {
            primary = fallback;
        }
    }

    public boolean hasFallback() {
        return fallback != null && fallback.isUsable();
    }
}
