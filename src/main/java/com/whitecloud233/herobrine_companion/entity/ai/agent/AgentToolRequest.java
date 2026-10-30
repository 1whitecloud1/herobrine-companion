package com.whitecloud233.herobrine_companion.entity.ai.agent;

import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolArgs;

import java.util.UUID;

/**
 * 一次待处理的工具调用请求（不可变）。由外部（对话 / LLM / 调试入口）注入 Agent。
 *
 * @param toolId        目标工具 id（注册表白名单内）
 * @param args          参数
 * @param requesterUuid 请求者玩家 UUID（用于归属与后续确认）
 * @param playerApproved 是否已获该玩家确认
 * @param requestId     客户端生成的关联 ID（用于把执行结果经 {@code AgentToolResultPacket} 送回）
 * @param receivedTick  服务端受理请求时的 gameTick（用于请求期与迟到回执判定；0 表示未标记）
 */
public record AgentToolRequest(String toolId, AgentToolArgs args, UUID requesterUuid,
                               boolean playerApproved, UUID requestId, long receivedTick) {

    /**
     * 显式工具请求的服务端有效期（tick）：20 秒。
     *
     * <p>取值依据：客户端等待上限是 30 秒（{@code AIService.TOOL_RESULT_TIMEOUT_SECONDS}），
     * 服务端请求期必须短于它，才不会出现"客户端已放弃、服务端稍后又执行"的分离状态；
     * 同时 20 秒足以覆盖一次寻路抖动或短动画。</p>
     */
    public static final long REQUEST_TTL_TICKS = 20L * 20L;

    public AgentToolRequest {
        args = args == null ? AgentToolArgs.empty() : args;
    }

    /** 兼容旧调用点：未提供受理时刻时按 0 处理（{: 视为未标记，不做过期判定）。 */
    public AgentToolRequest(String toolId, AgentToolArgs args, UUID requesterUuid,
                            boolean playerApproved, UUID requestId) {
        this(toolId, args, requesterUuid, playerApproved, requestId, 0L);
    }

    /** 是否已超过服务端请求期（{@code currentGameTime} 需单调递增）。 */
    public boolean isExpired(long currentGameTime) {
        return receivedTick > 0L && currentGameTime - receivedTick > REQUEST_TTL_TICKS;
    }

    /** 补上受理时刻的副本。 */
    public AgentToolRequest stamped(long gameTime) {
        return receivedTick == gameTime ? this
                : new AgentToolRequest(toolId, args, requesterUuid, playerApproved, requestId, gameTime);
    }
}
