package com.whitecloud233.herobrine_companion.entity.ai.agent;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;

/**
 * 透传执行器（M1 参考实现）：不做任何会改变世界的动作，仅返回说明性结果。
 *
 * <p>保留作为"零行为"基线与测试参照；M2 起默认执行器为 {@link DefaultAgentExecutor}。</p>
 */
public final class PassThroughAgentExecutor implements AgentExecutor {

    @Override
    public Outcome execute(HeroEntity hero, AgentFrame frame, AgentPlan plan, AgentToolRequest request) {
        return new Outcome(false, "透传: " + plan.actionNote());
    }
}