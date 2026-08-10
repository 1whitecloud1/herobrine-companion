package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

/**
 * 意图路由目标（M2）：分类器在"想要什么"之外，还要决定"走哪条通道处理"。
 *
 * <p>M1 只有"意图"，M2 增加"通道"，让不同性质的请求各归其位：世界行为交给既有 goal 系统，
 * 工具调用走统一工具注册表，对话交给 LLM 对话服务（M4 接入），无事则静默。</p>
 */
public enum AgentChannel {
    /** 世界行为：交由既有 goal 系统处理。 */
    WORLD_BEHAVIOR,
    /** 工具调用：交由 {@link com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolRegistry} 处理。 */
    TOOL_INVOCATION,
    /** 对话：交由对话服务（M4 接入）。 */
    DIALOGUE,
    /** 无事可做，保持静默。 */
    IDLE
}