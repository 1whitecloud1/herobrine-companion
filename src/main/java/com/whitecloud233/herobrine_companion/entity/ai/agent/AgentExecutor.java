package com.whitecloud233.herobrine_companion.entity.ai.agent;

/**
 * 执行器抽象（依赖倒置）。HeroAgent 只依赖本接口真正让"计划发生"。
 *
 * <p>安全约束：执行器是 Agent 改变世界的唯一出口。任何具体实现都必须遵守
 * "服务端权威 / 白名单 / 不越权"（见设计文档 4.4），本接口不作额外承诺，
 * 由实现自行满足。</p>
 */
public interface AgentExecutor {

    /**
     * 执行一份行动计划，返回一句话结果（供决策日志）。
     *
     * @param hero     Hero 实体（执行器的行动边界，唯一允许触碰实体的阶段）
     * @param frame    本次快照
     * @param plan     待执行的计划
     * @param request  若本次路由到工具，携带待处理的工具请求（否则为 null）
     * @return 执行结果描述；不改变世界时返回说明性文本
     */
    Outcome execute(com.whitecloud233.herobrine_companion.entity.HeroEntity hero,
                    AgentFrame frame, AgentPlan plan, AgentToolRequest request);

    /** 执行结果（不可变）。 */
    record Outcome(boolean acted, String description) {}
}