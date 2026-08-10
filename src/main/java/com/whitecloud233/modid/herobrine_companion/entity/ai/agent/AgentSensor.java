package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

/**
 * 传感器抽象（依赖倒置）。HeroAgent 只依赖本接口收集感知，不依赖任何具体传感器实现。
 *
 * <p>M2 起可自由新增/替换传感器（如对话意图传感器、LLM 语义传感器），核心循环无需改动。</p>
 *
 * <p>单一职责：每个实现只负责"从某一类来源提取观测并写入感知桶"。</p>
 */
public interface AgentSensor {

    /**
     * 基于当前快照向感知桶写入观测。
     *
     * @param frame 本次循环开始时捕获的状态快照（只读）
     * @param sink  感知桶（只写）
     */
    void collect(AgentFrame frame, AgentPerceptionCollector sink);

    /** 传感器来源标识，用于决策日志可读性。 */
    String source();
}