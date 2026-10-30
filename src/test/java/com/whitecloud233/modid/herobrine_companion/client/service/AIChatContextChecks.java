package com.whitecloud233.modid.herobrine_companion.client.service;

import com.whitecloud233.modid.herobrine_companion.client.llm.LlmSettings;
import com.whitecloud233.modid.herobrine_companion.client.llm.ResolvedTask;

import java.util.UUID;
import java.util.function.Consumer;

public final class AIChatContextChecks {
    private static int checks;

    public static void main(String[] args) {
        UUID scope = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID authority = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Consumer<String> partial = ignored -> { };
        AIRequestContext context = new AIRequestContext("original", scope, authority, "zh_cn", partial,
                AIRequestContext.ConversationPolicy.PERSISTENT, false);
        LlmSettings primary = new LlmSettings("https://primary.invalid/v1/chat/completions", "test-only", "model", null, null);
        LlmSettings secondary = new LlmSettings("https://fallback.invalid/v1/chat/completions", "test-only", "backup", null, null);
        ResolvedTask route = new ResolvedTask(primary, secondary);
        for (AIChatAttempt.ToolPolicy tools : AIChatAttempt.ToolPolicy.values()) {
            AIChatAttempt original = AIChatAttempt.initial(context, route, "prompt", true, tools);
            AIChatAttempt retry = original.retry("corrected");
            check(retry.context() == context && retry.route() == route, "Retry must preserve request identity and route");
            check(retry.prompt().equals("corrected") && retry.context().originalMessage().equals("original"), "Correction must not replace persisted user message");
            check(retry.context().partialConsumer() == partial && retry.context().authorityPlayerId().equals(authority)
                    && retry.context().conversationScopeId().equals(scope), "Callbacks and scope must not drift");
            check(retry.tools() == tools && original.retryCount() == 0 && retry.retryCount() == 1, "Retry must be immutable and preserve tools");
            check(!retry.retry("again").canRetry(), "Correction retries are bounded");
            expectFailure(() -> retry.retry("again").retry("too much"));
            AIChatAttempt varied = original.vary("different opening");
            check(!varied.canVary() && varied.retryCount() == 0 && varied.tools() == tools, "Variation budget is independent");
            expectFailure(() -> varied.vary("repeat"));
            AIChatAttempt fallback = original.fallback(false);
            check(fallback.route().primary() == secondary && fallback.route().fallback() == null, "Fallback may only be consumed once");
            check(!fallback.streaming() && fallback.tools() == tools && fallback.context() == context, "Fallback must preserve permissions and request context");
            check(!fallback.canFallback() && fallback.retryCount() == 1, "Fallback uses correction budget");
            expectFailure(() -> fallback.fallback(true));
            check(original.canFallback(), "Primary route should retain available fallback");
            check(!original.retry("one").retry("two").canFallback(), "Fallback must honor retry budget");
            AIChatAttempt synthesis = original.synthesize("tool result", AIChatAttempt.ToolPolicy.NONE);
            check(synthesis.tools() == AIChatAttempt.ToolPolicy.NONE && synthesis.retryCount() == 0, "Result synthesis must not spend retries or enable tools");
            check(synthesis.synthesize("another", AIChatAttempt.ToolPolicy.WORLD).tools() == AIChatAttempt.ToolPolicy.NONE,
                    "Read-only synthesis must not regain world tools");
            AIChatAttempt registry = original.synthesize("registry result", AIChatAttempt.ToolPolicy.WORLD_WITHOUT_LOOKUP);
            check(!registry.tools().allowLookup() && !registry.retry("fix").tools().allowLookup()
                    && !registry.vary("vary").tools().allowLookup() && !registry.fallback(false).tools().allowLookup(),
                    "Registry synthesis must not reopen lookup on retry, variation or fallback");
        }
        AIRequestContext cross = new AIRequestContext("cross", scope, authority, null, null,
                AIRequestContext.ConversationPolicy.EPHEMERAL, true);
        AIChatAttempt attempt = AIChatAttempt.initial(cross, route, "cross", true, AIChatAttempt.ToolPolicy.WORLD);
        check(attempt.tools() == AIChatAttempt.ToolPolicy.NONE, "Cross-session context cannot execute world tools");
        check(!cross.conversationPolicy().includeHistory() && !cross.conversationPolicy().persist()
                && !cross.conversationPolicy().refreshTitle(), "Ephemeral requests must not touch persistent history");
        AIRequestContext normalized = new AIRequestContext(null, null, authority, null, null,
                AIRequestContext.ConversationPolicy.PERSISTENT, false);
        check(normalized.originalMessage().isEmpty() && normalized.conversationScopeId().equals(authority), "Missing scope defaults to authority");
        normalized = new AIRequestContext("text", scope, null, null, null, AIRequestContext.ConversationPolicy.EPHEMERAL, false);
        check(normalized.authorityPlayerId().equals(scope), "Missing authority defaults to scope");
        check(context.conversationPolicy().includeHistory() && context.conversationPolicy().persist()
                && context.conversationPolicy().refreshTitle(), "Persistent chat keeps existing history policy");
        System.out.println("AIChatContextChecks: " + checks + " checks passed");
    }

    private static void expectFailure(Runnable call) {
        try { call.run(); } catch (IllegalStateException expected) { checks++; return; }
        throw new AssertionError("Expected exhausted attempt to reject transition");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
