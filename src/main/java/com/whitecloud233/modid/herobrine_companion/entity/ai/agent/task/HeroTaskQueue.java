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

/**
 * 任务队列（M3 核心）：把多步目标拆成的任务序列按序执行。
 *
 * <p>单一职责：调度 + tick 预算 + 失败策略 + 存档。任务本身不关心"自己在队列第几个"。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>单 {@link Deque}，队头 = 当前执行任务（pop 即前进到下一个）。</li>
 *   <li><b>tick 预算</b>：每 tick 最多切换 {@link #MAX_TRANSITIONS_PER_TICK} 个任务，
 *       避免一长串瞬时任务一次抽干卡 TPS。</li>
 *   <li><b>失败策略</b>：任务 FAILED → 尝试次数 +1；未达上限留在队头重启；
 *       达上限弹出该任务并累计连续失败；连续 {@link #MAX_CONSECUTIVE_FAILURES} 次 → 清空队列（不无限重试）。</li>
 *   <li><b>暂停</b>：战斗态不推进（避免与战斗 goal 抢移动）。</li>
 * </ul>
 */
public final class HeroTaskQueue {

    /** 每 tick 最多切换的任务数（预算）。 */
    public static final int MAX_TRANSITIONS_PER_TICK = 4;

    /** 连续失败达到该值则清空整个队列。 */
    public static final int MAX_CONSECUTIVE_FAILURES = 3;

    private static final String KEY_TASKS = "Tasks";

    private final AgentTaskRegistry registry;
    private final LinkedList<PlannedTask> tasks = new LinkedList<>();
    private final Deque<String> recentLog = new ArrayDeque<>();

    private int consecutiveFailures;

    public HeroTaskQueue(AgentTaskRegistry registry) {
        this.registry = registry == null ? new AgentTaskRegistry() : registry;
    }

    /** 入队：按 {@link PlannedTask#priority()} 从高到低插入（同优先级保持先到先排）。 */
    public void enqueue(PlannedTask task) {
        if (task == null) {
            return;
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
    }

    public void enqueueAll(List<PlannedTask> newTasks) {
        if (newTasks == null) {
            return;
        }
        for (PlannedTask task : newTasks) {
            enqueue(task);
        }
    }

    public void clear() {
        tasks.clear();
        consecutiveFailures = 0;
    }

    public boolean isIdle() {
        return tasks.isEmpty();
    }

    public int size() {
        return tasks.size();
    }

    /** 当前执行任务的进度描述；空闲返回空。 */
    public String currentProgress() {
        PlannedTask head = tasks.peekFirst();
        return head == null ? "" : head.progressText();
    }

    /** 最近日志（失败/中止留痕）。 */
    public List<String> recentLog() {
        List<String> result = new ArrayList<>(recentLog);
        return result;
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
        // 战斗态暂停：避免与战斗 goal 抢移动 / 攻击。
        if (hero.isBattleModeActive()) {
            return;
        }
        if (tasks.isEmpty()) {
            return;
        }

        int transitions = 0;
        while (!tasks.isEmpty() && transitions < MAX_TRANSITIONS_PER_TICK) {
            PlannedTask head = tasks.peekFirst();
            if (!head.isStarted()) {
                head.markStarted(hero.level().getGameTime());
                head.onStart(context);
            }

            TaskState state = head.tick(context);
            if (state == TaskState.RUNNING) {
                // 超时：超过任务自身 timeoutTicks 视为失败（走重试/放弃）。
                long timeout = head.timeoutTicks();
                if (timeout > 0 && hero.level().getGameTime() - head.startTick() > timeout) {
                    state = TaskState.FAILED;
                } else {
                    return;
                }
            }

            if (state == TaskState.DONE) {
                tasks.pollFirst();
                consecutiveFailures = 0;
                transitions++;
                continue;
            }

            // FAILED：尝试 + 1。
            head.incrementAttempts();
            if (head.attempts() < head.maxAttempts()) {
                head.resetStart(); // 下一 tick 重新 onStart 重试
                return;
            }
            tasks.pollFirst();
            consecutiveFailures++;
            log("[失败] " + head.id() + " " + head.progressText());
            transitions++;

            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                tasks.clear();
                log("[中止] 连续失败 " + consecutiveFailures + " 次，清空任务队列");
                return;
            }
        }
    }

    // ---- 存档（断线 / 重进恢复） ----

    public void write(CompoundTag tag) {
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
            list.add(entry);
        }
        tag.put(KEY_TASKS, list);
    }

    public void read(CompoundTag tag) {
        tasks.clear();
        consecutiveFailures = 0;
        if (!tag.contains(KEY_TASKS, Tag.TAG_LIST)) {
            return;
        }
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
            task.setAttempts(entry.getInt("attempts"));
            tasks.addLast(task);
        }
    }

    private void log(String line) {
        recentLog.addLast(line);
        while (recentLog.size() > 32) {
            recentLog.removeFirst();
        }
    }
}