package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

public final class LLMModelDiscovery {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private LLMModelDiscovery() {
    }

    public static CompletableFuture<ModelDiscoveryResult> fetchModels(String endpoint, String apiKey,
                                                                      LLMConfig.Provider provider,
                                                                      LLMConfig.EndpointFormat endpointFormat) {
        String normalizedEndpoint = endpoint == null ? "" : endpoint.trim();
        String normalizedApiKey = apiKey == null ? "" : apiKey.trim();
        if (normalizedEndpoint.isEmpty()) {
            return CompletableFuture.completedFuture(ModelDiscoveryResult.failed("Endpoint is empty."));
        }
        if (normalizedApiKey.isEmpty() || normalizedApiKey.equals(LLMConfig.getDefaultApiKeyPlaceholder())) {
            return CompletableFuture.completedFuture(ModelDiscoveryResult.failed("API key is empty."));
        }

        String modelsEndpoint;
        try {
            modelsEndpoint = deriveModelsEndpoint(normalizedEndpoint);
            URI.create(modelsEndpoint);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(ModelDiscoveryResult.failed("Invalid model endpoint."));
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(modelsEndpoint))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .header("Accept", "application/json");

        if (endpointFormat == LLMConfig.EndpointFormat.ANTHROPIC) {
            requestBuilder.header("x-api-key", normalizedApiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION);
        } else {
            requestBuilder.header("Authorization", "Bearer " + normalizedApiKey);
        }

        if (provider == LLMConfig.Provider.OPENROUTER) {
            requestBuilder.header("X-Title", "Herobrine Companion");
        }

        return CLIENT.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return ModelDiscoveryResult.httpError(response.statusCode());
                    }
                    try {
                        return ModelDiscoveryResult.success(parseModelIds(response.body()));
                    } catch (Exception e) {
                        return ModelDiscoveryResult.failed("Could not parse model list.");
                    }
                })
                .exceptionally(e -> ModelDiscoveryResult.failed(resolveExceptionMessage(e)));
    }

    public static String deriveModelsEndpoint(String endpoint) {
        String normalized = removeQueryAndFragment(endpoint == null ? "" : endpoint.trim());
        normalized = trimTrailingSlash(normalized);
        String lower = normalized.toLowerCase(Locale.ROOT);

        if (lower.endsWith("/models")) {
            return normalized;
        }
        if (lower.endsWith("/chat/completions")) {
            return normalized.substring(0, normalized.length() - "/chat/completions".length()) + "/models";
        }
        if (lower.endsWith("/messages")) {
            return normalized.substring(0, normalized.length() - "/messages".length()) + "/models";
        }
        if (lower.endsWith("/responses")) {
            return normalized.substring(0, normalized.length() - "/responses".length()) + "/models";
        }
        if (lower.matches(".*/v\\d+(\\.\\d+)?")) {
            return normalized + "/models";
        }
        if (hasNoPath(normalized)) {
            if (lower.contains("deepseek.com")) {
                return normalized + "/models";
            }
            return normalized + "/v1/models";
        }
        return normalized + "/models";
    }

    private static List<String> parseModelIds(String body) {
        JsonElement root = JsonParser.parseString(body);
        JsonArray modelArray = findModelArray(root);
        if (modelArray == null) {
            return List.of();
        }

        TreeSet<String> modelIds = new TreeSet<>();
        for (JsonElement element : modelArray) {
            String modelId = extractModelId(element);
            if (modelId != null && !modelId.isBlank()) {
                modelIds.add(modelId.trim());
            }
        }
        return List.copyOf(modelIds);
    }

    private static JsonArray findModelArray(JsonElement root) {
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
        return null;
    }

    private static String extractModelId(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();
        for (String key : List.of("id", "name", "model")) {
            if (object.has(key) && object.get(key).isJsonPrimitive()) {
                return object.get(key).getAsString();
            }
        }
        return null;
    }

    private static String removeQueryAndFragment(String endpoint) {
        int queryIndex = endpoint.indexOf('?');
        int fragmentIndex = endpoint.indexOf('#');
        int cutIndex = -1;
        if (queryIndex >= 0) {
            cutIndex = queryIndex;
        }
        if (fragmentIndex >= 0 && (cutIndex < 0 || fragmentIndex < cutIndex)) {
            cutIndex = fragmentIndex;
        }
        return cutIndex >= 0 ? endpoint.substring(0, cutIndex) : endpoint;
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean hasNoPath(String endpoint) {
        try {
            String path = URI.create(endpoint).getPath();
            return path == null || path.isBlank() || "/".equals(path);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String resolveExceptionMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }

    public record ModelDiscoveryResult(boolean success, List<String> models, int httpStatus, String error) {
        public static ModelDiscoveryResult success(List<String> models) {
            return new ModelDiscoveryResult(true, List.copyOf(models == null ? new ArrayList<>() : models), 200, "");
        }

        public static ModelDiscoveryResult httpError(int httpStatus) {
            return new ModelDiscoveryResult(false, List.of(), httpStatus, "");
        }

        public static ModelDiscoveryResult failed(String error) {
            return new ModelDiscoveryResult(false, List.of(), 0, error == null ? "" : error);
        }
    }
}
