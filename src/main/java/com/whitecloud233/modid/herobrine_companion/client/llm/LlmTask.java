package com.whitecloud233.modid.herobrine_companion.client.llm;

/**
 * 可路由的 LLM 请求类型。每种类型可分配到不同的 provider（含备用 provider）。
 */
public enum LlmTask {
    MAIN_CHAT,
    SCOPED_CHAT,
    CROSS_SESSION,
    ACTOR_DIALOGUE,
    LOCALIZE,
    OBSERVE,
    SUMMARY
}
