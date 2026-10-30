package com.whitecloud233.modid.herobrine_companion.client.llm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * 非流式一次性调用的公共编排：发请求 → 判状态码 → 解析 JSON → 归一成 {@link LlmResponse}。
 *
 * <p><b>为什么需要它</b>：这段骨架此前在四处各写了一遍
 * （{@code AIService} 的 localize / 主聊天 / actor 台词，以及 {@code ConversationSummaryService}），
 * 而各处的 HTTP 状态判断和 JSON 解析细节已经出现漂移：摘要那条路径
 * 把非 200 与空 body 合并成"返回 null"，其余三处都要区分 429/401/5xx 并做鉴权失效上报。</p>
 *
 * <p><b>职责边界（刻意很窄）</b>：这里只负责"拿到一个 {@link LlmResponse}"。
 * 失败之后是重试、降级成原文本、还是静默丢弃，由调用方决定 —— 这些策略本就不同，
 * 强行统一会改变行为。同样，{@code AIDebugLog} 的记录字段（reasoning/tool/原始 body）
 * 各调用点取舍不同，也留在调用方。</p>
 */
public final class LlmOneShot {

    private LlmOneShot() {
    }

    /** 保留 HTTP 元数据，避免格式归一化后丢失调试所需的原始响应。 */
    public record Exchange(int statusCode, String rawBody, LlmResponse result) {
    }

    public static Exchange parseExchange(int statusCode, String body, LlmFormatAdapter adapter) {
        return new Exchange(statusCode, body, parse(statusCode, body, adapter));
    }

    /**
     * 发送非流式请求并解析响应。
     *
     * <p>永不抛出：网络异常、JSON 解析失败都归一成 {@link LlmResponse#httpFailure} 或
     * {@link LlmFailure.Kind#INVALID_RESPONSE}，调用方按 {@code successful()} 分支即可。</p>
     *
     * @param client  共享 HttpClient（见 {@code HttpClients}）
     * @param request 已构建好的请求
     * @param adapter 用于解析该供应商的响应格式
     */
    public static CompletableFuture<LlmResponse> send(HttpClient client, HttpRequest request,
                                                      LlmFormatAdapter adapter) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> parse(response.statusCode(), response.body(), adapter))
                .exceptionally(error -> LlmResponse.providerFailure(describe(error)));
    }

    /**
     * 把"状态码 + body"解析成 {@link LlmResponse}。
     *
     * <p>抽成独立方法是为了让测试可以不经网络直接覆盖各供应商的报文。</p>
     */
    public static LlmResponse parse(int statusCode, String body, LlmFormatAdapter adapter) {
        if (statusCode != 200) {
            return LlmResponse.httpFailure(statusCode, body);
        }
        if (body == null || body.isBlank()) {
            return LlmResponse.providerFailure("Empty response body");
        }
        JsonObject json;
        try {
            json = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException malformed) {
            return LlmResponse.providerFailure("Malformed JSON: " + malformed.getMessage());
        }
        return adapter.parseResponse(json);
    }

    /** 异常的可读摘要（类型 + 消息），日志定位用。 */
    public static String describe(Throwable error) {
        if (error == null) {
            return "null";
        }
        // CompletableFuture 会把真实原因包一层 CompletionException
        Throwable cause = error.getCause() == null ? error : error.getCause();
        String message = cause.getMessage();
        return (message == null || message.isBlank())
                ? cause.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + message;
    }
}
