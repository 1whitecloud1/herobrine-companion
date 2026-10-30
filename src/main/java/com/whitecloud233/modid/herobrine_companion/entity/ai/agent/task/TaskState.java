package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task;

/**
 * 任务状态（<b>唯一一套</b>，同时用于"单步返回结果"与"整条任务运行状态"，见交接 4.1）。
 *
 * <p>约定：{@link PlannedTask#tick} 只返回 {@link #RUNNING} / {@link #DONE} / {@link #FAILED} /
 * {@link #CANCELLED}；{@link #PAUSED} 由队列在失去控制权时设置，任务实现不需要自己返回它。</p>
 */
public enum TaskState {
    /** 已接受，尚未开始第一 tick。 */
    ACCEPTED,
    /** 仍在执行，等待下一 tick。 */
    RUNNING,
    /** 等待玩家确认 / 等待外部条件（不推进，也不消耗失败次数）。 */
    WAITING,
    /** 已暂停：保留目标与进度，恢复后继续。 */
    PAUSED,
    /** 成功完成，队列前进到下一个任务。 */
    DONE,
    /** 失败，触发重试 / 失败策略。 */
    FAILED,
    /** 已取消，不启动后续步骤。 */
    CANCELLED,
    /** 超过期限。 */
    EXPIRED;

    /** 是否为终态（不再推进）。 */
    public boolean isTerminal() {
        return this == DONE || this == FAILED || this == CANCELLED || this == EXPIRED;
    }

    /** 是否仍在进行中（占用队列）。 */
    public boolean isOngoing() {
        return this == ACCEPTED || this == RUNNING || this == WAITING || this == PAUSED;
    }

    /** 玩家可读名（状态页 / 回执）。 */
    public String displayName() {
        return switch (this) {
            case ACCEPTED -> "已接受";
            case RUNNING -> "执行中";
            case WAITING -> "等待条件";
            case PAUSED -> "已暂停";
            case DONE -> "成功";
            case FAILED -> "失败";
            case CANCELLED -> "已取消";
            case EXPIRED -> "已过期";
        };
    }
}
