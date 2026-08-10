package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.PlannedTask;

import java.util.List;

/**
 * 任务规划器抽象（依赖倒置）。HeroAgent 只依赖本接口把"意图 + 路由"分解成可执行任务序列。
 *
 * <p>M3 提供规则实现 {@link DefaultAgentTaskPlanner}；后续可换 LLM 驱动实现，
 * 或让规划器输出结构化计划再由 LLM 逐条确认。</p>
 */
public interface AgentTaskPlanner {

    /**
     * 把一个（可能为空的）任务序列交给队列执行。
     *
     * @param frame            本次快照（用于选定目标点等）
     * @param classification   已分类的意图 / 路由 / 理由
     * @return 有序任务列表；无需执行时返回空表
     */
    List<PlannedTask> plan(AgentFrame frame, AgentIntentionClassifier.Classification classification);
}