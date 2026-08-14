package com.whitecloud233.herobrine_companion.entity.ai.agent.task;

import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * 计划任务抽象基类。每个任务是一个可独立执行、可序列化的动作单元；
 * {@link HeroTaskQueue} 负责调度（tick 预算 / 失败 / 存档），任务自身只负责"一步动作"。
 *
 * <p>约束：{@link #tick(AgentTaskContext)} 必须在单 tick 内自限工作量（队列统一做切换预算），
 * 长流程请拆成多个任务串行。</p>
 */
public abstract class PlannedTask {

    private final String id;
    private final Map<String, String> args;
    private int attempts;
    private boolean started;
    private long startTick = -1;

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

    /** 首次进入执行前的初始化钩子（默认空）。 */
    public void onStart(AgentTaskContext context) {
    }

    /** 每 tick 推进一次。必须自限工作量。 */
    public abstract TaskState tick(AgentTaskContext context);

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

    /** 持久化参数（默认返回构建参数；需要附加运行时字段时覆盖）。 */
    public Map<String, String> serializeArgs() {
        return args;
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