package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.client.llm.LlmSettings;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 上下文窗口解析：<b>只认服务端返回的真实值，不再按模型名猜。</b>
 *
 * <p>取值优先级（高 → 低）：</p>
 * <ol>
 *   <li><b>被动学习</b>：真实请求被服务端拒绝时，从 400/413/422 报文里解析出的窗口
 *       —— 这是服务端亲口报的数，最可信，可覆盖其它来源。</li>
 *   <li><b>主动探测</b>：元数据端点（OpenRouter {@code /api/v1/models} 的 context_length、
 *       Gemini {@code /v1beta/models/{id}} 的 inputTokenLimit、llama.cpp {@code /props} 的
 *       n_ctx、Ollama {@code /api/show} 的 model_info.*.context_length），
 *       失败时退到错误探针。</li>
 *   <li><b>缓存里的旧真实值</b>：探测不到时沿用上次拿到的真实值（比猜强）。</li>
 *   <li><b>保守兜底</b>：本地回环用启动器实际下发的 {@code -c} 参数，云端用
 *       {@link #UNKNOWN_WINDOW}。这只是"还没探测到"时的安全值，不是模型推断。</li>
 * </ol>
 *
 * <p>所有网络操作都是异步的：同步入口 {@link #resolve(LlmSettings)} 永远不阻塞，
 * 未命中缓存时后台发一次探测，下次调用即可用上真实值。</p>
 */
public final class LLMContextWindow {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /** 来源：服务端错误报文（最可信）。 */
    public static final String SOURCE_PASSIVE = "passive";
    /** 来源：错误探针（超大 max_tokens 触发服务端拒绝）。 */
    public static final String SOURCE_PROBE = "probe";
    /** 来源：元数据端点。 */
    public static final String SOURCE_META = "meta";

    /** 探测不到真实值时的保守兜底（云端）。只用于"尚未探测到"的窗口期。 */
    public static final int UNKNOWN_WINDOW = 8_192;

    /**
     * 探测不到真实值时的本地服务兜底：llama.cpp / Ollama 常见的默认 -c。
     * 本地服务在本机，/props 探测通常几毫秒就回来，这个常量只作探测前的占位。
     */
    public static final int LOCAL_FALLBACK_WINDOW = 16_384;

    private static final long FRESH_MILLIS = Duration.ofHours(6).toMillis();
    private static final long RETRY_COOLDOWN_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(12);

    /** 错误探针申请的 token 数：故意远超任何真实窗口，逼服务端报出自己的上限。 */
    private static final int PROBE_MAX_TOKENS = 100_000_000;

    private static final Pattern ANTHROPIC_MAXIMUM =
            Pattern.compile("([0-9][0-9,_]*)\\s*tokens\\s*>\\s*([0-9][0-9,_]*)\\s*maximum", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAX_CONTEXT_LENGTH =
            Pattern.compile("maximum context length is\\s+(?:approximately\\s+)?([0-9][0-9,_]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AVAILABLE_CONTEXT_SIZE =
            Pattern.compile("available context size[^0-9]{0,40}([0-9][0-9,_]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAX_TOKENS_ALLOWED =
            Pattern.compile("maximum number of tokens allowed[^0-9]{0,20}\\(?([0-9][0-9,_]*)\\)?", Pattern.CASE_INSENSITIVE);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .proxy(SystemProxy.SELECTOR)
            .build();

    private static final Map<String, CachedEntry> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_ATTEMPT = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();
    private static volatile boolean diskLoaded = false;

    private LLMContextWindow() {
    }

    /** 一条已探测到的真实上下文窗口。 */
    public record Snapshot(int contextWindow, String source, long updatedAtMillis) {
        public boolean known() {
            return contextWindow > 0;
        }
    }

    /** 一次探测的结果：窗口值 + 来源（决定与其它来源冲突时的优先级）。 */
    private record ProbeResult(int window, String source) {
    }

    /**
     * 同步入口：返回该 endpoint+model 的上下文窗口。
     *
     * <p>命中缓存直接返回真实值（必要时后台刷新）；未命中时先返回保守兜底，
     * 同时后台发起一次探测，探测结果落盘后下次调用即生效。绝不阻塞调用线程。</p>
     */
    public static int resolve(LlmSettings settings) {
        if (settings == null || !settings.isUsable()) {
            return defaultWindow(settings);
        }
        String key = key(settings);
        CachedEntry entry = cache().get(key);
        if (entry != null && entry.contextWindow > 0) {
            if (System.currentTimeMillis() - entry.updatedAtMillis > FRESH_MILLIS) {
                probeIfStale(settings);
            }
            return entry.contextWindow;
        }
        probeIfStale(settings);
        return defaultWindow(settings);
    }

    /** 已探测到的真实值；未探测到返回 null（不触发探测）。 */
    public static Snapshot peek(LlmSettings settings) {
        if (settings == null || !settings.isUsable()) {
            return null;
        }
        CachedEntry entry = cache().get(key(settings));
        return entry == null ? null : entry.snapshot();
    }

    /** 调试用描述，如 {@code 131072 (meta)} / {@code 8192 (fallback)}。 */
    public static String describe(LlmSettings settings) {
        Snapshot snapshot = peek(settings);
        if (snapshot != null && snapshot.known()) {
            return snapshot.contextWindow() + " (" + snapshot.source() + ")";
        }
        return defaultWindow(settings) + " (fallback)";
    }

    /** 缓存仍是新鲜值时什么都不做；过期或缺失才发一次探测（带冷却与并发去重）。 */
    public static void probeIfStale(LlmSettings settings) {
        if (!probeable(settings)) {
            return;
        }
        String key = key(settings);
        long now = System.currentTimeMillis();
        CachedEntry entry = cache().get(key);
        if (entry != null && entry.contextWindow > 0 && now - entry.updatedAtMillis < FRESH_MILLIS) {
            return;
        }
        Long lastAttempt = LAST_ATTEMPT.get(key);
        if (lastAttempt != null && now - lastAttempt < RETRY_COOLDOWN_MILLIS) {
            return;
        }
        if (IN_FLIGHT.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        LAST_ATTEMPT.put(key, now);
        probeAsync(settings)
                .whenComplete((value, throwable) -> IN_FLIGHT.remove(key));
    }

    /** 强制探测一次（忽略新鲜度/冷却），返回真实窗口，探测不到返回 0。 */
    public static CompletableFuture<Integer> probeAsync(LlmSettings settings) {
        if (!probeable(settings)) {
            return CompletableFuture.completedFuture(0);
        }
        return probeMetadataAsync(settings)
                .thenCompose(metadata -> metadata.window() > 0
                        ? CompletableFuture.completedFuture(metadata)
                        : probeByOverflowAsync(settings))
                .thenApply(result -> {
                    if (result.window() > 0) {
                        store(settings, result.window(), result.source());
                    }
                    return result.window();
                })
                .exceptionally(e -> 0);
    }

    /**
     * 被动学习：真实请求被服务端拒绝时，从错误报文里解析出真实窗口并写入缓存。
     *
     * <p>这是服务端亲口说的数，优先级最高，可覆盖元数据/探针得到的值。</p>
     */
    public static void learnFromErrorResponse(LlmSettings settings, int statusCode, String responseBody) {
        if (settings == null || !settings.isUsable()) {
            return;
        }
        if (statusCode != 400 && statusCode != 413 && statusCode != 422) {
            return;
        }
        int window = parseContextWindowFromError(responseBody);
        if (window <= 0) {
            return;
        }
        if (store(settings, window, SOURCE_PASSIVE)) {
            LOGGER.info("[ContextWindow] 从服务端 {} 响应学到 {} 的真实上下文窗口：{}",
                    statusCode, key(settings), window);
        }
    }

    /** 配置变更导致 endpoint/model 变化时清掉缓存，避免沿用旧模型的窗口。 */
    public static void invalidate(String endpoint, String model) {
        if (endpoint == null || endpoint.isBlank() || model == null || model.isBlank()) {
            return;
        }
        String key = endpoint.trim().toLowerCase(Locale.ROOT) + "|" + model.trim();
        if (cache().remove(key) != null) {
            saveCache();
        }
    }

    // ---------- 探测：元数据端点 ----------

    private static CompletableFuture<ProbeResult> probeMetadataAsync(LlmSettings settings) {
        if (settings.provider() == LLMConfig.Provider.OPENROUTER) {
            return probeOpenRouterAsync(settings);
        }
        if (settings.format() == LLMConfig.EndpointFormat.GEMINI) {
            return probeGeminiAsync(settings);
        }
        if (LLMConfig.isLoopbackEndpoint(settings.endpoint())) {
            // 本地服务：llama.cpp /props → Ollama /api/show → /v1/models 的 n_ctx_train
            return chain(probeLlamaCppPropsAsync(settings),
                    () -> chain(probeOllamaShowAsync(settings),
                            () -> probeOpenAiModelsMetadataAsync(settings)));
        }
        return probeOpenAiModelsMetadataAsync(settings);
    }

    /** 上一级探测没拿到值就继续下一级。 */
    private static CompletableFuture<ProbeResult> chain(CompletableFuture<ProbeResult> first,
                                                        java.util.function.Supplier<CompletableFuture<ProbeResult>> next) {
        return first.thenCompose(result -> result.window() > 0
                ? CompletableFuture.completedFuture(result)
                : next.get());
    }

    private static CompletableFuture<ProbeResult> probeOpenRouterAsync(LlmSettings settings) {
        String url = apiRoot(settings.endpoint()) + "/models";
        return getJsonAsync(url, settings)
                .thenApply(body -> {
                    JsonObject model = findModelEntry(body, settings.model());
                    return metaResult(model == null ? 0 : scanContextLikeNumber(model));
                })
                .exceptionally(e -> metaResult(0));
    }

    /** Gemini：GET /v1beta/models/{id} → inputTokenLimit + outputTokenLimit。 */
    private static CompletableFuture<ProbeResult> probeGeminiAsync(LlmSettings settings) {
        String modelId = settings.model().trim();
        while (modelId.startsWith("models/")) {
            modelId = modelId.substring("models/".length());
        }
        if (modelId.isBlank()) {
            return CompletableFuture.completedFuture(metaResult(0));
        }
        String url = apiRoot(settings.endpoint()) + "/models/" + modelId;
        return getJsonAsync(url, settings)
                .thenApply(body -> {
                    JsonObject root = parseObject(body);
                    if (root == null) {
                        return metaResult(0);
                    }
                    int inputLimit = readInt(root, "inputTokenLimit");
                    int outputLimit = readInt(root, "outputTokenLimit");
                    int total = inputLimit + outputLimit;
                    return metaResult(total > 0 ? total : Math.max(inputLimit, outputLimit));
                })
                .exceptionally(e -> metaResult(0));
    }

    /** llama.cpp：GET /props → n_ctx（新版在 default_generation_settings.n_ctx）。 */
    private static CompletableFuture<ProbeResult> probeLlamaCppPropsAsync(LlmSettings settings) {
        String url = serverRoot(settings.endpoint()) + "/props";
        return getJsonAsync(url, settings)
                .thenApply(body -> {
                    JsonObject root = parseObject(body);
                    if (root == null) {
                        return metaResult(0);
                    }
                    int fromSettings = 0;
                    if (root.has("default_generation_settings")) {
                        fromSettings = scanContextLikeNumber(root.getAsJsonObject("default_generation_settings"));
                    }
                    return metaResult(Math.max(fromSettings, scanContextLikeNumber(root)));
                })
                .exceptionally(e -> metaResult(0));
    }

    /** Ollama：POST /api/show → model_info 里的 *.context_length。 */
    private static CompletableFuture<ProbeResult> probeOllamaShowAsync(LlmSettings settings) {
        String url = serverRoot(settings.endpoint()) + "/api/show";
        String payload = "{\"model\":" + jsonString(settings.model()) + "}";
        try {
            HttpRequest request = authorized(url, settings)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            return sendAsync(request)
                    .thenApply(body -> {
                        JsonObject root = parseObject(body);
                        if (root == null) {
                            return metaResult(0);
                        }
                        int topLevel = readInt(root, "context_length");
                        int fromModelInfo = 0;
                        if (root.has("model_info") && root.get("model_info").isJsonObject()) {
                            fromModelInfo = scanContextLikeNumber(root.getAsJsonObject("model_info"));
                        }
                        return metaResult(Math.max(topLevel, fromModelInfo));
                    })
                    .exceptionally(e -> metaResult(0));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(metaResult(0));
        }
    }

    /** 通用 OpenAI 兼容：/v1/models 里找本模型，扫 context_length / max_model_len 之类字段。 */
    private static CompletableFuture<ProbeResult> probeOpenAiModelsMetadataAsync(LlmSettings settings) {
        String url;
        try {
            url = LLMModelDiscovery.deriveModelsEndpoint(settings.endpoint());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(metaResult(0));
        }
        return getJsonAsync(url, settings)
                .thenApply(body -> {
                    JsonArray array = findModelArray(body);
                    if (array == null) {
                        return metaResult(0);
                    }
                    JsonObject exact = null;
                    for (JsonElement element : array) {
                        if (element == null || !element.isJsonObject()) {
                            continue;
                        }
                        JsonObject candidate = element.getAsJsonObject();
                        if (matchesModelId(candidate, settings.model())) {
                            exact = candidate;
                            break;
                        }
                    }
                    return metaResult(exact == null ? 0 : scanContextLikeNumber(exact));
                })
                .exceptionally(e -> metaResult(0));
    }

    // ---------- 探测：错误探针 ----------

    /**
     * 错误探针：提交一个 max_tokens 远超窗口的极简请求，从服务端的拒绝报文里读出真实窗口。
     *
     * <p>仅用于 OpenAI/Anthropic 兼容端点：本地回环走 /props，Gemini 走元数据端点。
     * 每个 endpoint+model 组合最多探测一次（结果或失败都会进冷却窗口），
     * 且探测结果一旦拿到就永久缓存，不会反复发这种注定被拒的请求。</p>
     */
    private static CompletableFuture<ProbeResult> probeByOverflowAsync(LlmSettings settings) {
        if (LLMConfig.isLoopbackEndpoint(settings.endpoint())) {
            return CompletableFuture.completedFuture(probeResult(0));
        }
        String payload = overflowProbePayload(settings);
        if (payload == null) {
            return CompletableFuture.completedFuture(probeResult(0));
        }
        try {
            HttpRequest request = authorized(settings.endpoint(), settings)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            return sendAsync(request)
                    .thenApply(body -> probeResult(parseContextWindowFromError(body)))
                    .exceptionally(e -> probeResult(0));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(probeResult(0));
        }
    }

    private static String overflowProbePayload(LlmSettings settings) {
        String model = settings.model().trim();
        if (model.isBlank()) {
            return null;
        }
        String modelJson = jsonString(model);
        switch (settings.format()) {
            case OPENAI_RESPONSES:
                return "{\"model\":" + modelJson + ",\"input\":\"ping\",\"max_output_tokens\":" + PROBE_MAX_TOKENS
                        + ",\"stream\":false}";
            case ANTHROPIC:
                return "{\"model\":" + modelJson + ",\"max_tokens\":" + PROBE_MAX_TOKENS
                        + ",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}]}";
            case OPENAI_CHAT:
                return "{\"model\":" + modelJson + ",\"max_tokens\":" + PROBE_MAX_TOKENS
                        + ",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"stream\":false}";
            default:
                return null;
        }
    }

    // ---------- 错误报文解析 ----------

    /** 从服务端拒绝报文里解析真实窗口；解析不到返回 0。 */
    static int parseContextWindowFromError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return 0;
        }
        Matcher matcher = ANTHROPIC_MAXIMUM.matcher(responseBody);
        if (matcher.find()) {
            int window = parseNumber(matcher.group(2));
            if (window > 0) {
                return window;
            }
        }
        int window = firstMatch(responseBody, MAX_CONTEXT_LENGTH);
        if (window > 0) {
            return window;
        }
        window = firstMatch(responseBody, AVAILABLE_CONTEXT_SIZE);
        if (window > 0) {
            return window;
        }
        window = firstMatch(responseBody, MAX_TOKENS_ALLOWED);
        if (window > 0) {
            return window;
        }
        // 刻意不写"context length 后面跟个数字"这类宽松模式：
        // 真实报文里紧跟着的往往是"你请求了 N tokens"的 N，会把窗口记成偏大的错值。
        return 0;
    }

    private static int firstMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return parseNumber(matcher.group(1));
        }
        return 0;
    }

    private static int parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String digits = raw.replace(",", "").replace("_", "").trim();
        int dot = digits.indexOf('.');
        if (dot >= 0) {
            // 某些服务端把整数字段写成 131072.0
            digits = digits.substring(0, dot);
        }
        try {
            long value = Long.parseLong(digits);
            // 窗口不可能小于 512，也不可能大到 1e7；超出范围视为误匹配。
            if (value < 512L || value > 10_000_000L) {
                return 0;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------- HTTP ----------

    private static CompletableFuture<String> getJsonAsync(String url, LlmSettings settings) {
        try {
            HttpRequest request = authorized(url, settings).GET().build();
            return sendAsync(request);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 统一取响应体：错误探针要靠 4xx 的报文解析窗口，所以这里不按状态码过滤，
     * 由各探测方法自己判断（解析失败自然返回 0）。
     */
    private static CompletableFuture<String> sendAsync(HttpRequest request) {
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .orTimeout(HTTP_TIMEOUT.toMillis() + 2000L, TimeUnit.MILLISECONDS)
                .thenApply(response -> response.body() == null ? "" : response.body());
    }

    private static HttpRequest.Builder authorized(String url, LlmSettings settings) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json");

        String apiKey = settings.apiKey() == null ? "" : settings.apiKey().trim();
        if (!apiKey.isBlank()) {
            if (settings.format() == LLMConfig.EndpointFormat.ANTHROPIC) {
                builder.header("x-api-key", apiKey).header("anthropic-version", ANTHROPIC_VERSION);
            } else if (settings.format() == LLMConfig.EndpointFormat.GEMINI) {
                builder.header("x-goog-api-key", apiKey);
            } else {
                builder.header("Authorization", "Bearer " + apiKey);
            }
        }
        if (settings.provider() == LLMConfig.Provider.OPENROUTER) {
            builder.header("X-Title", "Herobrine Companion");
        }
        return builder;
    }

    // ---------- URL / JSON 工具 ----------

    private static String apiBase(String endpoint) {
        String value = trimTrailingSlash(endpoint == null ? "" : endpoint.trim());
        String lower = value.toLowerCase(Locale.ROOT);
        for (String suffix : List.of("/chat/completions", "/messages", "/responses", "/models")) {
            if (lower.endsWith(suffix)) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }

    /** 去掉动作路径后的 API 前缀（保留 /v1），用于拼 /models。 */
    private static String apiRoot(String endpoint) {
        return apiBase(endpoint);
    }

    /** 去掉动作路径与 /vN 后的服务器根，用于拼 /props、/api/show。 */
    private static String serverRoot(String endpoint) {
        String base = apiBase(endpoint);
        java.util.regex.Matcher matcher = Pattern.compile("^(.*)/v\\d+(?:\\.\\d+)?$").matcher(base);
        return matcher.find() ? matcher.group(1) : base;
    }

    private static JsonObject findModelEntry(String body, String model) {
        JsonArray array = findModelArray(body);
        if (array == null) {
            return null;
        }
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) {
                JsonObject candidate = element.getAsJsonObject();
                if (matchesModelId(candidate, model)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static JsonArray findModelArray(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(body);
            if (root == null || root.isJsonNull()) {
                return null;
            }
            if (root.isJsonArray()) {
                return root.getAsJsonArray();
            }
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject object = root.getAsJsonObject();
            if (object.has("data") && object.get("data").isJsonArray()) {
                return object.getAsJsonArray("data");
            }
            if (object.has("models") && object.get("models").isJsonArray()) {
                return object.getAsJsonArray("models");
            }
        } catch (Exception ignored) {
            // 非 JSON 响应（含错误探针的 4xx 报文）按"无模型列表"处理
        }
        return null;
    }

    private static boolean matchesModelId(JsonObject candidate, String model) {
        String target = model == null ? "" : model.trim();
        if (target.isBlank()) {
            return false;
        }
        for (String key : List.of("id", "name", "model")) {
            JsonElement element = candidate.get(key);
            if (element != null && element.isJsonPrimitive()) {
                String value = element.getAsString().trim();
                if (value.equalsIgnoreCase(target) || value.equalsIgnoreCase("models/" + target)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 递归扫出对象里"看起来像上下文窗口"的数值字段（键名含 context / n_ctx / max_model_len）。 */
    private static int scanContextLikeNumber(JsonObject object) {
        return scanContextLikeNumber(object, 0, 3);
    }

    private static int scanContextLikeNumber(JsonObject object, int depth, int maxDepth) {
        if (object == null || depth > maxDepth) {
            return 0;
        }
        int best = 0;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (value.isJsonObject()) {
                best = Math.max(best, scanContextLikeNumber(value.getAsJsonObject(), depth + 1, maxDepth));
                continue;
            }
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                continue;
            }
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (key.contains("context") || key.contains("n_ctx") || key.contains("max_model_len")) {
                best = Math.max(best, parseNumber(value.getAsString()));
            }
        }
        return best;
    }

    private static int readInt(JsonObject object, String key) {
        if (object == null || !object.has(key)) {
            return 0;
        }
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        return parseNumber(element.getAsString());
    }

    private static JsonObject parseObject(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(body);
            return root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String jsonString(String value) {
        return "\"" + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    // ---------- 缓存与持久化 ----------

    /** 值得发请求去探测吗：设置可用且 Key 不是占位符。 */
    private static boolean probeable(LlmSettings settings) {
        if (settings == null || !settings.isUsable()) {
            return false;
        }
        String apiKey = settings.apiKey() == null ? "" : settings.apiKey().trim();
        if (apiKey.isBlank()) {
            return false;
        }
        String placeholder = LLMConfig.getDefaultApiKeyPlaceholder();
        return placeholder == null || placeholder.isBlank() || !placeholder.equals(apiKey);
    }

    private static String key(LlmSettings settings) {
        String endpoint = settings.endpoint() == null ? "" : settings.endpoint().trim().toLowerCase(Locale.ROOT);
        String model = settings.model() == null ? "" : settings.model().trim();
        return endpoint + "|" + model;
    }

    /** 探测不到时的兜底：本地回环用常见默认 -c，云端用保守常量。 */
    private static int defaultWindow(LlmSettings settings) {
        if (settings != null && LLMConfig.isLoopbackEndpoint(settings.endpoint())) {
            return LOCAL_FALLBACK_WINDOW;
        }
        return UNKNOWN_WINDOW;
    }

    private static ProbeResult metaResult(int window) {
        return new ProbeResult(window, SOURCE_META);
    }

    private static ProbeResult probeResult(int window) {
        return new ProbeResult(window, SOURCE_PROBE);
    }

    private static Map<String, CachedEntry> cache() {
        if (!diskLoaded) {
            synchronized (LLMContextWindow.class) {
                if (!diskLoaded) {
                    loadCache();
                    diskLoaded = true;
                }
            }
        }
        return CACHE;
    }

    private static void loadCache() {
        File file = cacheFile();
        if (!file.isFile()) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type rootType = new TypeToken<Map<String, Map<String, CachedEntry>>>() {
            }.getType();
            Map<String, Map<String, CachedEntry>> parsed = GSON.fromJson(reader, rootType);
            Map<String, CachedEntry> entries = parsed == null ? null : parsed.get("entries");
            if (entries == null) {
                return;
            }
            for (Map.Entry<String, CachedEntry> entry : entries.entrySet()) {
                CachedEntry value = entry.getValue();
                if (value != null && value.contextWindow > 0 && entry.getKey() != null) {
                    CACHE.put(entry.getKey(), value);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[ContextWindow] 缓存读取失败，将按未探测处理", e);
        }
    }

    private static synchronized boolean store(LlmSettings settings, int window, String source) {
        if (window <= 0) {
            return false;
        }
        String key = key(settings);
        CachedEntry previous = cache().get(key);
        if (previous != null && previous.contextWindow == window) {
            previous.updatedAtMillis = System.currentTimeMillis();
            if (!source.equals(previous.source)) {
                previous.source = source;
                saveCache();
            }
            return false;
        }
        // 更不可信的来源不覆盖已探测到的值：passive > probe > meta
        if (previous != null && rank(source) > rank(previous.source)) {
            return false;
        }
        cache().put(key, new CachedEntry(window, source, System.currentTimeMillis()));
        saveCache();
        LOGGER.info("[ContextWindow] {} -> {}（来源 {}）", key, window, source);
        return true;
    }

    private static int rank(String source) {
        if (SOURCE_PASSIVE.equals(source)) {
            return 0;
        }
        if (SOURCE_PROBE.equals(source)) {
            return 1;
        }
        return 2;
    }

    private static void saveCache() {
        try {
            File file = cacheFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("entries", new LinkedHashMap<>(CACHE));
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            LOGGER.warn("[ContextWindow] 缓存写入失败", e);
        }
    }

    private static File cacheFile() {
        // 与私钥文件同处游戏目录：这是本机探测结果，不该被整合包打包带走。
        return FMLPaths.GAMEDIR.get().resolve("herobrine_ai_context_cache.json").toFile();
    }

    /** 持久化条目（普通类而非 record，避免依赖特定 Gson 版本的 record 支持）。 */
    private static final class CachedEntry {
        int contextWindow;
        String source = "";
        long updatedAtMillis;

        CachedEntry() {
        }

        CachedEntry(int contextWindow, String source, long updatedAtMillis) {
            this.contextWindow = contextWindow;
            this.source = source == null ? "" : source;
            this.updatedAtMillis = updatedAtMillis;
        }

        Snapshot snapshot() {
            return new Snapshot(contextWindow, source, updatedAtMillis);
        }
    }
}
