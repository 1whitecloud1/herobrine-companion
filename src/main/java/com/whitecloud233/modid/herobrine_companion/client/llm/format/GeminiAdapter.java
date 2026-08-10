package com.whitecloud233.modid.herobrine_companion.client.llm.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmChatMessage;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmChatPayload;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmFormatAdapter;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmSettings;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmStreamingResponse;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmToolInvocation;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmToolSpec;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Google Gemini 原生（generateContent / streamGenerateContent）适配器。
 * 职责单一：仅处理这一种线上格式的请求构建 / 响应解析 / 流式 / 工具包装 / 鉴权。
 * 存储 endpoint 是 Gemini base（如 https://generativelanguage.googleapis.com/v1beta）。
 */
public final class GeminiAdapter implements LlmFormatAdapter {

    @Override
    public HttpRequest buildChatRequest(LlmSettings settings, LlmChatPayload payload) {
        String url = buildContentUrl(settings.endpoint(), settings.model(), payload.stream());
        JsonObject body = new JsonObject();

        JsonArray contents = new JsonArray();
        for (LlmChatMessage msg : payload.messages()) {
            if ("system".equals(msg.role())) {
                continue; // system 由 systemInstruction 表达
            }
            JsonObject content = new JsonObject();
            content.addProperty("role", "assistant".equals(msg.role()) ? "model" : msg.role());
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", msg.content());
            parts.add(part);
            content.add("parts", parts);
            contents.add(content);
        }
        body.add("contents", contents);

        body.add("systemInstruction", textPart(payload.systemPrompt()));
        body.add("generationConfig", generationConfig(payload.temperature(), payload.topP(), payload.maxOutputTokens()));

        if (!payload.tools().isEmpty()) {
            JsonArray functionDeclarations = new JsonArray();
            for (LlmToolSpec spec : payload.tools()) {
                JsonObject declaration = new JsonObject();
                declaration.addProperty("name", spec.name());
                declaration.addProperty("description", spec.description());
                declaration.add("parameters", spec.inputSchema());
                functionDeclarations.add(declaration);
            }
            JsonObject tool = new JsonObject();
            tool.add("functionDeclarations", functionDeclarations);
            JsonArray tools = new JsonArray();
            tools.add(tool);
            body.add("tools", tools);
        }
        return buildRequest(url, settings.apiKey(), body, payload.stream());
    }

    @Override
    public HttpRequest buildSimpleRequest(LlmSettings settings, String systemPrompt, String userPrompt,
                                          double temperature, double topP, int maxTokens) {
        String url = buildContentUrl(settings.endpoint(), settings.model(), false);
        JsonObject body = new JsonObject();

        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        userContent.add("parts", textPart(userPrompt).getAsJsonArray("parts"));
        JsonArray contents = new JsonArray();
        contents.add(userContent);
        body.add("contents", contents);

        body.add("systemInstruction", textPart(systemPrompt));
        body.add("generationConfig", generationConfig(temperature, topP, maxTokens));
        return buildRequest(url, settings.apiKey(), body, false);
    }

    private static JsonObject generationConfig(double temperature, double topP, int maxTokens) {
        JsonObject config = new JsonObject();
        config.addProperty("temperature", temperature);
        config.addProperty("topP", topP);
        config.addProperty("maxOutputTokens", maxTokens);
        return config;
    }

    private static JsonObject textPart(String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject wrapper = new JsonObject();
        wrapper.add("parts", parts);
        return wrapper;
    }

    private static String buildContentUrl(String endpoint, String model, boolean stream) {
        String base = endpoint;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String modelPath = model.startsWith("models/") ? model : "models/" + model;
        return base + "/" + modelPath + (stream ? ":streamGenerateContent?alt=sse" : ":generateContent");
    }

    private static HttpRequest buildRequest(String url, String apiKey, JsonObject body, boolean stream) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("x-goog-api-key", apiKey);
        if (stream) {
            builder.header("Accept", "text/event-stream");
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
    }

    @Override
    public String extractText(JsonObject json, String fallback) {
        try {
            JsonArray candidates = json.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return fallback;
            }
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            if (content == null) {
                return fallback;
            }
            JsonArray parts = content.getAsJsonArray("parts");
            if (parts == null) {
                return fallback;
            }
            StringBuilder text = new StringBuilder();
            for (JsonElement element : parts) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject part = element.getAsJsonObject();
                if (part.has("text") && !part.get("text").isJsonNull()) {
                    text.append(part.get("text").getAsString());
                }
            }
            return text.isEmpty() ? fallback : text.toString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    public LlmToolInvocation extractToolInvocation(JsonObject json) {
        try {
            JsonArray candidates = json.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            if (content == null) {
                return null;
            }
            JsonArray parts = content.getAsJsonArray("parts");
            if (parts == null) {
                return null;
            }
            for (JsonElement element : parts) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject part = element.getAsJsonObject();
                if (part.has("functionCall") && part.get("functionCall").isJsonObject()) {
                    JsonObject functionCall = part.getAsJsonObject("functionCall");
                    if (functionCall.has("name") && !functionCall.get("name").isJsonNull()) {
                        JsonObject args = functionCall.has("args") && functionCall.get("args").isJsonObject()
                                ? functionCall.getAsJsonObject("args")
                                : new JsonObject();
                        return new LlmToolInvocation(functionCall.get("name").getAsString(), args);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public JsonObject wrapTool(LlmToolSpec spec) {
        JsonObject declaration = new JsonObject();
        declaration.addProperty("name", spec.name());
        declaration.addProperty("description", spec.description());
        declaration.add("parameters", spec.inputSchema());
        JsonArray functionDeclarations = new JsonArray();
        functionDeclarations.add(declaration);
        JsonObject tool = new JsonObject();
        tool.add("functionDeclarations", functionDeclarations);
        return tool;
    }

    @Override
    public LlmStreamingResponse readStreaming(HttpClient client, HttpRequest request,
                                              Consumer<String> partialConsumer, Logger logger) {
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                try (InputStream errorStream = response.body()) {
                    String errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    return LlmStreamingResponse.error(response.statusCode(), errorBody);
                }
            }

            try (InputStream inputStream = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder replyBuilder = new StringBuilder();
                GeminiToolAccumulator toolAccumulator = new GeminiToolAccumulator();
                StreamEmitState emitState = new StreamEmitState();
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmedLine = line.trim();
                    if (!trimmedLine.startsWith("data:")) {
                        continue;
                    }
                    String data = trimmedLine.substring(5).trim();
                    if (data.isEmpty() || "{}".equals(data) || "[DONE]".equals(data)) {
                        continue;
                    }
                    processStreamingFrame(data, replyBuilder, toolAccumulator, partialConsumer, emitState, logger);
                }
                return LlmStreamingResponse.success(replyBuilder.toString(), toolAccumulator.getToolName(), toolAccumulator.getToolArguments());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void processStreamingFrame(String payload, StringBuilder replyBuilder, GeminiToolAccumulator toolAccumulator,
                                              Consumer<String> partialConsumer, StreamEmitState emitState, Logger logger) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            JsonArray candidates = json.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return;
            }
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            if (content == null) {
                return;
            }
            JsonArray parts = content.getAsJsonArray("parts");
            if (parts == null) {
                return;
            }
            for (JsonElement element : parts) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject part = element.getAsJsonObject();
                if (part.has("text") && !part.get("text").isJsonNull()) {
                    String deltaText = part.get("text").getAsString();
                    if (!deltaText.isEmpty()) {
                        replyBuilder.append(deltaText);
                        emitStreamingText(partialConsumer, replyBuilder, emitState);
                    }
                }
                if (part.has("functionCall") && part.get("functionCall").isJsonObject()) {
                    toolAccumulator.absorbFunctionCall(part.getAsJsonObject("functionCall"));
                }
            }
        } catch (Exception e) {
            logger.debug("Ignoring malformed gemini streaming payload: {}", payload, e);
        }
    }

    private static void emitStreamingText(Consumer<String> partialConsumer, StringBuilder replyBuilder, StreamEmitState emitState) {
        if (partialConsumer == null || replyBuilder.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int currentLength = replyBuilder.length();
        if ((now - emitState.lastEmitAt) < 40L && (currentLength - emitState.lastEmitLength) < 2) {
            return;
        }
        emitState.lastEmitAt = now;
        emitState.lastEmitLength = currentLength;
        partialConsumer.accept(replyBuilder.toString());
    }

    private static class StreamEmitState {
        private long lastEmitAt;
        private int lastEmitLength;
    }

    private static class GeminiToolAccumulator {
        private String toolName;
        private String toolArguments;

        private void absorbFunctionCall(JsonObject functionCall) {
            if (functionCall.has("name") && !functionCall.get("name").isJsonNull()) {
                this.toolName = functionCall.get("name").getAsString();
            }
            if (functionCall.has("args") && functionCall.get("args").isJsonObject()) {
                this.toolArguments = functionCall.getAsJsonObject("args").toString();
            }
        }

        private String getToolName() {
            return this.toolName;
        }

        private String getToolArguments() {
            return this.toolArguments;
        }
    }
}
