package com.whitecloud233.modid.herobrine_companion.client.llm;

import com.google.gson.JsonObject;

/**
 * 中性工具描述：名字 + 说明 + JSON Schema 参数。
 * 各格式适配器负责包装成 OpenAI function / Anthropic input_schema /
 * Responses flat function / Gemini functionDeclarations。
 */
public record LlmToolSpec(String name, String description, JsonObject inputSchema) {
    public LlmToolSpec {
        if (name == null) {
            name = "";
        }
        if (description == null) {
            description = "";
        }
    }
}
