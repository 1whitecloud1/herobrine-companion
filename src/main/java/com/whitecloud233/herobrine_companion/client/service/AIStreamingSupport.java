package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

final class AIStreamingSupport {
    private AIStreamingSupport() {}

    static StreamingResponse readStreamingResponse(HttpClient client, HttpRequest request, LLMConfig.EndpointFormat endpointFormat,
                                                   Consumer<String> partialConsumer, Logger logger) {
        return endpointFormat == LLMConfig.EndpointFormat.ANTHROPIC
                ? readAnthropicStreamingResponse(client, request, partialConsumer, logger)
                : readOpenAiStreamingResponse(client, request, partialConsumer, logger);
    }

    private static StreamingResponse readOpenAiStreamingResponse(HttpClient client, HttpRequest request, Consumer<String> partialConsumer, Logger logger) {
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                try (InputStream errorStream = response.body()) {
                    String errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    return StreamingResponse.error(response.statusCode(), errorBody);
                }
            }

            try (InputStream inputStream = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder replyBuilder = new StringBuilder();
                StreamToolAccumulator toolAccumulator = new StreamToolAccumulator();
                StreamEmitState emitState = new StreamEmitState();
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
                        processOpenAiStreamingPayload(data, replyBuilder, toolAccumulator, partialConsumer, emitState, logger);
                    } else if (trimmedLine.startsWith("{")) {
                        processOpenAiStreamingPayload(trimmedLine, replyBuilder, toolAccumulator, partialConsumer, emitState, logger);
                    }
                }
                return StreamingResponse.success(replyBuilder.toString(), toolAccumulator.getToolName(), toolAccumulator.getToolArguments());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static StreamingResponse readAnthropicStreamingResponse(HttpClient client, HttpRequest request, Consumer<String> partialConsumer, Logger logger) {
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                try (InputStream errorStream = response.body()) {
                    String errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    return StreamingResponse.error(response.statusCode(), errorBody);
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
                            processAnthropicStreamingEvent(eventName, dataBuffer.toString(), replyBuilder, toolAccumulator, partialConsumer, emitState, logger);
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
                    processAnthropicStreamingEvent(eventName, dataBuffer.toString(), replyBuilder, toolAccumulator, partialConsumer, emitState, logger);
                }
                return StreamingResponse.success(replyBuilder.toString(), toolAccumulator.getToolName(), toolAccumulator.getToolArguments());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void processOpenAiStreamingPayload(String payload, StringBuilder replyBuilder, StreamToolAccumulator toolAccumulator,
                                                      Consumer<String> partialConsumer, StreamEmitState emitState, Logger logger) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                return;
            }
            JsonObject choice = choices.get(0).getAsJsonObject();
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
            if (delta.has("tool_calls") && delta.get("tool_calls").isJsonArray()) {
                toolAccumulator.absorb(delta.getAsJsonArray("tool_calls"));
            }
        } catch (Exception e) {
            logger.debug("Ignoring malformed streaming payload: {}", payload, e);
        }
    }

    private static void processAnthropicStreamingEvent(String eventName, String payload, StringBuilder replyBuilder,
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

    static final class StreamingResponse {
        final int statusCode;
        final String errorBody;
        final String reply;
        final String toolName;
        final String toolArguments;

        private StreamingResponse(int statusCode, String errorBody, String reply, String toolName, String toolArguments) {
            this.statusCode = statusCode;
            this.errorBody = errorBody;
            this.reply = reply;
            this.toolName = toolName;
            this.toolArguments = toolArguments;
        }

        static StreamingResponse success(String reply, String toolName, String toolArguments) {
            return new StreamingResponse(200, null, reply, toolName, toolArguments);
        }

        static StreamingResponse error(int statusCode, String errorBody) {
            return new StreamingResponse(statusCode, errorBody, null, null, null);
        }
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
