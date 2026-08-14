package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

/**
 * 工具执行上下文：工具实现可用的最小只读事实集（依赖倒置，不直接触碰实体具体类）。
 *
 * <p>单一职责：只在工具被调用时构建一次，之后完全只读。危险能力一律不在上下文中暴露，
 * 工具实现如需世界改动，必须通过明确、受限的路径（本包遵循"服务端权威"约束）。</p>
 *
 * @param frame         本次循环的状态快照（只读）
 * @param hero          Hero 实体（只读）
 * @param requesterUuid 本次工具调用的请求者玩家 UUID（可为 null；需要玩家上下文的动作工具由此解析）
 */
public record AgentToolContext(
        com.whitecloud233.herobrine_companion.entity.ai.agent.AgentFrame frame,
        com.whitecloud233.herobrine_companion.entity.HeroEntity hero,
        java.util.UUID requesterUuid) {
}