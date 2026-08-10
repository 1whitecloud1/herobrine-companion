package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task;

/**
 * 任务执行状态。
 */
public enum TaskState {
    /** 仍在执行，等待下一 tick。 */
    RUNNING,
    /** 成功完成，队列前进到下一个任务。 */
    DONE,
    /** 失败，触发重试 / 失败策略。 */
    FAILED
}