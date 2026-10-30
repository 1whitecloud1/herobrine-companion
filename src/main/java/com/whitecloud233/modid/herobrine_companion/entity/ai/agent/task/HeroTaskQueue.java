package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 任务队列（多步目标的顺序执行器）。
 *
 * <p><b>单一职责</b>：调度 + tick 预算 + 失败/暂停/取消策略 + 存档。任务本身不关心"自己在队列第几个"。</p>
 *
 * <p><b>生命周期</b>（交接阶段 B）：每个任务都有 启动 → 运行 → （暂停 / 恢复）→ 终态，
 * 终态为 DONE / FAILED / CANCELLED / EXPIRED，终态一定调用一次 {@code onEnd} 释放资源。
 * 取消会清空后续步骤，<b>不启动</b>已取消目标的后继任务；暂停保留目标与进度。</p>
 *
 * <p><b>控制权</b>：队列不再自己往导航里写目标。移动类任务把目标交给
 * {@link TaskControl}，由 {@code HeroTaskControlGoal} 经 GoalSelector 取得 MOVE/LOOK；
 * 战斗 / 挑战抢走控制权时队列进入暂停（保留进度），结束后继续。</p>
 *
 * <p><b>容量</b>：队列入队有上限，满了返回 false（调用方回 busy），绝不静默覆盖旧目标。</p>
 */
public final class HeroTaskQueue {

    /** 每 tick 最多切换的任务数（预算）。 */
    public static final int MAX_TRANSITIONS_PER_TICK = 4;

    /** 连续失败达到该值则清空整个队列。 */
    public static final int MAX_CONSECUTIVE_FAILURES = 3;

    /** 队列容量：玩家任务与自主任务共享，满了返回 busy 而不是覆盖。 */
    public static final int CAPACITY = 16;

    /** 终态记录保留条数（完成 / 失败 / 取消 / 过期），供状态查询与迟到回执。 */
    public static final int TERMINAL_HISTORY = 24;

    private static final String KEY_TASKS = "Tasks";
    private static final String KEY_VERSION = "Version";

    /** 存档格式版本：1 = 只有 id/args/attempts（旧版），2 = 增加进度、目标、来源。 */
    public static final int SAVE_VERSION = 2;

    private final AgentTaskRegistry registry;
    private final LinkedList<PlannedTask> tasks = new LinkedList<>();
    private final Deque<TerminalTask> recentTerminals = new ArrayDeque<>();
    private final Deque<String> recentLog = new ArrayDeque<>();

    private int consecutiveFailures;
    private boolean paused;
    private String activeGoalId = "";

    public HeroTaskQueue(AgentTaskRegistry registry) {
        this.registry = registry == null ? new AgentTaskRegistry() : registry;
    }

    /** 一条已终结任务的记录（供状态页 / 迟到回执）。 */
    public record TerminalTask(String taskId, String goalId, TaskState state, String progress, long gameTime) {
        public String line() {
            return taskId + " -> " + state.displayName() + (progress.isBlank() ? "" : "（" + progress + "）");
        }
    }

    /**
     * 入队：按 {@link PlannedTask#priority()} 从高到低插入（同优先级保持先到先排）。
     *
     * @return false 表示队列已满，本次请求未被接受（调用方必须回 busy，不能覆盖旧目标）
     */
    public boolean enqueue(PlannedTask task) {
        if (task == null) {
            return false;
        }
        if (tasks.size() >= CAPACITY) {
            log("[拒绝] 队列已满（" + CAPACITY + "），未接受 " + task.id());
            return false;
        }
        int priority = task.priority();
        int index = 0;
        for (PlannedTask existing : tasks) {
            if (existing.priority() < priority) {
                break;
            }
            index++;
        }
        tasks.add(index, task);
        if (activeGoalId.isEmpty() && task.goalId() != null) {
            activeGoalId = task.goalId();
        }
        return true;
    }

    /**
     * 批量入队。
     *
     * @return false 表示中途已满，<b>未接受的任务不会被加入</b>（已加入的保持），调用方据此如实回执
     */
    public boolean enqueueAll(List<PlannedTask> newTasks) {
        if (newTasks == null) {
            return true;
        }
        for (PlannedTask task : newTasks) {
            if (!enqueue(task)) {
                return false;
            }
        }
        return true;
    }

    /** 清空队列（含取消语义：已取消目标不启动后续步骤）。 */
    public void clear() {
        tasks.clear();
        consecutiveFailures = 0;
        paused = false;
        activeGoalId = "";
    }

    /** 取消指定长目标的剩余步骤；返回被移除的步数。 */
    public int cancelGoal(String goalId) {
        return cancelGoal(null, goalId);
    }

    /**
     * 取消指定长目标的剩余步骤，并<b>正确收尾</b>；返回被移除的步数。
     *
     * <p><b>为什么必须有 context</b>：只把任务从列表里删掉并不会停止已经在跑的导航——
     * 移动控制权还挂在 {@code TaskControl} 上、{@code taskMovementActive} 仍为 true，
     * 结果是"取消了任务，Hero 还在往旧目标走"。取消必须同完成一样走
     * {@link PlannedTask#onEnd} 并释放控制权。</p>
     */
    public int cancelGoal(AgentTaskContext context, String goalId) {
        if (goalId == null || goalId.isBlank()) {
            return 0;
        }
        long gameTime = context != null && context.hero() != null && context.hero().level() != null
                ? context.hero().level().getGameTime() : 0L;
        int removed = 0;
        var it = tasks.iterator();
        while (it.hasNext()) {
            PlannedTask task = it.next();
            if (!goalId.equals(task.goalId())) {
                continue;
            }
            it.remove();
            // 只有真正启动过的任务才需要 onEnd（未启动的没占用任何运行时资源）。
            if (task.isStarted()) {
                task.onEnd(context, TaskState.CANCELLED);
            }
            rememberTerminal(task, TaskState.CANCELLED, gameTime);
            removed++;
        }
        // 控制权若还挂在这个目标上，必须收回，否则 Hero 继续朝旧目标走。
        if (context != null && context.hero() != null
                && context.hero().taskControl().isHeldBy(goalId)) {
            context.hero().releaseTaskMovement();
        }
        if (removed > 0) {
            log("[取消] " + goalId + " 移除 " + removed + " 个未完成步骤");
        }
        if (tasks.isEmpty()) {
            activeGoalId = "";
        }
        return removed;
    }

    /**
     * 清空整个队列并收尾所有已启动任务（含释放移动控制权）。
     *
     * <p>{@link #clear()} 只清数据结构，用于"没有英雄上下文"的场景；
     * 任何会真正中断运行中任务的路径都应该用本方法。</p>
     */
    public void clearWithCleanup(AgentTaskContext context) {
        long gameTime = context != null && context.hero() != null && context.hero().level() != null
                ? context.hero().level().getGameTime() : 0L;
        for (PlannedTask task : tasks) {
            if (task.isStarted()) {
                task.onEnd(context, TaskState.CANCELLED);
            }
            rememberTerminal(task, TaskState.CANCELLED, gameTime);
        }
        tasks.clear();
        consecutiveFailures = 0;
        paused = false;
        activeGoalId = "";
        if (context != null && context.hero() != null) {
            context.hero().releaseTaskMovement();
        }
    }

    /** 暂停整个队列（保留目标与进度）。 */
    public void pause() {
        paused = true;
    }

    /** 恢复整个队列。 */
    public void resume() {
        paused = false;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isIdle() {
        return tasks.isEmpty();
    }

    public int size() {
        return tasks.size();
    }

    /** 当前长目标 ID（空表示没有目标）。 */
    public String activeGoalId() {
        return activeGoalId;
    }

    /** 当前执行任务的进度描述；空闲返回空。 */
    public String currentProgress() {
        PlannedTask head = tasks.peekFirst();
        return head == null ? "" : head.progressText();
    }

    /** 当前任务的运行状态（空闲返回 null）。 */
    public TaskState currentState() {
        PlannedTask head = tasks.peekFirst();
        if (head == null) {
            return null;
        }
        if (paused) {
            return TaskState.PAUSED;
        }
        return head.isStarted() ? TaskState.RUNNING : TaskState.ACCEPTED;
    }

    /** 已完成 / 已终结的最近记录（新的在前）。 */
    public List<TerminalTask> recentTerminals(int max) {
        List<TerminalTask> result = new ArrayList<>();
        var it = recentTerminals.descendingIterator();
        while (it.hasNext() && result.size() < Math.max(0, max)) {
            result.add(it.next());
        }
        return result;
    }

    /** 最近日志（失败/中止/拒绝留痕）。 */
    public List<String> recentLog() {
        return new ArrayList<>(recentLog);
    }

    /**
     * 每 tick 推进一次。调用方（HeroAgent.tickTasks）保证已在服务端、存活、非挑战维度。
     */
    public void tick(AgentTaskContext context) {
        HeroEntity hero = context.hero();
        if (hero == null || hero.level() == null || hero.level().isClientSide) {
            return;
        }
        if (hero.isRemoved() || !hero.isAlive()) {
            return;
        }
        if (tasks.isEmpty()) {
            return;
        }
        long gameTime = hero.level().getGameTime();

        // 暂停：战斗 / 挑战 / 玩家显式暂停。保留目标与进度，释放移动控制权。
        if (paused || hero.isBattleModeActive() || hero.isChallengeActiveState()) {
            PlannedTask head = tasks.peekFirst();
            // onPause 只调一次：它要释放导航 / 动画 / chunk ticket，
            // 每 tick 重复调用会反复打断导航并制造无意义的资源释放。
            if (head != null && head.isStarted() && head.pausedAtTick() < 0L) {
                head.onPause(context);
                head.markPaused(gameTime);
            }
            return;
        }

        int transitions = 0;
        while (!tasks.isEmpty() && transitions < MAX_TRANSITIONS_PER_TICK) {
            PlannedTask head = tasks.peekFirst();
            if (!head.isStarted()) {
                head.markStarted(gameTime);
                head.onStart(context);
            } else if (head.pausedAtTick() >= 0L) {
                head.markResumed();
                head.onResume(context);
            }

            TaskState state = head.tick(context);

            if (state == TaskState.RUNNING) {
                // 超时：超过任务自身 timeoutTicks 视为失败（走重试/放弃）。
                long timeout = head.timeoutTicks();
                if (timeout > 0 && gameTime - head.startTick() > timeout) {
                    state = TaskState.EXPIRED;
                } else {
                    long noProgress = head.noProgressTicks();
                    if (noProgress > 0 && hero.taskControl().isHeldBy(holderKey(head))
                            && hero.taskControl().ticksSinceProgress(gameTime) > noProgress) {
                        // 无进展（被墙挡住 / 目标不可达）：按失败处理，交给重试策略，
                        // 而不是一直 RUNNING 把队列堵死。
                        log("[无进展] " + head.id() + " 超过 " + noProgress + " tick 未接近目标");
                        state = TaskState.FAILED;
                    } else {
                        return;
                    }
                }
            }

            if (state.isTerminal()) {
                finish(head, context, state, gameTime);
                transitions++;
                continue;
            }
            // PAUSED / WAITING：不推进，也不消耗失败次数。
            return;
        }
    }

    /** 收尾一个任务：调用 onEnd 释放资源、记日志、移除队头、按状态决定是否继续。 */
    private void finish(PlannedTask head, AgentTaskContext context, TaskState state, long gameTime) {
        head.onEnd(context, state);
        // onEnd 只释放自己持有的控制权，避免误伤后继任务。
        if (context.hero().taskControl().isHeldBy(holderKey(head))) {
            context.hero().releaseTaskMovement();
        }

        if (state == TaskState.DONE) {
            tasks.pollFirst();
            consecutiveFailures = 0;
            rememberTerminal(head, state, gameTime);
            return;
        }

        // 失败：尝试次数 +1；未达上限留在队头重启。
        head.incrementAttempts();
        if (state == TaskState.FAILED && head.attempts() < head.maxAttempts()) {
            head.resetStart();
            return;
        }
        tasks.pollFirst();
        rememberTerminal(head, state, gameTime);
        log("[" + state.displayName() + "] " + head.id() + " " + head.progressText());
        if (state == TaskState.FAILED) {
            consecutiveFailures++;
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                // 连续失败：取消同一长目标的剩余步骤，避免继续无意义重试。
                cancelGoal(context, activeGoalId);
                tasks.clear();
                activeGoalId = "";
                log("[中止] 连续失败 " + consecutiveFailures + " 次，清空任务队列");
            }
        }
    }

    private void rememberTerminal(PlannedTask task, TaskState state, long gameTime) {
        recentTerminals.addLast(new TerminalTask(task.id(), task.goalId() == null ? "" : task.goalId(),
                state, task.progressText(), gameTime));
        while (recentTerminals.size() > TERMINAL_HISTORY) {
            recentTerminals.removeFirst();
        }
        if (tasks.isEmpty()) {
            activeGoalId = "";
        }
    }

    /** 任务在 TaskControl 里的持有者键：优先长目标 ID，否则任务 ID。 */
    private static String holderKey(PlannedTask task) {
        String goalId = task.goalId();
        return goalId == null || goalId.isBlank() ? task.id() : goalId;
    }

    // ---- 存档（断线 / 重进恢复） ----

    public void write(CompoundTag tag) {
        tag.putInt(KEY_VERSION, SAVE_VERSION);
        ListTag list = new ListTag();
        for (PlannedTask task : tasks) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", task.id());
            CompoundTag argsTag = new CompoundTag();
            for (Map.Entry<String, String> arg : task.serializeArgs().entrySet()) {
                argsTag.putString(arg.getKey(), arg.getValue());
            }
            entry.put("args", argsTag);
            entry.putInt("attempts", task.attempts());
            // v2：恢复后继续按原时刻计算超时 / 无进展预算，并保留长目标归属。
            entry.putLong("startTick", task.startTick());
            if (task.goalId() != null && !task.goalId().isBlank()) {
                entry.putString("goalId", task.goalId());
            }
            list.add(entry);
        }
        tag.put(KEY_TASKS, list);
        tag.putBoolean("Paused", paused);
    }

    public void read(CompoundTag tag) {
        tasks.clear();
        consecutiveFailures = 0;
        paused = false;
        activeGoalId = "";
        if (!tag.contains(KEY_TASKS, Tag.TAG_LIST)) {
            return;
        }
        int version = tag.contains(KEY_VERSION) ? tag.getInt(KEY_VERSION) : 1;
        ListTag list = tag.getList(KEY_TASKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String id = entry.getString("id");
            CompoundTag argsTag = entry.getCompound("args");
            Map<String, String> args = new java.util.HashMap<>();
            for (String key : argsTag.getAllKeys()) {
                args.put(key, argsTag.getString(key));
            }
            PlannedTask task = registry.create(id, args);
            if (task == null) {
                log("[丢弃] 未知任务 id: " + id);
                continue;
            }
            if (task.discardOnRestore()) {
                // 结果不明确的不可逆步骤：不盲目重放（交接阶段 B），如实记一条并跳过。
                log("[丢弃] 恢复时不可安全重放: " + id);
                continue;
            }
            task.setAttempts(entry.getInt("attempts"));
            if (version >= 2) {
                task.setGoalId(entry.contains("goalId") ? entry.getString("goalId") : "");
                task.setStartTick(entry.contains("startTick") ? entry.getLong("startTick") : -1L);
            }
            tasks.addLast(task);
        }
        if (!tasks.isEmpty()) {
            PlannedTask head = tasks.peekFirst();
            activeGoalId = head.goalId() == null ? "" : head.goalId();
        }
        // 默认恢复策略：重进世界后先暂停，等目标 / 物品 / 维度重新核对再继续（交接阶段 B）。
        // 状态页会显示"已暂停"，玩家用 agent_task resume 或命令确认后继续，不会盲目重放。
        paused = !tasks.isEmpty();
    }

    private void log(String line) {
        recentLog.addLast(line);
        while (recentLog.size() > 32) {
            recentLog.removeFirst();
        }
    }
}
