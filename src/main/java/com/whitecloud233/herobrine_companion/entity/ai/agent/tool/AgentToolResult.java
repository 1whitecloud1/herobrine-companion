package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import net.minecraft.network.chat.Component;

/**
 * 工具调用结果（不可变）。
 *
 * <p><b>状态与原因分开</b>（交接 A3）：{@link Status#WAITING} 表示"已受理、等玩家确认"，
 * 既不是成功也不是失败；{@link Status#FAIL} 再用 {@link Cause} 区分"软件流程类失败"
 * （参数错误 / 未知工具 / 队列满 / 执行异常）与"游戏内动作失败"（世界事实）。
 * 反思与心智反馈只消费世界事实，不再把等待审批、参数被拒记成"被拒绝的经历"。</p>
 *
 * @param status  结果状态：成功 / 失败 / 等待确认
 * @param message 结果/错误描述（中文，供 LLM 上下文、决策日志与审计；不作玩家呈现）
 * @param display 玩家可见的本地化组件（聊天 / 确认屏渲染）；为 null 时回退到 {@code message} 字面量
 * @param cause   失败/等待的原因分类（成功为 {@link Cause#NONE}）
 */
public record AgentToolResult(Status status, String message, Component display, Cause cause) {

    /** 结果状态。 */
    public enum Status {
        OK,
        FAIL,
        /** 已受理，正等待玩家确认（不执行、不记失败）。 */
        WAITING
    }

    /** 结果原因分类：决定"要不要写进世界经历"。 */
    public enum Cause {
        /** 成功。 */
        NONE,
        /** 等待玩家确认。 */
        WAITING,
        /** 玩家拒绝。 */
        REJECTED,
        /** 审批或请求过期。 */
        EXPIRED,
        /** 参数缺失 / 类型错误 / 越界（软件侧）。 */
        INVALID_ARGS,
        /** 未注册的工具 ID（软件侧）。 */
        UNKNOWN_TOOL,
        /** 执行期异常（软件侧）。 */
        INTERNAL,
        /** 队列 / 审批队列已满。 */
        CAPACITY,
        /** 游戏内动作真的失败了（世界事实）。 */
        WORLD
    }

    public AgentToolResult {
        message = message == null ? "" : message;
        cause = cause == null ? Cause.WORLD : cause;
    }

    public static AgentToolResult ok(String message) {
        return new AgentToolResult(Status.OK, message == null ? "ok" : message, null, Cause.NONE);
    }

    public static AgentToolResult ok(String message, Component display) {
        return new AgentToolResult(Status.OK, message == null ? "ok" : message, display, Cause.NONE);
    }

    /** 游戏内动作失败（默认原因；世界事实，计入经历）。 */
    public static AgentToolResult fail(String message) {
        return fail(message, null, Cause.WORLD);
    }

    public static AgentToolResult fail(String message, Component display) {
        return fail(message, display, Cause.WORLD);
    }

    public static AgentToolResult fail(String message, Component display, Cause cause) {
        return new AgentToolResult(Status.FAIL, message == null ? "failed" : message, display, cause);
    }

    /** 软件流程类失败：参数/未知工具/容量/内部异常，不进入世界经历。 */
    public static AgentToolResult denied(String message, Component display, Cause cause) {
        Cause safe = cause == null || cause == Cause.WORLD || cause == Cause.NONE ? Cause.INVALID_ARGS : cause;
        return new AgentToolResult(Status.FAIL, message == null ? "failed" : message, display, safe);
    }

    /** 需要玩家确认后才执行（注册表在确认分支返回）。 */
    public static AgentToolResult confirmationRequired(String message, Component display) {
        return new AgentToolResult(Status.WAITING,
                message == null ? "confirmation_required" : message, display, Cause.WAITING);
    }

    /** 审批被玩家拒绝。 */
    public static AgentToolResult rejected(String message) {
        return new AgentToolResult(Status.FAIL, message == null ? "rejected" : message, null, Cause.REJECTED);
    }

    /** 审批 / 请求已过期。 */
    public static AgentToolResult expired(String message) {
        return new AgentToolResult(Status.FAIL, message == null ? "expired" : message, null, Cause.EXPIRED);
    }

    public boolean ok() {
        return status == Status.OK;
    }

    public boolean failed() {
        return status == Status.FAIL;
    }

    public boolean confirmationRequired() {
        return status == Status.WAITING || cause == Cause.WAITING;
    }

    /** 软件流程类失败：不应记成 Hero 的世界经历。 */
    public boolean softwareFailure() {
        return status == Status.FAIL && isSoftwareCause(cause);
    }

    /** 世界事实类失败：可以写进经历。 */
    public boolean worldFailure() {
        return status == Status.FAIL && cause == Cause.WORLD;
    }

    public static boolean isSoftwareCause(Cause cause) {
        return cause == Cause.INVALID_ARGS || cause == Cause.UNKNOWN_TOOL
                || cause == Cause.INTERNAL || cause == Cause.CAPACITY;
    }

    /**
     * 玩家呈现用的组件：优先本地化 {@code display}；缺失时回退到 {@code message} 字面量
     * （动态文本 / 外部 mod 文案等无法预定义翻译键的场景）。
     */
    public Component displayComponent() {
        return display != null ? display : Component.literal(message == null ? "" : message);
    }
}
