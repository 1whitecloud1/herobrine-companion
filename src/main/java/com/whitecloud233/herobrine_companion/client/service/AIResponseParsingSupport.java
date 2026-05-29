package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class AIResponseParsingSupport {
    private AIResponseParsingSupport() {}

    static String extractOpenAiMessageText(JsonObject json, String fallback) {
        try {
            JsonObject responseMessageObj = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
            if (responseMessageObj.has("content") && !responseMessageObj.get("content").isJsonNull()) {
                return responseMessageObj.get("content").getAsString();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    static String extractAnthropicText(JsonObject json, String fallback) {
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

    static ToolInvocation extractOpenAiToolInvocation(JsonObject json) {
        try {
            JsonObject responseMessageObj = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
            if (!responseMessageObj.has("tool_calls")) {
                return null;
            }
            JsonArray toolCalls = responseMessageObj.getAsJsonArray("tool_calls");
            if (toolCalls == null || toolCalls.isEmpty()) {
                return null;
            }
            JsonObject funcObj = toolCalls.get(0).getAsJsonObject().getAsJsonObject("function");
            String toolName = funcObj.get("name").getAsString();
            JsonObject args = JsonParser.parseString(funcObj.get("arguments").getAsString()).getAsJsonObject();
            return new ToolInvocation(toolName, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    static ToolInvocation extractAnthropicToolInvocation(JsonObject json) {
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
                    return new ToolInvocation(block.get("name").getAsString(), block.getAsJsonObject("input"));
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    record ToolInvocation(String name, JsonObject arguments) {}
}
