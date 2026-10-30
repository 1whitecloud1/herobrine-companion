package com.whitecloud233.herobrine_companion.client.llm.format;

import com.google.gson.JsonArray;
import com.whitecloud233.herobrine_companion.client.llm.LlmHttp;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.herobrine_companion.client.llm.LlmChatMessage;
import com.whitecloud233.herobrine_companion.client.llm.LlmChatPayload;
import com.whitecloud233.herobrine_companion.client.llm.LlmFormatAdapter;
import com.whitecloud233.herobrine_companion.client.llm.LlmSettings;
import com.whitecloud233.herobrine_companion.client.llm.LlmResponse;
import com.whitecloud233.herobrine_companion.client.llm.LlmFinishReason;
import com.whitecloud233.herobrine_companion.client.llm.LlmToolInvocation;
import com.whitecloud233.herobrine_companion.client.llm.LlmToolSpec;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
        return LlmHttp.post(settings.endpoint(), settings.apiKey(), body.toString(), stream,
                LlmHttp.AuthStyle.BEARER,
                settings.provider() == LLMConfig.Provider.OPENROUTER
                        ? new String[]{"X-Title", "Herobrine Companion"}
                        : null);
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
                JsonObject args = JsonParser.parseString(arguments).getAsJsonObject();
                return new LlmToolInvocation(toolName, args);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public LlmFinishReason extractFinishReason(JsonObject json) {
        return finishReason(json);
    }

    private static LlmFinishReason finishReason(JsonObject json) {
        try {
            String status = json.get("status").getAsString();
            if ("completed".equals(status)) return LlmFinishReason.COMPLETE;
            if ("failed".equals(status) || "cancelled".equals(status)) return LlmFinishReason.ERROR;
            if ("incomplete".equals(status)) {
                String reason = json.getAsJsonObject("incomplete_details").get("reason").getAsString();
                return switch (reason) {
                    case "max_output_tokens" -> LlmFinishReason.TOKEN_LIMIT;
                    case "content_filter" -> LlmFinishReason.CONTENT_FILTERED;
                    default -> LlmFinishReason.UNKNOWN;
                };
            }
        } catch (RuntimeException ignored) {
        }
        return LlmFinishReason.UNKNOWN;
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
    public LlmResponse readStreaming(HttpClient client, HttpRequest request,
                                              Consumer<String> partialConsumer, Logger logger) {
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                try (InputStream errorStream = response.body()) {
                    String errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    return LlmResponse.httpFailure(response.statusCode(), errorBody);
                }
            }

            try (InputStream inputStream = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder replyBuilder = new StringBuilder();
                ResponsesToolAccumulator toolAccumulator = new ResponsesToolAccumulator();
                StreamTextEmitter emitState = new StreamTextEmitter();
                StreamCompletion completion = new StreamCompletion();
                StringBuilder reasoningBuilder = new StringBuilder();
                StringBuilder dataBuffer = new StringBuilder();
                String eventName = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (!dataBuffer.isEmpty()) {
                            processStreamingEvent(eventName, dataBuffer.toString(), replyBuilder, reasoningBuilder, toolAccumulator, partialConsumer, emitState, completion, logger);
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
                    processStreamingEvent(eventName, dataBuffer.toString(), replyBuilder, reasoningBuilder, toolAccumulator, partialConsumer, emitState, completion, logger);
                }
                return completion.response(replyBuilder.toString(), reasoningBuilder.toString(), toolAccumulator.getToolName(), toolAccumulator.getToolArguments());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void processStreamingEvent(String eventName, String payload, StringBuilder replyBuilder,
                                              StringBuilder reasoningBuilder, ResponsesToolAccumulator toolAccumulator, Consumer<String> partialConsumer,
                                              StreamTextEmitter emitState, StreamCompletion completion, Logger logger) {
        try {
            if (payload == null || payload.isBlank()) {
                return;
            }
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            if (completion.readError(json)) return;
            String resolvedEvent = eventName;
            if ((resolvedEvent == null || resolvedEvent.isBlank()) && json.has("type") && !json.get("type").isJsonNull()) {
                resolvedEvent = json.get("type").getAsString();
            }
            if (resolvedEvent == null) {
                return;
            }

            switch (resolvedEvent) {
                case "response.completed", "response.incomplete", "response.failed", "response.cancelled" -> {
                    if (json.has("response") && json.get("response").isJsonObject()) {
                        JsonObject response = json.getAsJsonObject("response");
                        completion.readError(response);
                        completion.finish(finishReason(response));
                    } else if ("response.failed".equals(resolvedEvent) || "response.cancelled".equals(resolvedEvent)) {
                        completion.finish(LlmFinishReason.ERROR);
                    }
                }
                case "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> {
                    if (json.has("delta") && !json.get("delta").isJsonNull()) reasoningBuilder.append(json.get("delta").getAsString());
                }
                case "response.output_text.delta" -> {
                    if (json.has("delta") && !json.get("delta").isJsonNull()) {
                        String delta = json.get("delta").getAsString();
                        if (!delta.isEmpty()) {
                            replyBuilder.append(delta);
                            emitState.emit(partialConsumer, replyBuilder);
                        }
                    }
                }
                case "response.output_item.added" -> {
                    if (json.has("item") && json.get("item").isJsonObject()) {
                        JsonObject item = json.getAsJsonObject("item");
                        if (item.has("type") && "function_call".equals(item.get("type").getAsString())) {
                            toolAccumulator.startFunctionCall(json, item);
                        }
                    }
                }
                case "response.function_call_arguments.delta" -> {
                    if (json.has("delta") && !json.get("delta").isJsonNull()) {
                        toolAccumulator.appendArguments(json, json.get("delta").getAsString());
                    }
                }
                default -> {
                }
            }
        } catch (Exception e) {
            logger.debug("Ignoring malformed responses streaming payload: {}", payload, e);
        }
    }

    private static class ResponsesToolAccumulator {
        private String toolName;
        private String itemId;
        private Integer outputIndex;
        private final StringBuilder arguments = new StringBuilder();
        private boolean sawAnotherCall;

        private void startFunctionCall(JsonObject event, JsonObject item) {
            if (this.toolName != null) {
                sawAnotherCall = true;
                return;
            }
            if (item.has("name") && !item.get("name").isJsonNull()) this.toolName = item.get("name").getAsString();
            if (item.has("id") && !item.get("id").isJsonNull()) this.itemId = item.get("id").getAsString();
            if (event.has("output_index")) this.outputIndex = event.get("output_index").getAsInt();
            if (item.has("arguments") && !item.get("arguments").isJsonNull()) {
                this.arguments.append(item.get("arguments").getAsString());
            }
        }

        private void appendArguments(JsonObject event, String delta) {
            if (toolName == null) return;
            if (event.has("item_id") && itemId != null) {
                if (!itemId.equals(event.get("item_id").getAsString())) return;
            } else if (event.has("output_index") && outputIndex != null) {
                if (outputIndex != event.get("output_index").getAsInt()) return;
            } else if (sawAnotherCall) {
                return;
            }
            arguments.append(delta);
        }

        private String getToolName() { return toolName; }
        private String getToolArguments() { return arguments.isEmpty() ? null : arguments.toString(); }
    }
}
