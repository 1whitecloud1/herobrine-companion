package com.whitecloud233.modid.herobrine_companion.client.llm.format;

import com.google.gson.JsonObject;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmFinishReason;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmResponse;

/** SSE 终态独立于正文和工具增量，错误帧不能被当成成功的空回复。 */
final class StreamCompletion {
    private LlmFinishReason reason = LlmFinishReason.UNKNOWN;
    private String error;

    void finish(LlmFinishReason next) {
        if (next != null && next != LlmFinishReason.UNKNOWN) reason = next;
    }

    boolean readError(JsonObject json) {
        if (json.has("error") && !json.get("error").isJsonNull()) {
            error = json.get("error").toString();
            return true;
        }
        if (json.has("type") && !json.get("type").isJsonNull() && "error".equals(json.get("type").getAsString())) {
            error = json.has("message") ? json.get("message").toString() : "Provider stream error";
            return true;
        }
        return false;
    }

    LlmResponse response(String text, String reasoning, String toolName, String arguments) {
        if (error != null || reason == LlmFinishReason.ERROR) {
            return LlmResponse.providerFailure(error != null ? error : "Provider stream failed");
        }
        return LlmResponse.streamed(text, reasoning, toolName, arguments, reason);
    }
}
