package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task;

import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * 计划任务抽象基类。每个任务是一个可独立执行、可序列化的动作单元；
 * {@link HeroTaskQueue} 负责调度（tick 预算 / 失败 / 暂停 / 存档），任务自身只负责"一步动作"。
 *
 * <p><b>控制权约定</b>（交接阶段 B）：任务<b>不直接长期写导航</b>。移动类任务把目标交给
 * {@link com.whitecloud233.modid.herobrine_companion.entity.ai.goal.HeroTaskControlGoal}
 * ——它接入 GoalSelector 的 MOVE/LOOK 互斥，由游戏本体仲裁"谁在走"。
 * 任务只报告"到没到、通不通过"，这样跟随 / 送礼 / 战斗不会和任务来回抢导航。</p>
 *
 * <p><b>生命周期</b>：{@link #onStart} → 若干次 {@link #tick} → 终态（DONE/FAILED/CANCELLED/EXPIRED）。
 * 失败重试会重新 {@link #onStart}；暂停 / 恢复不重置进度。</p>
 */
public abstract class PlannedTask {

    private final String id;
    private final Map<String, String> args;
    private int attempts;
    private boolean started;
    private long startTick = -1;
    /** 本任务所属的长目标 ID（可空；同一目标的多步任务共享它）。 */
    private String goalId;
    private long pausedAtTick = -1;

    protected PlannedTask(String id, Map<String, String> args) {
        this.id = id;
        this.args = args == null ? Map.of() : Map.copyOf(args);
    }

    /** 任务类型 id（持久化/注册表用）。 */
    public final String id() {
        return id;
    }

    public final Map<String, String> args() {
        return args;
    }

    /** 所属长目标 ID（可空）。 */
    public final String goalId() {
        return goalId;
    }

    public final void setGoalId(String goalId) {
        this.goalId = goalId;
    }

    /** 首次进入执行前的初始化钩子（默认空）。 */
    public void onStart(AgentTaskContext context) {
    }

    /** 每 tick 推进一次。必须自限工作量。 */
    public abstract TaskState tick(AgentTaskContext context);

    /**
     * 暂停钩子：释放导航 / 动画 / chunk ticket 等运行时资源，但<b>保留目标与进度</b>。
     * 默认释放移动控制权。
     */
    public void onPause(AgentTaskContext context) {
        releaseMovement(context);
    }

    /** 恢复钩子：重新申请移动控制权；默认不做额外动作。 */
    public void onResume(AgentTaskContext context) {
    }

    /**
     * 结束钩子（完成 / 失败 / 取消 / 过期都会调用一次）：释放导航与临时资源。
     * 任务实现如持有 chunk ticket、动画或借用中的对象，必须在这里归还。
     */
    public void onEnd(AgentTaskContext context, TaskState terminalState) {
        releaseMovement(context);
    }

    /** 释放移动控制权：停导航并把任务控制标记收回。 */
    protected final void releaseMovement(AgentTaskContext context) {
        if (context == null || context.hero() == null) {
            return;
        }
        context.hero().releaseTaskMovement();
    }

    /** 可读进度描述（决策日志 / 调试面板）。 */
    public abstract String progressText();

    /** 单任务最大尝试次数；达到后该任务被放弃（默认 1 = 失败即放弃）。 */
    public int maxAttempts() {
        return 1;
    }

    /** 任务优先级（数值越大越先执行；默认 0 = 普通）。队列按此插入。 */
    public int priority() {
        return 0;
    }

    /** 单任务超时（tick）；超过即按失败走重试/放弃。0 = 无超时（默认）。 */
    public long timeoutTicks() {
        return 0L;
    }

    /**
     * 无进展判定预算（tick）：位置连续这么久没有实质性前进就判 FAILED（0 = 不判定）。
     * 默认继承 {@link #timeoutTicks()} 的一半，避免"卡住但没超时"长期占用队列。
     */
    public long noProgressTicks() {
        long timeout = timeoutTicks();
        return timeout > 0L ? Math.max(20L, timeout / 2L) : 0L;
    }

    /** 持久化参数（默认返回构建参数；需要附加运行时字段时覆盖）。 */
    public Map<String, String> serializeArgs() {
        return args;
    }

    /** 是否不支持存档恢复（默认支持）。不可逆或依赖运行时引用的任务可覆盖为 true，恢复时被丢弃并记录。 */
    public boolean discardOnRestore() {
        return false;
    }

    // ---- 队列调度辅助（包内访问） ----

    final boolean isStarted() {
        return started;
    }

    final void markStarted(long gameTime) {
        started = true;
        startTick = gameTime;
    }

    /** 失败重试前重置启动状态，使下一 tick 重新执行 onStart。 */
    final void resetStart() {
        started = false;
        startTick = -1;
    }

    final int attempts() {
        return attempts;
    }

    final long startTick() {
        return startTick;
    }

    final void incrementAttempts() {
        attempts++;
    }

    /** 存档恢复时回填已尝试次数。 */
    final void setAttempts(int attempts) {
        this.attempts = Math.max(0, attempts);
    }

    /** 存档恢复时回填启动时刻（恢复后超时 / 无进展预算继续按原时刻计算）。 */
    final void setStartTick(long tick) {
        this.started = tick >= 0L;
        this.startTick = tick;
    }

    final long pausedAtTick() {
        return pausedAtTick;
    }

    final void markPaused(long gameTime) {
        this.pausedAtTick = gameTime;
    }

    final void markResumed() {
        this.pausedAtTick = -1L;
    }

    // ---- 参数类型化读取 ----

    protected final String getString(String key) {
        return args.getOrDefault(key, "");
    }

    protected final int getInt(String key, int fallback) {
        String value = args.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    protected final long getLong(String key, long fallback) {
        String value = args.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    protected final double getDouble(String key, double fallback) {
        String value = args.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    protected final BlockPos getBlockPos(String key) {
        String value = args.get(key);
        if (value == null) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    protected final String posToString(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
