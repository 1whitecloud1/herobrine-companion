package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

/**
 * 行动计划：意图在规划阶段的产出（不可变），携带路由目标。
 */
public record AgentPlan(AgentIntention intention, AgentChannel channel, String rationale, String actionNote) {
}