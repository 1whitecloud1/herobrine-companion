package com.whitecloud233.herobrine_companion.client.llm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** 流式与非流式共用的不可变结果；保留当前一次执行一个工具的契约。 */
public record LlmResponse(String text, String reasoning, LlmToolInvocation tool,
                          LlmFinishReason finishReason, LlmFailure failure) {
    public LlmResponse {
        text = text == null ? "" : text;
        reasoning = reasoning == null ? "" : reasoning;
        finishReason = finishReason == null ? LlmFinishReason.UNKNOWN : finishReason;
    }

    public static LlmResponse success(String text, String reasoning, LlmToolInvocation tool, LlmFinishReason reason) {
        return new LlmResponse(text, reasoning, tool, reason, null);
    }

    public static LlmResponse streamed(String text, String reasoning, String toolName, String toolArguments,
                                      LlmFinishReason reason) {
        LlmToolInvocation tool = null;
        if (toolName != null && !toolName.isBlank()) {
            try {
                if (toolArguments == null || toolArguments.isBlank()) throw new IllegalArgumentException("Missing tool arguments");
                JsonObject arguments = JsonParser.parseString(toolArguments).getAsJsonObject();
                tool = new LlmToolInvocation(toolName, arguments);
            } catch (RuntimeException e) {
                return new LlmResponse(text, reasoning, null, reason,
                        new LlmFailure(LlmFailure.Kind.INVALID_RESPONSE, 200, "Invalid tool arguments"));
            }
        }
        return success(text, reasoning, tool, reason);
    }

    public static LlmResponse httpFailure(int statusCode, String detail) {
        return new LlmResponse("", "", null, LlmFinishReason.ERROR, LlmFailure.http(statusCode, detail));
    }

    public static LlmResponse providerFailure(String detail) {
        LlmFailure classification = LlmFailure.http(200, detail);
        return new LlmResponse("", "", null, LlmFinishReason.ERROR,
                classification.kind() == LlmFailure.Kind.AUTHENTICATION ? classification
                        : new LlmFailure(LlmFailure.Kind.PROVIDER, 200, detail));
    }

    public boolean successful() { return failure == null; }
    public boolean truncated() { return finishReason == LlmFinishReason.TOKEN_LIMIT; }
}
