package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

/**
 * 工具参数的声明（不可变）：名称 / 是否必填 / 类型 / 说明。
 * 用于工具目录元数据与 LLM function-calling schema 生成。
 */
public record AgentToolParameter(String name, boolean required, String type, String description) {

    public static AgentToolParameter required(String name, String type, String description) {
        return new AgentToolParameter(name, true, type, description);
    }

    public static AgentToolParameter optional(String name, String type, String description) {
        return new AgentToolParameter(name, false, type, description);
    }
}