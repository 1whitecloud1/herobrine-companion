package com.whitecloud233.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;

/**
 * 任务执行上下文（不可变）。只含 Hero 实体——任务通常逐 tick 驱动实体动作，
 * 不需要每 tick 抓取完整 {@code AgentFrame}（那会带来实体扫描开销）。
 *
 * <p>依赖倒置：任务环节只读取本上下文，不直接触碰无关系统。</p>
 */
public record AgentTaskContext(HeroEntity hero) {
}