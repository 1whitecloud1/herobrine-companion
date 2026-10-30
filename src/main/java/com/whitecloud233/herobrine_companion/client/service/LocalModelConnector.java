package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.whitecloud233.herobrine_companion.client.llm.LlmTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Connects only to a player-configured, already installed local OpenAI-compatible service. */
public final class LocalModelConnector {
    public static final String DEFAULT_ENDPOINT = "http://127.0.0.1:8090/v1/chat/completions";
    private static final HttpClient CLIENT = HttpClients.direct(5);

    public enum Status {
        READY, INVALID_ENDPOINT, MISSING_MODEL, INVALID_KEY, AUTHENTICATION,
        MODEL_NOT_FOUND, INVALID_RESPONSE, HTTP_ERROR, UNAVAILABLE;

        public String translationKey() {
            return "message.herobrine_companion.local_model_probe." + name().toLowerCase(Locale.ROOT);
        }
    }

    public record ProbeResult(Status status, String modelId, String endpoint, int statusCode) {
        public boolean reachable() {
            return status == Status.READY;
        }
    }

    private LocalModelConnector() {
    }

    public static boolean isValidApiKey(String apiKey) {
        String value = apiKey == null ? "" : apiKey.trim();
        return value.chars().allMatch(character -> character >= 0x20 && character <= 0x7e);
    }

    /** Tests the exact address and model without scanning ports or selecting another model. */
    public static CompletableFuture<ProbeResult> probeEndpointAsync(String endpoint, String modelId, String apiKey) {
        String normalized = LocalModelEndpoint.normalize(endpoint);
        String model = modelId == null ? "" : modelId.trim();
        if (normalized.isEmpty()) return result(Status.INVALID_ENDPOINT, model, normalized, 0);
        if (model.isEmpty()) return result(Status.MISSING_MODEL, model, normalized, 0);
        if (!isValidApiKey(apiKey)) return result(Status.INVALID_KEY, model, normalized, 0);
        String key = apiKey == null || apiKey.isBlank() ? LLMConfig.LOCAL_MODEL_KEY : apiKey.trim();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LocalModelEndpoint.modelsEndpoint(normalized)))
                .timeout(Duration.ofSeconds(6))
                .header("Authorization", "Bearer " + key)
                .header("Accept", "application/json")
                .GET().build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .orTimeout(8, TimeUnit.SECONDS)
                .thenApply(response -> {
                    int code = response.statusCode();
                    if (code == 401 || code == 403) return new ProbeResult(Status.AUTHENTICATION, model, normalized, code);
                    if (code < 200 || code >= 300) return new ProbeResult(Status.HTTP_ERROR, model, normalized, code);
                    List<String> models = parseModelIds(response.body());
                    Status status = models == null ? Status.INVALID_RESPONSE
                            : models.contains(model) ? Status.READY : Status.MODEL_NOT_FOUND;
                    return new ProbeResult(status, model, normalized, code);
                })
                .exceptionally(error -> new ProbeResult(Status.UNAVAILABLE, model, normalized, 0));
    }

    private static CompletableFuture<ProbeResult> result(Status status, String model, String endpoint, int code) {
        return CompletableFuture.completedFuture(new ProbeResult(status, model, endpoint, code));
    }

    static List<String> parseModelIds(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            JsonElement data = root.isJsonArray() ? root
                    : root.isJsonObject() ? root.getAsJsonObject().get("data") : null;
            if (data == null || !data.isJsonArray()) return null;
            List<String> models = new ArrayList<>();
            for (JsonElement element : data.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonElement id = element.getAsJsonObject().get("id");
                if (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()) {
                    String value = id.getAsString().trim();
                    if (!value.isEmpty()) models.add(value);
                }
            }
            return List.copyOf(models);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static void applyConfig(String endpoint, String modelId, String apiKey) {
        LLMConfig.setLocalModel(endpoint, modelId, apiKey);
        // Local chat must not silently fall back to a cloud provider.
        for (LlmTask task : List.of(LlmTask.MAIN_CHAT, LlmTask.SCOPED_CHAT, LlmTask.CROSS_SESSION)) {
            LLMConfig.setTaskRoute(task, new LLMConfig.TaskRoute(LLMConfig.LOCAL_ROUTE_ID, ""));
        }
        LLMConfig.save();
    }
}
