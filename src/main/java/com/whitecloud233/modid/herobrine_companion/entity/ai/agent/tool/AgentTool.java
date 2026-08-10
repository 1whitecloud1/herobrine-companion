package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import java.util.List;

/**
 * 工具实现抽象（依赖倒置）。Agent 循环与工具注册表只依赖本接口，
 * 具体工具（读取 / 世界操作 / 将来接入的 command-skill 端口）各自独立实现。
 *
 * <p><b>安全约束（不可妥协）</b>：工具是 Agent 调用外部能力的唯一出口。实现必须遵守</p>
 * <ul>
 *   <li>参数一律先经 {@link #validate(AgentToolArgs)} 白名单 / 范围校验，非法输入返回失败而不是执行。</li>
 *   <li>默认无副作用；需要世界改动时走服务端权威路径。</li>
 *   <li>{@link #requiresConfirmation()} 返回 true 的工具，在未获玩家确认前必须拒绝执行（由注册表统一拦截）。</li>
 * </ul>
 */
public interface AgentTool {

    /** 唯一工具 ID（注册表按此白名单查找）。 */
    String id();

    /** 一句话说明（注入 LLM prompt 时使用）。 */
    String description();

    /** 工具类别（如 "world" / "player" / "info" / "meta"）。 */
    String category();

    /** 是否需要玩家确认后才执行。 */
    boolean requiresConfirmation();

    /** 参数声明（用于目录与 schema 生成）。 */
    List<AgentToolParameter> parameters();

    /** 校验参数：合法返回 ok，否则返回带原因的失败。 */
    AgentToolResult validate(AgentToolArgs args);

    /** 执行。仅在 {@link #validate} 通过且确认条件满足时由注册表调用。 */
    AgentToolResult execute(AgentToolContext context, AgentToolArgs args);

    /** 由元数据生成的目录描述。 */
    default AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(id(), description(), category(), requiresConfirmation(), parameters());
    }
}