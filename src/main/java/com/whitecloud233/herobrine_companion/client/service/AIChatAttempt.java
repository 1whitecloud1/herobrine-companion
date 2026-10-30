package com.whitecloud233.herobrine_companion.client.service;

import com.whitecloud233.herobrine_companion.client.llm.LlmSettings;
import com.whitecloud233.herobrine_companion.client.llm.ResolvedTask;

import java.util.Objects;

/** 单轮可变选择用新实例表达，重试不能偷偷扩大工具权限或改变会话身份。 */
public record AIChatAttempt(AIRequestContext context, ResolvedTask route, String prompt,
                            int retryCount, int variationRetryCount, boolean streaming, ToolPolicy tools) {
    public static final int MAX_RETRIES = 2;
    public static final int MAX_VARIATIONS = 1;

    public enum ToolPolicy {
        NONE,
        WORLD,
        WORLD_WITHOUT_LOOKUP;

        public boolean allowWorldActions() { return this != NONE; }
        public boolean allowLookup() { return this == WORLD; }
    }

    public AIChatAttempt {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(tools, "tools");
        prompt = prompt == null ? "" : prompt;
        if (retryCount < 0 || variationRetryCount < 0) throw new IllegalArgumentException("Negative retry count");
        if (context.crossSessionMode()) tools = ToolPolicy.NONE;
    }

    public static AIChatAttempt initial(AIRequestContext context, ResolvedTask route, String prompt,
                                         boolean streaming, ToolPolicy tools) {
        return new AIChatAttempt(context, route, prompt, 0, 0, streaming, tools);
    }

    public boolean canRetry() { return retryCount < MAX_RETRIES; }
    public boolean canVary() { return variationRetryCount < MAX_VARIATIONS; }

    public AIChatAttempt retry(String nextPrompt) {
        if (!canRetry()) throw new IllegalStateException("Retry budget exhausted");
        return new AIChatAttempt(context, route, nextPrompt, retryCount + 1, variationRetryCount, streaming, tools);
    }

    public AIChatAttempt vary(String nextPrompt) {
        if (!canVary()) throw new IllegalStateException("Variation budget exhausted");
        return new AIChatAttempt(context, route, nextPrompt, retryCount, variationRetryCount + 1, streaming, tools);
    }

    public AIChatAttempt synthesize(String nextPrompt, ToolPolicy nextTools) {
        ToolPolicy restricted = tools == ToolPolicy.NONE || nextTools == ToolPolicy.NONE ? ToolPolicy.NONE
                : tools == ToolPolicy.WORLD_WITHOUT_LOOKUP ? tools : nextTools;
        return new AIChatAttempt(context, route, nextPrompt, retryCount, variationRetryCount, streaming, restricted);
    }

    public AIChatAttempt withStreaming(boolean enabled) {
        return new AIChatAttempt(context, route, prompt, retryCount, variationRetryCount, enabled, tools);
    }

    public boolean canFallback() {
        return canRetry() && route.hasFallback();
    }

    public AIChatAttempt fallback(boolean useStreaming) {
        LlmSettings fallback = route.fallback();
        if (fallback == null || !fallback.isUsable()) throw new IllegalStateException("No usable fallback");
        return new AIChatAttempt(context, new ResolvedTask(fallback, null), prompt,
                retryCount + 1, variationRetryCount, useStreaming, tools);
    }
}
