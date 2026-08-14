package com.whitecloud233.herobrine_companion.client.llm;

import com.google.gson.JsonObject;

/**
 * LLM 返回的工具调用（名字 + 已解析的 JSON 参数）。
 */
public record LlmToolInvocation(String name, JsonObject arguments) {}
