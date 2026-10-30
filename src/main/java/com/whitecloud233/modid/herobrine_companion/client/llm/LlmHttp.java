package com.whitecloud233.modid.herobrine_companion.client.llm;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * LLM 请求的统一构造（全项目唯一一处 {@code HttpRequest.newBuilder()}）。
 *
 * <p><b>为什么需要它</b>：四个供应商适配器此前各写了一份 {@code buildRequest}，
 * 其中 OpenAI Chat 与 Responses 两份<b>逐字节相同</b>，Anthropic 与 Gemini 只差鉴权头名字。
 * 更关键的是，四处都<b>没有设置 {@code .timeout(...)}</b> —— 只有
 * {@code HttpClients} 给的连接超时，请求一旦发出去而服务端不回，就会永久挂住
 * {@code CompletableFuture}，玩家侧表现为"AI 永远不回话"。</p>
 *
 * <p><b>鉴权差异用 {@link AuthStyle} 表达</b>，而不是让每个适配器自己拼 header：
 * OpenAI 系走 {@code Authorization: Bearer}，Anthropic 走 {@code x-api-key} +
 * {@code anthropic-version}，Gemini 走 {@code x-goog-api-key}。</p>
 */
public final class LlmHttp {

    /** 非流式请求的总超时。 */
    private static final Duration NON_STREAM_TIMEOUT = Duration.ofSeconds(60);
    /**
     * 流式请求的超时。
     *
     * <p>注意 {@code HttpRequest.timeout} 只管到"响应头到达"，<b>不覆盖</b>后续
     * {@code BodyHandlers.ofInputStream()} 的读帧阶段。因此这里的值要足够容纳
     * 首字节等待（长推理模型的思考时间）。</p>
     */
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(300);

    private LlmHttp() {
    }

    /** 供应商的鉴权头风格。 */
    public enum AuthStyle {
        /** {@code Authorization: Bearer <key>}（OpenAI 兼容端点）。 */
        BEARER,
        /** {@code x-api-key}。 */
        ANTHROPIC,
        /** {@code x-goog-api-key}。 */
        GEMINI
    }

    /**
     * 构造 POST 请求。
     *
     * @param endpoint   完整端点 URL
     * @param apiKey     API Key（空则不发鉴权头）
     * @param body       已序列化的 JSON 请求体
     * @param stream     是否走 SSE
     * @param authStyle  鉴权头风格
     * @param extraHeader 额外固定 header（可为 null）；Anthropic 的
     *                    {@code anthropic-version} 走这里
     */
    public static HttpRequest post(String endpoint, String apiKey, String body, boolean stream,
                                   AuthStyle authStyle, String[] extraHeader) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(stream ? STREAM_TIMEOUT : NON_STREAM_TIMEOUT)
                .header("Content-Type", "application/json; charset=UTF-8");

        if (apiKey != null && !apiKey.isBlank()) {
            switch (authStyle) {
                case BEARER -> builder.header("Authorization", "Bearer " + apiKey);
                case ANTHROPIC -> builder.header("x-api-key", apiKey);
                case GEMINI -> builder.header("x-goog-api-key", apiKey);
            }
        }
        if (extraHeader != null && extraHeader.length == 2) {
            builder.header(extraHeader[0], extraHeader[1]);
        }
        if (stream) {
            builder.header("Accept", "text/event-stream");
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
    }

    /** 无额外 header 的便捷版本。 */
    public static HttpRequest post(String endpoint, String apiKey, String body, boolean stream, AuthStyle authStyle) {
        return post(endpoint, apiKey, body, stream, authStyle, null);
    }
}
