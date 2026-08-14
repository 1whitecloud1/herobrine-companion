package com.whitecloud233.herobrine_companion.client.llm;

import com.whitecloud233.herobrine_companion.client.llm.format.AnthropicAdapter;
import com.whitecloud233.herobrine_companion.client.llm.format.GeminiAdapter;
import com.whitecloud233.herobrine_companion.client.llm.format.OpenAiChatAdapter;
import com.whitecloud233.herobrine_companion.client.llm.format.OpenAiResponsesAdapter;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;

/**
 * 格式适配器工厂（组合根）：EndpointFormat → 适配器。
 * 适配器无状态，进程内单例。
 */
public final class LlmFormats {

    private static final LlmFormatAdapter OPENAI_CHAT = new OpenAiChatAdapter();
    private static final LlmFormatAdapter ANTHROPIC = new AnthropicAdapter();
    private static final LlmFormatAdapter OPENAI_RESPONSES = new OpenAiResponsesAdapter();
    private static final LlmFormatAdapter GEMINI = new GeminiAdapter();

    private LlmFormats() {
    }

    public static LlmFormatAdapter forFormat(LLMConfig.EndpointFormat format) {
        if (format == null) {
            return OPENAI_CHAT;
        }
        return switch (format) {
            case ANTHROPIC -> ANTHROPIC;
            case GEMINI -> GEMINI;
            case OPENAI_RESPONSES -> OPENAI_RESPONSES;
            default -> OPENAI_CHAT;
        };
    }
}
