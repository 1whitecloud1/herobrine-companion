package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import net.minecraft.network.chat.Component;

/**
 * 工具调用结果（不可变）。
 *
 * @param status  结果状态：成功 / 失败 / 需玩家确认
 * @param message 结果/错误描述（中文，供 LLM 上下文、决策日志与审计；不作玩家呈现）
 * @param display 玩家可见的本地化组件（聊天 / 确认屏渲染）；为 null 时回退到 {@code message} 字面量
 */
public record AgentToolResult(Status status, String message, Component display) {

    /** 结果状态（P3 起三态：成功 / 失败 / 需玩家确认）。 */
    public enum Status {
        OK,
        FAIL,
        CONFIRMATION_REQUIRED
    }

    public static AgentToolResult ok(String message) {
        return new AgentToolResult(Status.OK, message == null ? "ok" : message, null);
    }

    public static AgentToolResult ok(String message, Component display) {
        return new AgentToolResult(Status.OK, message == null ? "ok" : message, display);
    }

    public static AgentToolResult fail(String message) {
        return new AgentToolResult(Status.FAIL, message == null ? "failed" : message, null);
    }

    public static AgentToolResult fail(String message, Component display) {
        return new AgentToolResult(Status.FAIL, message == null ? "failed" : message, display);
    }

    /** P3：工具需要玩家确认后才执行（注册表在确认分支返回）。 */
    public static AgentToolResult confirmationRequired(String message, Component display) {
        return new AgentToolResult(Status.CONFIRMATION_REQUIRED,
                message == null ? "confirmation_required" : message, display);
    }

    public boolean ok() {
        return status == Status.OK;
    }

    public boolean failed() {
        return status == Status.FAIL;
    }

    public boolean confirmationRequired() {
        return status == Status.CONFIRMATION_REQUIRED;
    }

    /**
     * 玩家呈现用的组件：优先本地化 {@code display}；缺失时回退到 {@code message} 字面量
     * （动态文本 / 外部 mod 文案等无法预定义翻译键的场景）。
     */
    public Component displayComponent() {
        return display != null ? display : Component.literal(message == null ? "" : message);
    }
}
