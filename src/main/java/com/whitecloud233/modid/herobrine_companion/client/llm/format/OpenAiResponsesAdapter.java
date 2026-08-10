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
import com.whitecloud233.modid.herobrine_companion.client.service.LLMConfig;
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
 * OpenAI Responses API（/v1/responses）适配器。
 * 职责单一：仅处理这一种线上格式的请求构建 / 响应解析 / 流式 / 工具包装 / 鉴权。
 */
public final class OpenAiResponsesAdapter implements LlmFormatAdapter {

    @Override
    public HttpRequest buildChatRequest(LlmSettings settings, LlmChatPayload payload) {
        JsonObject body = new JsonObject();
        body.addProperty("model", settings.model());
        body.addProperty("stream", payload.stream());
        body.addProperty("temperature", payload.temperature());
        body.addProperty("top_p", payload.topP());
        body.addProperty("max_output_tokens", payload.maxOutputTokens());
        body.addProperty("instructions", payload.systemPrompt());

        JsonArray input = new JsonArray();
        for (LlmChatMessage msg : payload.messages()) {
            JsonObject item = new JsonObject();
            item.addProperty("role", msg.role());
            item.addProperty("content", msg.content());
            input.add(item);
        }
        body.add("input", input);

        if (!payload.tools().isEmpty()) {
            JsonArray tools = new JsonArray();
            for (LlmToolSpec spec : payload.tools()) {
                tools.add(wrapTool(spec));
            }
            body.add("tools", tools);
            if (payload.toolChoiceAuto()) {
                body.addProperty("tool_choice", "auto");
            }
        }
        return buildRequest(settings, body, payload.stream());
    }

    @Override
    public HttpRequest buildSimpleRequest(LlmSettings settings, String systemPrompt, String userPrompt,
                                          double temperature, double topP, int maxTokens) {
        JsonObject body = new JsonObject();
        body.addProperty("model", settings.model());
        body.addProperty("stream", false);
        body.addProperty("temperature", temperature);
        body.addProperty("top_p", Math.min(1.0D, Math.max(0.7D, topP)));
        body.addProperty("max_output_tokens", maxTokens);
        body.addProperty("instructions", systemPrompt);

        JsonArray input = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userPrompt);
        input.add(user);
        body.add("input", input);
        return buildRequest(settings, body, false);
    }

    private static HttpRequest buildRequest(LlmSettings settings, JsonObject body, boolean stream) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(settings.endpoint()))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + settings.apiKey());
        if (stream) {
            builder.header("Accept", "text/event-stream");
        }
        if (settings.provider() == LLMConfig.Provider.OPENROUTER) {
            builder.header("X-Title", "Herobrine Companion");
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
    }

    @Override
    public String extractText(JsonObject json, String fallback) {
        try {
            if (json.has("output_text") && !json.get("output_text").isJsonNull()) {
                String direct = json.get("output_text").getAsString();
                if (!direct.isBlank()) {
                    return direct;
                }
            }
            JsonArray output = json.getAsJsonArray("output");
            if (output == null || output.isEmpty()) {
                return fallback;
            }
            StringBuilder text = new StringBuilder();
            for (JsonElement element : output) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject item = element.getAsJsonObject();
                if (!item.has("type") || !"message".equals(item.get("type").getAsString())) {
                    continue;
                }
                JsonArray content = item.has("content") && item.get("content").isJsonArray() ? item.getAsJsonArray("content") : null;
                if (content == null) {
                    continue;
                }
                for (JsonElement partElement : content) {
                    if (!partElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject part = partElement.getAsJsonObject();
                    if (part.has("type") && "output_text".equals(part.get("type").getAsString())
                            && part.has("text") && !part.get("text").isJsonNull()) {
                        text.append(part.get("text").getAsString());
                    }
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
            JsonArray output = json.getAsJsonArray("output");
            if (output == null || output.isEmpty()) {
                return null;
            }
            for (JsonElement element : output) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject item = element.getAsJsonObject();
                if (!item.has("type") || !"function_call".equals(item.get("type").getAsString())) {
                    continue;
                }
                if (!item.has("name") || !item.has("arguments")) {
                    continue;
                }
                String toolName = item.get("name").getAsString();
                String arguments = item.get("arguments").getAsString();
                JsonObject args;
                try {
                    args = JsonParser.parseString(arguments).getAsJsonObject();
                } catch (Exception e) {
                    args = new JsonObject();
                }
                return new LlmToolInvocation(toolName, args);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public JsonObject wrapTool(LlmToolSpec spec) {
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.addProperty("name", spec.name());
        tool.addProperty("description", spec.description());
        tool.add("parameters", spec.inputSchema());
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
                ResponsesToolAccumulator toolAccumulator = new ResponsesToolAccumulator();
                StreamEmitState emitState = new StreamEmitState();
                StringBuilder dataBuffer = new StringBuilder();
                String eventName = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (!dataBuffer.isEmpty()) {
                            processStreamingEvent(eventName, dataBuffer.toString(), replyBuilder, toolAccumulator, partialConsumer, emitState, logger);
                            dataBuffer.setLength(0);
                        }
                        eventName = null;
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        eventName = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        if (!dataBuffer.isEmpty()) {
                            dataBuffer.append('\n');
                        }
                        dataBuffer.append(line.substring(5).trim());
                    }
                }
                if (!dataBuffer.isEmpty()) {
                    processStreamingEvent(eventName, dataBuffer.toString(), replyBuilder, toolAccumulator, partialConsumer, emitState, logger);
                }
                return LlmStreamingResponse.success(replyBuilder.toString(), toolAccumulator.getToolName(), toolAccumulator.getToolArguments());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void processStreamingEvent(String eventName, String payload, StringBuilder replyBuilder,
                                              ResponsesToolAccumulator toolAccumulator, Consumer<String> partialConsumer,
                                              StreamEmitState emitState, Logger logger) {
        try {
            if (payload == null || payload.isBlank()) {
                return;
            }
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String resolvedEvent = eventName;
            if ((resolvedEvent == null || resolvedEvent.isBlank()) && json.has("type") && !json.get("type").isJsonNull()) {
                resolvedEvent = json.get("type").getAsString();
            }
            if (resolvedEvent == null) {
                return;
            }

            switch (resolvedEvent) {
                case "response.output_text.delta" -> {
                    if (json.has("delta") && !json.get("delta").isJsonNull()) {
                        String delta = json.get("delta").getAsString();
                        if (!delta.isEmpty()) {
                            replyBuilder.append(delta);
                            emitStreamingText(partialConsumer, replyBuilder, emitState);
                        }
                    }
                }
                case "response.output_item.added" -> {
                    if (json.has("item") && json.get("item").isJsonObject()) {
                        JsonObject item = json.getAsJsonObject("item");
                        if (item.has("type") && "function_call".equals(item.get("type").getAsString())) {
                            toolAccumulator.startFunctionCall(item);
                        }
                    }
                }
                case "response.function_call_arguments.delta" -> {
                    if (json.has("delta") && !json.get("delta").isJsonNull()) {
                        toolAccumulator.appendArguments(json.get("delta").getAsString());
                    }
                }
                default -> {
                }
            }
        } catch (Exception e) {
            logger.debug("Ignoring malformed responses streaming payload: {}", payload, e);
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

    private static class ResponsesToolAccumulator {
        private String toolName;
        private final StringBuilder arguments = new StringBuilder();
        private boolean complete;

        private void startFunctionCall(JsonObject item) {
            if (item.has("name") && !item.get("name").isJsonNull()) {
                this.toolName = item.get("name").getAsString();
            }
            if (item.has("arguments") && !item.get("arguments").isJsonNull()) {
                String present = item.get("arguments").getAsString();
                if (!present.isBlank()) {
                    this.arguments.append(present);
                }
            }
            this.complete = true;
        }

        private void appendArguments(String delta) {
            if (this.toolName == null) {
                return;
            }
            this.arguments.append(delta);
        }

        private String getToolName() {
            return this.toolName;
        }

        private String getToolArguments() {
            return this.arguments.isEmpty() ? null : this.arguments.toString();
        }
    }
}
