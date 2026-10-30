package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;

/**
 * 自主通道执行器（交接 A1 之后收窄为单一职责）。
 *
 * <p>显式工具请求已由 {@link com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolRequestService}
 * 直接执行，<b>不再经过本类</b>；本类只回答一个问题：这一轮自主意图要不要动世界。
 * 答案是不动——世界行为交还既有 Goal 系统，Agent 只产出可读的行动说明，
 * 因此它不再持有工具注册表，也不会因为工具通道而分叉出第二条执行路径。</p>
 */
public final class DefaultAgentExecutor implements AgentExecutor {

    @Override
    public Outcome execute(HeroEntity hero, AgentFrame frame, AgentPlan plan, AgentToolRequest request) {
        // 自主通道不执行工具：即使调用方传了请求，也不在这里产生副作用（唯一入口见 AgentToolRequestService）。
        return new Outcome(false, "让位给既有层: " + plan.actionNote());
    }
}
