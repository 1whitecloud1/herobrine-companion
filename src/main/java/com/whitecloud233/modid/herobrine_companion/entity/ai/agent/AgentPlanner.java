package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

/**
 * 规划器抽象（依赖倒置）。HeroAgent 只依赖本接口把意图翻译成行动计划。
 *
 * <p>M1 默认实现只产出"说明性"计划（让位给既有 goal 系统），不引入新行为；
 * M3 将在其上接真正的任务队列。</p>
 */
public interface AgentPlanner {

    AgentPlan plan(AgentFrame frame, AgentIntentionClassifier.Classification classification);
}