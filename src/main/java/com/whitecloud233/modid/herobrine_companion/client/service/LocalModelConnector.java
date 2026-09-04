package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 本地蒸馏模型（llama.cpp / Ollama 等本地 OpenAI 兼容服务）一键连接助手。
 *
 * <p>流程：探测本地候选端口（llama.cpp 8090、Ollama 11434）→ 读 /v1/models 拿到模型名 →
 * 自动写入独立"本地模型"槽位 + 聊天任务路由，实现"傻瓜式"接入，且不覆盖任何云端/自定义档案。
 * 玩家只需要：① 先启动本地服务器；② 在游戏设置里点一个按钮。
 */
public final class LocalModelConnector {

    /** 默认端口：llama.cpp 分发包（start_herobrine_cpu.bat）使用 8090。 */
    public static final int DEFAULT_PORT = 8090;
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final String DEFAULT_MODEL = "herobrine";
    public static final String DEFAULT_ENDPOINT = "http://" + DEFAULT_HOST + ":" + DEFAULT_PORT + "/v1/chat/completions";

    /** 本地 OpenAI 兼容候选：先 llama.cpp 分发包，再 Ollama。 */
    private static final List<String> CANDIDATE_BASES = List.of(
            "http://127.0.0.1:8090",
            "http://127.0.0.1:11434"
    );

    /** 本地服务任意 API Key 即可（llama.cpp 需要 Bearer，Ollama 忽略）。 */
    private static final String LOCAL_API_KEY = "sk-local";

    /** 聊天任务回退到的云端提供商（工具/元任务走全局，不受影响）。 */
    private static final String CLOUD_FALLBACK = "qiniu_cloud";

    // 回环地址由 SystemProxy.SELECTOR 判为直连，本地 llama 探测不受代理开关影响
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .proxy(SystemProxy.SELECTOR)
            .build();

    public record ProbeResult(boolean reachable, String modelId, String endpoint) {
    }

    private LocalModelConnector() {
    }

    /** 异步探测本地服务器，返回首个可达节点的模型名与端点。带硬超时，永不无限期卡住。 */
    public static CompletableFuture<ProbeResult> probeAsync() {
        return probeCandidates(0)
                .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(e -> new ProbeResult(false, null, DEFAULT_ENDPOINT));
    }

    private static CompletableFuture<ProbeResult> probeCandidates(int index) {
        if (index >= CANDIDATE_BASES.size()) {
            return CompletableFuture.completedFuture(new ProbeResult(false, null, DEFAULT_ENDPOINT));
        }
        String base = CANDIDATE_BASES.get(index);
        return probeSingle(base).thenCompose(result -> {
            if (result.reachable()) {
                return CompletableFuture.completedFuture(result);
            }
            return probeCandidates(index + 1);
        });
    }

    private static CompletableFuture<ProbeResult> probeSingle(String base) {
        return CLIENT.sendAsync(HttpRequest.newBuilder()
                        .uri(URI.create(base + "/v1/models"))
                        .timeout(Duration.ofSeconds(6))
                        .header("Authorization", "Bearer " + LOCAL_API_KEY)
                        .header("Accept", "application/json")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return new ProbeResult(false, null, base + "/v1/chat/completions");
                    }
                    String modelId = parseFirstModelId(response.body());
                    return new ProbeResult(true,
                            modelId == null ? DEFAULT_MODEL : modelId,
                            base + "/v1/chat/completions");
                })
                .exceptionally(e -> new ProbeResult(false, null, base + "/v1/chat/completions"));
    }

    /** 解析 OpenAI 风格 /v1/models 响应里的第一个模型 id。 */
    private static String parseFirstModelId(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            JsonElement arrayElement = null;
            if (root.isJsonObject() && root.getAsJsonObject().has("data")) {
                arrayElement = root.getAsJsonObject().get("data");
            } else if (root.isJsonArray()) {
                arrayElement = root;
            }
            if (arrayElement == null || !arrayElement.isJsonArray()) {
                return null;
            }
            JsonArray data = arrayElement.getAsJsonArray();
            for (JsonElement element : data) {
                if (element.isJsonObject()) {
                    JsonElement id = element.getAsJsonObject().get("id");
                    if (id != null && id.isJsonPrimitive()) {
                        String value = id.getAsString();
                        if (value != null && !value.isBlank()) {
                            return value.trim();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 响应不可解析时按可达处理，用默认模型名
        }
        return null;
    }

    /**
     * 写入本地模型完整配置：
     * 1) 独立本地槽位（端点为本地服务、模型名取服务器实际模型）——不覆盖、不占用任何云端/自定义档案；
     * 2) 聊天类任务路由 → 本地槽位（虚拟 id "local"），备用云端；
     * 3) 本地模型不算云端模式：不改全局活跃提供商，也不要求云端 Key 才能使用。
     */
    public static void applyConfig(String endpoint, String modelId) {
        String normalizedEndpoint = (endpoint == null || endpoint.isBlank()) ? DEFAULT_ENDPOINT : endpoint.trim();
        String normalizedModel = (modelId == null || modelId.isBlank()) ? DEFAULT_MODEL : modelId.trim();

        // 1) 只写独立本地模型槽位（内部已落盘），云端/自定义档案原样保留。
        LLMConfig.setLocalModel(normalizedEndpoint, normalizedModel);

        // 2) 聊天类任务路由 → 本地槽位，备用云端；不动全局活跃提供商。
        LLMConfig.setTaskRoute(LlmTask.MAIN_CHAT, new LLMConfig.TaskRoute(LLMConfig.LOCAL_ROUTE_ID, CLOUD_FALLBACK));
        LLMConfig.setTaskRoute(LlmTask.SCOPED_CHAT, new LLMConfig.TaskRoute(LLMConfig.LOCAL_ROUTE_ID, CLOUD_FALLBACK));
        LLMConfig.setTaskRoute(LlmTask.CROSS_SESSION, new LLMConfig.TaskRoute(LLMConfig.LOCAL_ROUTE_ID, CLOUD_FALLBACK));
        LLMConfig.save();
    }
}
