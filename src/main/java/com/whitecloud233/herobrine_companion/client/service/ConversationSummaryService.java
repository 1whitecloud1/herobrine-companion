package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.herobrine_companion.client.llm.LlmFormatAdapter;
import com.whitecloud233.herobrine_companion.client.llm.LlmFormats;
import com.whitecloud233.herobrine_companion.client.llm.LlmSettings;
import com.whitecloud233.herobrine_companion.client.llm.LlmTask;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 普通聊天的滚动摘要服务。
 *
 * <p>把"每次重发最近 N 条原文"升级为"摘要 + 近期明细":在每次对话落盘后按阈值
 * <b>非阻塞</b>地调用一次 LLM,把历史压缩成 <=4 行要点存进
 * {@link ConversationStore}(见 {@code ConversationThread.summary})。</p>
 *
 * <p>设计纪律:</p>
 * <ul>
 *   <li><b>不阻塞主回复</b>:独立 {@link HttpClient} + 非流式 + 失败静默,纯 fire-and-forget。</li>
 *   <li><b>增量</b>:输入 = 上次摘要 + 自上次摘要以来的新消息(由
 *       {@code summaryMessageCount} 定位),每次生成都便宜。</li>
 *   <li><b>不失控</b>:开关 + API 可用性 + in-flight 去重 + 阈值门槛都不过不放行。</li>
 *   <li><b>API Key 不动</b>:LLM 调用仍在客户端(与 {@code AIService} 一致)。</li>
 * </ul>
 */
public final class ConversationSummaryService {

    /** 会话至少这么多条消息才值得生成摘要。 */
    public static final int MIN_MESSAGES = 12;

    /** 距上次摘要新增达到该条数才再次生成(滚动节奏)。 */
    public static final int SUMMARY_INTERVAL = 8;

    /** 单次生成最多喂给模型的新消息条数(防超大切片)。 */
    public static final int INPUT_CAP = 40;

    private static final int MAX_OUTPUT_TOKENS = 320;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .proxy(SystemProxy.SELECTOR)
            .build();

    /** 正在生成摘要的 scope(去重,避免并发触发同会话多次调用)。 */
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private ConversationSummaryService() {
    }

    /**
     * 落盘汇点回调:判断当前会话是否该触发一次滚动摘要,是则异步生成。
     * 由 {@code AIService.addExchangeToConversation} 在每次正常轮完成后调用。
     */
    public static void maybeTrigger(UUID scopeId) {
        if (scopeId == null) {
            return;
        }
        if (!LLMConfig.isConversationSummaryEnabled()) {
            return;
        }
        if (LLMConfig.isSetupIncomplete() || LLMConfig.isKeyMissingOrInvalid()) {
            return;
        }
        if (!IN_FLIGHT.add(scopeId)) {
            return; // 已有一份在途,避免并发重复调用
        }

        ConversationStore store = ConversationStore.getInstance();
        int count = store.getActiveConversationMessageCount(scopeId);
        int covered = store.getActiveConversationSummaryMessageCount(scopeId);
        if (count < MIN_MESSAGES || count - covered < SUMMARY_INTERVAL) {
            IN_FLIGHT.remove(scopeId);
            return;
        }

        generate(scopeId)
                .whenComplete((ignored, error) -> IN_FLIGHT.remove(scopeId));
    }

    /** 生成一份滚动摘要并写回存储。任何失败都静默(下次触发重试)。 */
    private static java.util.concurrent.CompletableFuture<Void> generate(UUID scopeId) {
        ConversationStore store = ConversationStore.getInstance();
        List<ConversationStore.ConversationMessageSnapshot> history = store.getActiveConversationMessages(scopeId);
        if (history.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        int covered = Math.min(store.getActiveConversationSummaryMessageCount(scopeId), history.size());
        String prevSummary = store.getActiveConversationSummary(scopeId);

        List<String> lines = new ArrayList<>();
        for (int i = Math.max(covered, history.size() - INPUT_CAP); i < history.size(); i++) {
            ConversationStore.ConversationMessageSnapshot msg = history.get(i);
            String role = "user".equalsIgnoreCase(msg.role()) ? "玩家" : "Herobrine";
            String content = msg.content();
            if (content == null || content.isBlank()) {
                continue;
            }
            lines.add(role + ": " + content.replace('\n', ' ').replace('\r', ' ').trim());
        }
        String transcript = String.join("\n", lines);
        if (transcript.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        String userPrompt = buildUserPrompt(prevSummary, transcript);
        String systemPrompt = "你是 Herobrine,一个世界背后的旁观者。请把一段会话压缩成简短要点,供后续对话回忆。"
                + "只保留:关键事实、玩家偏好、承诺/答应过的事、进行中的任务/计划、重大事件(战斗/破坏/修复等)。"
                + "去掉纯寒暄、客套和重复话题。用与该会话一致的语言输出,第一人称,最多 4 行纯文本,不要用 Markdown,不要复述对话原句。";

        LlmSettings settings = LLMConfig.resolveTaskSettings(LlmTask.SUMMARY).primary();
        if (!settings.isUsable()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        LlmFormatAdapter adapter = LlmFormats.forFormat(settings.format());
        HttpRequest request = adapter.buildSimpleRequest(settings, systemPrompt, userPrompt,
                0.3D, LLMConfig.getConfiguredTopP(), MAX_OUTPUT_TOKENS);

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                        return null;
                    }
                    JsonObject json;
                    try {
                        json = JsonParser.parseString(response.body()).getAsJsonObject();
                    } catch (Exception e) {
                        return null;
                    }
                    String summary = adapter.extractText(json, "");
                    if (summary == null || summary.isBlank()) {
                        return null;
                    }
                    store.setActiveConversationSummary(scopeId, summary);
                    return null;
                });
    }

    private static String buildUserPrompt(String prevSummary, String transcript) {
        String head = prevSummary == null || prevSummary.isBlank()
                ? "以下是本次会话的对话记录,请压缩成要点:\n\n"
                : "这是此前的会话摘要:\n" + prevSummary + "\n\n以下是自上次摘要以来新发生的对话:\n\n";
        return head + transcript;
    }
}
