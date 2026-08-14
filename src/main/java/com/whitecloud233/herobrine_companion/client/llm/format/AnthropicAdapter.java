package com.whitecloud233.herobrine_companion.client.llm.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.herobrine_companion.client.llm.LlmChatMessage;
import com.whitecloud233.herobrine_companion.client.llm.LlmChatPayload;
import com.whitecloud233.herobrine_companion.client.llm.LlmFormatAdapter;
import com.whitecloud233.herobrine_companion.client.llm.LlmSettings;
import com.whitecloud233.herobrine_companion.client.llm.LlmStreamingResponse;
import com.whitecloud233.herobrine_companion.client.llm.LlmToolInvocation;
import com.whitecloud233.herobrine_companion.client.llm.LlmToolSpec;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Anthropic Messages（/v1/messages）适配器。
 * 职责单一：仅处理这一种线上格式的请求构建 / 响应解析 / 流式 / 工具包装 / 鉴权。
 */
public final class AnthropicAdapter implements LlmFormatAdapter {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    public HttpRequest buildChatRequest(LlmSettings settings, LlmChatPayload payload) {
        JsonObject body = new JsonObject();
        body.addProperty("model", settings.model());
        body.addProperty("stream", payload.stream());
        body.addProperty("temperature", payload.temperature());
        body.addProperty("top_p", payload.topP());
        body.addProperty("max_tokens", payload.maxOutputTokens());
        body.addProperty("system", payload.systemPrompt());

        JsonArray messages = new JsonArray();
        for (LlmChatMessage msg : payload.messages()) {
            JsonObject message = new JsonObject();
            message.addProperty("role", msg.role());
            message.addProperty("content", msg.content());
            messages.add(message);
        }
        body.add("messages", messages);

        if (!payload.tools().isEmpty()) {
            JsonArray tools = new JsonArray();
            for (LlmToolSpec spec : payload.tools()) {
                tools.add(wrapTool(spec));
            }
            body.add("tools", tools);
            if (payload.toolChoiceAuto()) {
                JsonObject toolChoice = new JsonObject();
                toolChoice.addProperty("type", "auto");
                body.add("tool_choice", toolChoice);
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
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("system", systemPrompt);

        JsonArray messages = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userPrompt);
        messages.add(user);
        body.add("messages", messages);
        return buildRequest(settings, body, false);
    }

    private static HttpRequest buildRequest(LlmSettings settings, JsonObject body, boolean stream) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(settings.endpoint()))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("x-api-key", settings.apiKey())
                .header("anthropic-version", ANTHROPIC_VERSION);
        if (stream) {
            builder.header("Accept", "text/event-stream");
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
    }

    @Override
    public String extractText(JsonObject json, String fallback) {
        try {
            JsonArray content = json.getAsJsonArray("content");
            if (content == null || content.isEmpty()) {
                return fallback;
            }
            StringBuilder text = new StringBuilder();
            for (JsonElement element : content) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject block = element.getAsJsonObject();
                if (block.has("type") && "text".equals(block.get("type").getAsString())
                        && block.has("text") && !block.get("text").isJsonNull()) {
                    text.append(block.get("text").getAsString());
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
            JsonArray content = json.getAsJsonArray("content");
            if (content == null || content.isEmpty()) {
                return null;
            }
            for (JsonElement element : content) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject block = element.getAsJsonObject();
                if (block.has("type") && "tool_use".equals(block.get("type").getAsString())
                        && block.has("name") && block.has("input") && block.get("input").isJsonObject()) {
                    return new LlmToolInvocation(block.get("name").getAsString(), block.getAsJsonObject("input"));
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public JsonObject wrapTool(LlmToolSpec spec) {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", spec.name());
        tool.addProperty("description", spec.description());
        tool.add("input_schema", spec.inputSchema());
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
                AnthropicStreamToolAccumulator toolAccumulator = new AnthropicStreamToolAccumulator();
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
                                              AnthropicStreamToolAccumulator toolAccumulator, Consumer<String> partialConsumer,
                                              StreamEmitState emitState, Logger logger) {
        try {
            if (payload == null || payload.isBlank() || "[DONE]".equals(payload)) {
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
                case "content_block_start" -> toolAccumulator.startBlock(json);
                case "content_block_delta" -> {
                    if (json.has("delta") && json.get("delta").isJsonObject()) {
                        JsonObject delta = json.getAsJsonObject("delta");
                        if (delta.has("type") && "text_delta".equals(delta.get("type").getAsString())
                                && delta.has("text") && !delta.get("text").isJsonNull()) {
                            replyBuilder.append(delta.get("text").getAsString());
                            emitStreamingText(partialConsumer, replyBuilder, emitState);
                        } else if (delta.has("type") && "input_json_delta".equals(delta.get("type").getAsString())
                                && delta.has("partial_json") && !delta.get("partial_json").isJsonNull()) {
                            int index = json.has("index") && !json.get("index").isJsonNull() ? json.get("index").getAsInt() : 0;
                            toolAccumulator.appendPartialJson(index, delta.get("partial_json").getAsString());
                        }
                    }
                }
                case "content_block_stop" -> {
                    if (json.has("index") && !json.get("index").isJsonNull()) {
                        toolAccumulator.finishBlock(json.get("index").getAsInt());
                    }
                }
                default -> {
                }
            }
        } catch (Exception e) {
            logger.debug("Ignoring malformed anthropic streaming payload: {}", payload, e);
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

    private static class AnthropicStreamToolAccumulator {
        private final Map<Integer, AnthropicStreamToolCall> toolCalls = new TreeMap<>();

        private void startBlock(JsonObject eventJson) {
            if (!eventJson.has("content_block") || !eventJson.get("content_block").isJsonObject()) {
                return;
            }
            JsonObject contentBlock = eventJson.getAsJsonObject("content_block");
            if (!contentBlock.has("type") || !"tool_use".equals(contentBlock.get("type").getAsString())) {
                return;
            }
            int index = eventJson.has("index") && !eventJson.get("index").isJsonNull() ? eventJson.get("index").getAsInt() : 0;
            AnthropicStreamToolCall toolCall = this.toolCalls.computeIfAbsent(index, ignored -> new AnthropicStreamToolCall());
            if (contentBlock.has("name") && !contentBlock.get("name").isJsonNull()) {
                toolCall.name = contentBlock.get("name").getAsString();
            }
            if (contentBlock.has("input") && contentBlock.get("input").isJsonObject()) {
                toolCall.arguments = contentBlock.getAsJsonObject("input").toString();
                toolCall.complete = true;
            }
        }

        private void appendPartialJson(int index, String partialJson) {
            AnthropicStreamToolCall toolCall = this.toolCalls.computeIfAbsent(index, ignored -> new AnthropicStreamToolCall());
            toolCall.partialJson.append(partialJson);
        }

        private void finishBlock(int index) {
            AnthropicStreamToolCall toolCall = this.toolCalls.get(index);
            if (toolCall == null || toolCall.complete || toolCall.partialJson.isEmpty()) {
                return;
            }
            toolCall.arguments = toolCall.partialJson.toString();
            toolCall.complete = true;
        }

        private String getToolName() {
            return this.toolCalls.isEmpty() ? null : this.toolCalls.values().iterator().next().name;
        }

        private String getToolArguments() {
            return this.toolCalls.isEmpty() ? null : this.toolCalls.values().iterator().next().arguments;
        }
    }

    private static class AnthropicStreamToolCall {
        private String name;
        private final StringBuilder partialJson = new StringBuilder();
        private String arguments;
        private boolean complete;
    }
}
