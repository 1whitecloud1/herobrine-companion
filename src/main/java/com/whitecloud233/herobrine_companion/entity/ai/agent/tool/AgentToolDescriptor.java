package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import java.util.List;

/**
 * 单个工具的目录元数据（不可变），用于统一工具清单与 LLM function-calling schema 生成。
 */
public record AgentToolDescriptor(
        String id,
        String description,
        String category,
        boolean requiresConfirmation,
        List<AgentToolParameter> parameters) {

    public AgentToolDescriptor {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    /** 该工具参数简要声明（供工具注册表 / 决策日志用）。 */
    public String parameterSummary() {
        if (parameters.isEmpty()) {
            return "(无参数)";
        }
        StringBuilder builder = new StringBuilder();
        for (AgentToolParameter parameter : parameters) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(parameter.name()).append(":").append(parameter.type())
                    .append(parameter.required() ? "" : "?");
        }
        return builder.toString();
    }
}