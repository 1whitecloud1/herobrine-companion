package com.whitecloud233.herobrine_companion.client.llm;

/**
 * 中性对话消息。由适配器转换为各线上格式的表示（OpenAI messages / Anthropic messages /
 * Responses input / Gemini contents）。
 */
public record LlmChatMessage(String role, String content) {
    public LlmChatMessage {
        if (role == null) {
            role = "";
        }
        if (content == null) {
            content = "";
        }
    }
}
