package com.whitecloud233.modid.herobrine_companion.client.llm;

/** 不暴露供应商的结束原因字符串给业务编排。 */
public enum LlmFinishReason {
    COMPLETE,
    TOKEN_LIMIT,
    TOOL_CALL,
    CONTENT_FILTERED,
    ERROR,
    UNKNOWN
}
