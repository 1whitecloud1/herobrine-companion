package com.whitecloud233.modid.herobrine_companion.client.llm.format;

import com.google.gson.JsonArray;
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
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * OpenAI Chat Completions（/v1/chat/completions）适配器。
 * 职责单一：仅处理这一种线上格式的请求构建 / 响应解析 / 流式 / 工具包装 / 鉴权。
 */
public final class OpenAiChatAdapter implements LlmFormatAdapter {

    @Override
    public HttpRequest buildChatRequest(LlmSettings settings, LlmChatPayload payload) {
        JsonObject body = new JsonObject();
        body.addProperty("model", settings.model());
        body.addProperty("stream", payload.stream());
        body.addProperty("temperature", payload.temperature());
        body.addProperty("top_p", payload.topP());
        body.addProperty("presence_penalty", 0.35D);
        body.addProperty("frequency_penalty", 0.45D);
        body.addProperty("max_tokens", payload.maxOutputTokens());

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", payload.systemPrompt());
        messages.add(system);
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
        body.addProperty("presence_penalty", 0.35D);
        body.addProperty("frequency_penalty", 0.45D);
        body.addProperty("max_tokens", maxTokens);

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", systemPrompt);
        messages.add(system);
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
            JsonObject message = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
            if (message.has("content") && !message.get("content").isJsonNull()) {
                return message.get("content").getAsString();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    @Override
    public String extractReasoning(JsonObject json) {
        try {
            JsonObject message = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
            if (message.has("reasoning_content") && !message.get("reasoning_content").isJsonNull()) {
                return message.get("reasoning_content").getAsString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    @Override
    public LlmToolInvocation extractToolInvocation(JsonObject json) {
        try {
            JsonObject message = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
            if (!message.has("tool_calls")) {
                return null;
            }
            JsonArray toolCalls = message.getAsJsonArray("tool_calls");
            if (toolCalls == null || toolCalls.isEmpty()) {
                return null;
            }
            JsonObject function = toolCalls.get(0).getAsJsonObject().getAsJsonObject("function");
            String toolName = function.get("name").getAsString();
            JsonObject args = JsonParser.parseString(function.get("arguments").getAsString()).getAsJsonObject();
            return new LlmToolInvocation(toolName, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public JsonObject wrapTool(LlmToolSpec spec) {
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        JsonObject function = new JsonObject();
        function.addProperty("name", spec.name());
        function.addProperty("description", spec.description());
        function.add("parameters", spec.inputSchema());
        tool.add("function", function);
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
                StringBuilder reasoningBuilder = new StringBuilder();
                StreamToolAccumulator toolAccumulator = new StreamToolAccumulator();
                StreamEmitState emitState = new StreamEmitState();
                String[] finishReasonHolder = new String[1];
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String trimmedLine = line.trim();
                    if (trimmedLine.startsWith("data:")) {
                        String data = trimmedLine.substring(5).trim();
                        if (data.isEmpty()) {
                            continue;
                        }
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        processStreamingPayload(data, replyBuilder, reasoningBuilder, toolAccumulator, partialConsumer, emitState, finishReasonHolder, logger);
                    } else if (trimmedLine.startsWith("{")) {
                        processStreamingPayload(trimmedLine, replyBuilder, reasoningBuilder, toolAccumulator, partialConsumer, emitState, finishReasonHolder, logger);
                    }
                }
                return LlmStreamingResponse.success(replyBuilder.toString(), reasoningBuilder.toString(),
                        toolAccumulator.getToolName(), toolAccumulator.getToolArguments(), finishReasonHolder[0]);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void processStreamingPayload(String payload, StringBuilder replyBuilder, StringBuilder reasoningBuilder,
                                                StreamToolAccumulator toolAccumulator,
                                                Consumer<String> partialConsumer, StreamEmitState emitState,
                                                String[] finishReasonHolder, Logger logger) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                return;
            }
            JsonObject choice = choices.get(0).getAsJsonObject();
            if (finishReasonHolder[0] == null && choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                finishReasonHolder[0] = choice.get("finish_reason").getAsString();
            }
            JsonObject delta = null;
            if (choice.has("delta") && choice.get("delta").isJsonObject()) {
                delta = choice.getAsJsonObject("delta");
            } else if (choice.has("message") && choice.get("message").isJsonObject()) {
                delta = choice.getAsJsonObject("message");
            }
            if (delta == null) {
                return;
            }
            if (delta.has("content") && !delta.get("content").isJsonNull()) {
                String deltaText = delta.get("content").getAsString();
                if (!deltaText.isEmpty()) {
                    replyBuilder.append(deltaText);
                    emitStreamingText(partialConsumer, replyBuilder, emitState);
                }
            }
            if (delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull()) {
                String deltaReasoning = delta.get("reasoning_content").getAsString();
                if (!deltaReasoning.isEmpty()) {
                    reasoningBuilder.append(deltaReasoning);
                }
            }
            if (delta.has("tool_calls") && delta.get("tool_calls").isJsonArray()) {
                toolAccumulator.absorb(delta.getAsJsonArray("tool_calls"));
            }
        } catch (Exception e) {
            logger.debug("Ignoring malformed streaming payload: {}", payload, e);
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

    private static class StreamToolAccumulator {
        private final Map<Integer, StreamToolCall> toolCalls = new TreeMap<>();

        private void absorb(JsonArray deltaToolCalls) {
            for (int i = 0; i < deltaToolCalls.size(); i++) {
                JsonObject toolCallObj = deltaToolCalls.get(i).getAsJsonObject();
                int index = toolCallObj.has("index") && !toolCallObj.get("index").isJsonNull()
                        ? toolCallObj.get("index").getAsInt()
                        : i;
                StreamToolCall toolCall = this.toolCalls.computeIfAbsent(index, ignored -> new StreamToolCall());
                if (toolCallObj.has("function") && toolCallObj.get("function").isJsonObject()) {
                    JsonObject functionObj = toolCallObj.getAsJsonObject("function");
                    if (functionObj.has("name") && !functionObj.get("name").isJsonNull()) {
                        toolCall.name.append(functionObj.get("name").getAsString());
                    }
                    if (functionObj.has("arguments") && !functionObj.get("arguments").isJsonNull()) {
                        toolCall.arguments.append(functionObj.get("arguments").getAsString());
                    }
                }
            }
        }

        private String getToolName() {
            return this.toolCalls.isEmpty() ? null : this.toolCalls.values().iterator().next().name.toString();
        }

        private String getToolArguments() {
            return this.toolCalls.isEmpty() ? null : this.toolCalls.values().iterator().next().arguments.toString();
        }
    }

    private static class StreamToolCall {
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();
    }
}
