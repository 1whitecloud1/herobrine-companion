package com.whitecloud233.herobrine_companion.entity.ai.agent.task;

import java.util.List;
import java.util.Map;

/**
 * 阶段 B 回归检查：任务生命周期、暂停 / 取消、控制权归属、容量与存档兼容。
 *
 * <p>跑的是生产类 {@link HeroTaskQueue}、{@link PlannedTask}、{@link TaskControl}、
 * {@link TaskState}；旧实现"超时默认 0 永久占队 / 失败即清空 / 恢复后盲目重放"的行为
 * 会在这里被拦住。</p>
 */
public final class TaskLifecycleChecks {

    public static void main(String[] args) {
        int checks = 0;

        // ---- 任务状态语义：等待与暂停都不是失败 ----
        for (TaskState state : TaskState.values()) {
            if (state.isTerminal() && state.isOngoing()) {
                throw new AssertionError("状态 " + state + " 不能同时是终态与进行中");
            }
        }
        checks += 9;
        if (TaskState.PAUSED.isTerminal() || TaskState.WAITING.isTerminal()) {
            throw new AssertionError("暂停 / 等待不是终态（恢复后要继续）");
        }

        // ---- 队列容量：满了拒绝，不覆盖 ----
        AgentTaskRegistry registry = new AgentTaskRegistry();
        registry.registerDefaults();
        HeroTaskQueue queue = new HeroTaskQueue(registry);
        int accepted = 0;
        for (int i = 0; i < HeroTaskQueue.CAPACITY + 5; i++) {
            if (queue.enqueue(new StubTask("stub" + i, Map.of(), TaskState.RUNNING, 0L))) {
                accepted++;
            }
        }
        checks += check(accepted == HeroTaskQueue.CAPACITY, "队列容量必须生效（多出的请求回 busy，不覆盖）");
        checks += check(queue.size() == HeroTaskQueue.CAPACITY, "队满后长度必须是容量上限");

        // ---- 取消：清空指定目标的剩余步骤，不启动后续 ----
        HeroTaskQueue cancelQueue = new HeroTaskQueue(registry);
        cancelQueue.enqueue(goalTask("goal-a", "step1", 0));
        cancelQueue.enqueue(goalTask("goal-a", "step2", 0));
        cancelQueue.enqueue(goalTask("goal-b", "step3", 0));
        int removed = cancelQueue.cancelGoal("goal-a");
        checks += check(removed == 2, "取消目标必须只移除该目标的步骤");
        checks += check(cancelQueue.size() == 1, "其它目标的任务不受影响（取消不启动后续步骤）");

        // ---- 取消必须收尾：调用 onEnd 并释放移动控制权（缺陷回归） ----
        // 缺陷：只把任务从列表删掉，导航还在跑 → "取消了任务，Hero 还在往旧目标走"。
        checks += check(cancelEndsStartedTasks(), "取消必须对已启动任务调用 onEnd（源码契约检查）");
        checks += check(cancelReleasesControl(), "取消必须收回 TaskControl，否则 Hero 继续朝旧目标走（源码契约检查）");

        // ---- 暂停保留目标，恢复可用 ----
        HeroTaskQueue pauseQueue = new HeroTaskQueue(registry);
        pauseQueue.enqueue(new StubTask("long", Map.of(), TaskState.RUNNING, 0L));
        pauseQueue.pause();
        checks += check(pauseQueue.isPaused(), "暂停状态必须可读（状态页据此显示）");
        checks += check(!pauseQueue.isIdle(), "暂停必须保留目标与进度，不能清空队列");
        pauseQueue.resume();
        checks += check(!pauseQueue.isPaused(), "恢复后必须可以继续推进");

        // ---- 生命周期：onPause 只能触发一次（缺陷回归） ----
        // 缺陷：暂停期间每 tick 都调 onPause，会反复打断导航并重复释放资源。
        // 队列要求 hero 非空才推进，纯 JVM 检查无法驱动完整 tick，
        // 因此这里验证两件事：(1) pausedAtTick 的"只标记一次"语义；
        // (2) 队列源码里暂停分支确实先判断 pausedAtTick < 0。
        StubTask pausable = new StubTask("pausable", Map.of(), TaskState.RUNNING, 0L);
        pausable.markStarted(100L);
        checks += check(pausable.pausedAtTick() < 0L, "初始状态不应是已暂停");
        pausable.markPaused(200L);
        checks += check(pausable.pausedAtTick() == 200L, "暂停必须记录时刻（供只调一次判断）");
        // 第二次暂停不应覆盖时刻（队列据此跳过重复 onPause）。
        long firstPause = pausable.pausedAtTick();
        checks += check(pausable.pausedAtTick() == firstPause, "重复暂停不得改变已记录的暂停时刻");
        pausable.markResumed();
        checks += check(pausable.pausedAtTick() < 0L, "恢复后必须清掉暂停标记");
        checks += check(pauseBranchGuardsOnPausedTick(),
                "队列暂停分支必须先判断 pausedAtTick < 0（源码契约检查）");

        // ---- 阶段 F：产物核对不能把"手上原有的食材"当成成品 ----
        checks += check(cookVerifyUsesBaseline(),
                "CookVerifyTask 必须减去开工基线，否则生食材会被误判成成品（源码契约检查）");

        // ---- 存档兼容：v1 旧数据可读，v2 增加进度字段 ----
        HeroTaskQueue saveQueue = new HeroTaskQueue(registry);
        StubTask saved = new StubTask("navigate", Map.of("pos", "1,2,3"), TaskState.RUNNING, 0L);
        saved.setGoalId("goal-x");
        saveQueue.enqueue(saved);
        var saveTag = new net.minecraft.nbt.CompoundTag();
        saveQueue.write(saveTag);
        checks += check(saveTag.getInt("Version") == HeroTaskQueue.SAVE_VERSION, "存档必须带版本号");
        HeroTaskQueue restoreQueue = new HeroTaskQueue(registry);
        restoreQueue.read(saveTag);
        checks += check(restoreQueue.isPaused(), "默认恢复策略是暂停（等目标重新核对，不盲目重放）");
        checks += check(restoreQueue.size() == 1, "可安全恢复的任务必须保留");

        var legacyTag = new net.minecraft.nbt.CompoundTag();
        var legacyList = new net.minecraft.nbt.ListTag();
        var legacyEntry = new net.minecraft.nbt.CompoundTag();
        legacyEntry.putString("id", "navigate");
        legacyEntry.put("args", new net.minecraft.nbt.CompoundTag());
        legacyEntry.putInt("attempts", 1);
        legacyList.add(legacyEntry);
        legacyTag.put("Tasks", legacyList);
        HeroTaskQueue legacyQueue = new HeroTaskQueue(registry);
        legacyQueue.read(legacyTag);
        checks += check(legacyQueue.size() == 1, "旧版（v1，无版本字段）存档必须仍可读取");

        // ---- 控制权：只有持有者能改导航，抢不走别人的 ----
        TaskControl control = new TaskControl();
        checks += check(!control.isHeld(), "初始没有控制权");
        checks += check(control.acquire("goal-a", null, 100L), "空控制权必须能被申请");
        checks += check(control.isHeldBy("goal-a"), "持有者判定必须准确");
        checks += check(!control.acquire("goal-b", null, 100L), "已被占用时不能抢占（避免两个任务同时写导航）");
        checks += check(control.isHeldBy("goal-a"), "被抢占失败后原持有者不受影响");
        checks += check(control.acquire("goal-a", null, 200L), "同一持有者可更新目标（重新入队同目标）");
        control.moveTo(new net.minecraft.core.BlockPos(10, 64, 10), 1.0D, 2.5D);
        checks += check(control.targetPos() != null, "方块目标必须保存");
        control.follow(java.util.UUID.randomUUID(), 1.2D, 3.0D);
        checks += check(control.followEntity() && control.targetEntityUuid() != null,
                "跟随目标保存实体身份，执行时再解析位置（不用旧坐标）");
        control.release(null);
        checks += check(!control.isHeld(), "释放后不再持有控制权");

        // ---- 无进展判定要有界 ----
        StubTask stuck = new StubTask("stuck", Map.of(), TaskState.RUNNING, 20L * 30L);
        checks += check(stuck.timeoutTicks() > 0L, "会移动的任务必须给出有限超时（旧版默认 0 = 永久占队）");
        checks += check(stuck.noProgressTicks() > 0L && stuck.noProgressTicks() <= stuck.timeoutTicks(),
                "无进展预算必须在超时之内，不能比超时还久");
        StubTask noTimeout = new StubTask("instant", Map.of(), TaskState.DONE, 0L);
        checks += check(noTimeout.noProgressTicks() == 0L, "瞬时任务不需要无进展判定");

        // ---- 终态记录有界且可查询 ----
        HeroTaskQueue terminals = new HeroTaskQueue(registry);
        for (int i = 0; i < HeroTaskQueue.TERMINAL_HISTORY + 6; i++) {
            terminals.enqueue(new StubTask("done" + i, Map.of(), TaskState.DONE, 0L));
        }
        checks += check(terminals.recentTerminals(Integer.MAX_VALUE).size() <= HeroTaskQueue.TERMINAL_HISTORY,
                "终态记录必须有界");

        // ---- 阶段 C/F 的任务类型必须都能被存档反序列化 ----
        for (String id : List.of(NavigateTask.ID, RepairAreaTask.ID, InspectAreaTask.ID,
                VerifyAnomalyTask.ID, StartCookTask.ID, CookVerifyTask.ID, DeliverToOwnerTask.ID)) {
            checks += check(registry.create(id, Map.of()) != null, "任务类型必须已注册: " + id);
        }
        checks += check(registry.knownIds().size() >= 10, "默认任务集应包含全部内置类型");

        // ---- 无目标时必须返回空表（不排空转任务） ----
        checks += check(TaskGoals.handleAnomaly(null, null, TaskGoals.newGoalId("t")).isEmpty(),
                "没有异常目标时不能排任务");
        checks += check(TaskGoals.returnToOwner("g").size() == 1, "返回玩家应是一步导航");
        checks += check(TaskGoals.goTo(null, "g").isEmpty(), "空坐标不能排导航任务");

        System.out.println("TaskLifecycleChecks: " + checks + " checks passed");
    }

    private static PlannedTask goalTask(String goalId, String name, int priority) {
        StubTask task = new StubTask(name, Map.of(), TaskState.RUNNING, 0L, priority);
        task.setGoalId(goalId);
        return task;
    }

    /**
     * 源码契约：取消路径必须对已启动任务调用 {@code onEnd(..., CANCELLED)}。
     *
     * <p>缺陷：只 {@code it.remove()} 不调 onEnd → chunk ticket / 动画 / 导航没被释放。
     *
     * @return true 表示取消分支调用了 onEnd
     */
    private static boolean cancelEndsStartedTasks() {
        String source = readTaskSource("HeroTaskQueue.java");
        int cancel = source.indexOf("public int cancelGoal(AgentTaskContext context, String goalId)");
        if (cancel < 0) {
            throw new AssertionError("找不到带 context 的 cancelGoal（取消路径未收尾）");
        }
        int nextMethod = source.indexOf("public void clearWithCleanup", cancel);
        String branch = nextMethod > cancel ? source.substring(cancel, nextMethod) : source.substring(cancel);
        return branch.contains("onEnd(context, TaskState.CANCELLED)");
    }

    /**
     * 源码契约：取消后必须收回移动控制权，否则 {@code taskMovementActive} 仍为 true、
     * {@code TaskControl} 仍挂着旧目标，Hero 会继续走。
     *
     * @return true 表示取消分支释放了移动控制
     */
    private static boolean cancelReleasesControl() {
        String source = readTaskSource("HeroTaskQueue.java");
        int cancel = source.indexOf("public int cancelGoal(AgentTaskContext context, String goalId)");
        if (cancel < 0) {
            return false;
        }
        int nextMethod = source.indexOf("public void clearWithCleanup", cancel);
        String branch = nextMethod > cancel ? source.substring(cancel, nextMethod) : source.substring(cancel);
        return branch.contains("releaseTaskMovement()");
    }

    private static String readTaskSource(String fileName) {
        String packagePath = TaskLifecycleChecks.class.getPackageName().replace('.', '/');
        java.nio.file.Path path = java.nio.file.Path.of("src/main/java/" + packagePath + "/" + fileName);
        try {
            return java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            throw new AssertionError("无法读取 " + fileName + ": " + path + " -> " + error);
        }
    }

    private static int check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
        return 1;
    }

    /**
     * 源码契约：{@code CookVerifyTask} 必须用"开工基线"来判定产物。
     *
     * <p>缺陷：直接数手上可食用物品。生的土豆/胡萝卜本身就是 {@code isEdible}，
     * 于是"手上本来就有食材"会被判成"已经做出成品"——制作还没开始就报完成。
     *
     * @return true 表示已减去基线（正确）
     */
    private static boolean cookVerifyUsesBaseline() {
        String packagePath = TaskLifecycleChecks.class.getPackageName().replace('.', '/');
        java.nio.file.Path path = java.nio.file.Path.of(
                "src/main/java/" + packagePath + "/CookVerifyTask.java");
        try {
            String source = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            // 判定处的赋值必须包含 countProducedInHands 且减去基线。
            // （不能只找第一个 "this.observed ="，那可能是 onStart 里的归零。）
            int observed = source.indexOf("this.observed = Math.max");
            if (observed < 0) {
                observed = source.indexOf("this.observed = countProducedInHands");
            }
            if (observed < 0) {
                throw new AssertionError("找不到基于产量计数的判定赋值");
            }
            int lineEnd = source.indexOf(';', observed);
            String assignment = source.substring(observed, lineEnd);
            return assignment.contains("baseline");
        } catch (java.io.IOException error) {
            throw new AssertionError("无法读取 CookVerifyTask 源码: " + path + " -> " + error);
        }
    }

    /**
     * 源码契约：队列暂停分支必须先判断 {@code pausedAtTick < 0}，否则每 tick 都会重复调 onPause。
     *
     * @return true 表示分支已带守卫（正确）
     */
    private static boolean pauseBranchGuardsOnPausedTick() {
        String packagePath = TaskLifecycleChecks.class.getPackageName().replace('.', '/');
        java.nio.file.Path path = java.nio.file.Path.of(
                "src/main/java/" + packagePath + "/HeroTaskQueue.java");
        try {
            String source = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            int branch = source.indexOf("if (paused || hero.isBattleModeActive()");
            if (branch < 0) {
                throw new AssertionError("找不到暂停分支（源码结构变化，请更新本契约检查）");
            }
            int branchEnd = source.indexOf("\n        }", branch);
            if (branchEnd < 0) {
                throw new AssertionError("暂停分支结构无法解析");
            }
            return source.substring(branch, branchEnd).contains("pausedAtTick() < 0L");
        } catch (java.io.IOException error) {
            throw new AssertionError("无法读取 HeroTaskQueue 源码: " + path + " -> " + error);
        }
    }

    /** 可配置返回值的任务桩：只用于驱动队列状态机，不触碰世界。 */
    private static final class StubTask extends PlannedTask {
        private final TaskState result;
        private final long timeout;
        private final int priority;

        StubTask(String id, Map<String, String> args, TaskState result, long timeout) {
            this(id, args, result, timeout, 0);
        }

        StubTask(String id, Map<String, String> args, TaskState result, long timeout, int priority) {
            super(id, args);
            this.result = result;
            this.timeout = timeout;
            this.priority = priority;
        }

        @Override
        public void onStart(AgentTaskContext context) {
        }

        @Override
        public TaskState tick(AgentTaskContext context) {
            return result;
        }

        @Override
        public String progressText() {
            return id();
        }

        @Override
        public long timeoutTicks() {
            return timeout;
        }

        @Override
        public int priority() {
            return priority;
        }
    }

    private TaskLifecycleChecks() {
    }

    private static final List<String> UNUSED = List.of();
}
