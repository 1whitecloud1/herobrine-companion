package com.whitecloud233.herobrine_companion.client.llm;

import java.util.List;

/**
 * 聊天请求的中性载荷，由适配器序列化为各格式请求体。
 * messages 已含历史 + 当前用户消息；systemPrompt 由适配器按格式安放。
 */
public record LlmChatPayload(
        String systemPrompt,
        List<LlmChatMessage> messages,
        List<LlmToolSpec> tools,
        double temperature,
        double topP,
        int maxOutputTokens,
        boolean stream,
        boolean toolChoiceAuto) {

    public LlmChatPayload {
        if (systemPrompt == null) {
            systemPrompt = "";
        }
        if (messages == null) {
            messages = List.of();
        }
        if (tools == null) {
            tools = List.of();
        }
    }
}
