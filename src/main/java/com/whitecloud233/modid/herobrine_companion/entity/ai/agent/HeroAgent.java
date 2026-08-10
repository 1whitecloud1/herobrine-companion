package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import com.whitecloud233.modid.herobrine_companion.config.Config;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.memory.HeroMemory;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.memory.MemoryEpisode;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.AgentTaskContext;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.AgentTaskRegistry;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.HeroTaskQueue;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.PlannedTask;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolRegistry;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.HeroAcceptChallengeTool;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.HeroAscendTool;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.HeroDescendTool;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.HeroInspectTool;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.HeroLocateCompanionTool;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.HeroSetModeTool;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.HeroSummonTool;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.HeroUseSkillTool;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.network.AgentChatOutcomePacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 主控（M4）：在 M1 循环、M2 工具路由、M3 任务队列之上接通<b>记忆 + 反思学习</b>。
 *
 * <p><b>设计原则</b></p>
 * <ul>
 *   <li><b>单一职责</b>：本类只做编排（时序 + 数据流），感知 / 分类 / 规划 / 执行 / 工具 / 任务 / 记忆 / 反思各自独立。</li>
 *   <li><b>依赖倒置</b>：只依赖 {@link AgentSensor} / {@link AgentIntentionClassifier} /
 *       {@link AgentPlanner} / {@link AgentExecutor} / {@link AgentTaskPlanner} / {@link AgentReflector} 等抽象。</li>
 *   <li><b>低耦合</b>：经不可变 {@link AgentFrame} 快照读取实体状态；执行阶段唯一触碰实体。</li>
 *   <li><b>无回归（M1–M4 承诺）</b>：记忆 / 反思由 {@link Config#heroMemoryEnabled} 门控（默认 true，可关即无回归）。</li>
 * </ul>
 *
 * <p>任务队列由 {@link HeroEntity} <b>每 tick</b> 经 {@link #tickTasks(HeroEntity)} 驱动；
 * 完整感知循环仍是每 {@link #CYCLE_TICKS} tick 一次。</p>
 */
public final class HeroAgent {

    /** 完整循环周期：每 20 tick（1 秒）运行一次感知→决策。 */
    public static final int CYCLE_TICKS = 20;

    /** 自主任务重排冷却（tick）：避免同一意图在队列刚空闲时立刻重排，导致"检查完成"等播报刷屏。 */
    private static final long AUTONOMY_REPLAN_COOLDOWN_TICKS = 10L * 20L;

    /** 长期记忆 digest 的事实条数上限（注入 token 控制）。 */
    private static final int DIGEST_FACT_CAP = 8;

    /** 长期记忆 digest 的情景条数上限（注入 token 控制）。 */
    private static final int DIGEST_EPISODE_CAP = 12;

    private final List<AgentSensor> sensors;
    private final AgentIntentionClassifier classifier;
    private final AgentPlanner planner;
    private final AgentExecutor executor;
    private final AgentDecisionLog decisionLog;

    /** 统一工具注册表：Agent 调用外部能力的唯一出口（白名单/校验/确认/审计收敛其中）。 */
    private final AgentToolRegistry toolRegistry;

    /** 任务规划器：意图 → 任务序列。 */
    private final AgentTaskPlanner taskPlanner;

    /** 任务队列：多步目标的顺序执行器（tick 预算 / 失败 / 存档）。 */
    private final HeroTaskQueue taskQueue;

    /** 三层记忆（per-owner 懒加载，长期存 PlayerProfile）。 */
    private final HeroMemory memory;

    /** 反思 / 学习：把决策结果回填记忆与心智。 */
    private final AgentReflector reflector;

    private boolean memoryLoaded;

    /** 下一个周期待处理的工具请求（跨线程由外部注入，volatile 保证可见性）。 */
    private volatile AgentToolRequest pendingRequest;

    private AgentIntention lastIntention = AgentIntention.IDLE;

    /** 上一次自主入队任务序列的 game tick（配合冷却防刷屏）。 */
    private long lastAutonomousPlanTick = -AUTONOMY_REPLAN_COOLDOWN_TICKS;

    /** 使用默认装配构造（M2 默认实现：规则分类器 + 注册表默认工具 + P5 感知/记忆传感器）。 */
    public HeroAgent() {
        this(List.of(new AgentMindSensor(), new AgentWorldSensor(),
                        new AgentTargetSensor(), new AgentInventorySensor(), new AgentEnvironmentSensor()),
                new AutonomyIntentionClassifier(),
                new DefaultAgentPlanner(),
                null);
    }

    /** 面向测试 / M2+ 替换的注入构造（依赖倒置）。 */
    public HeroAgent(List<AgentSensor> sensors,
                     AgentIntentionClassifier classifier,
                     AgentPlanner planner,
                     AgentExecutor executor) {
        // 先建记忆，记忆传感器才可注入（P5 记忆→感知闭环）。
        this.memory = new HeroMemory();
        // 始终附加指令传感器 + 记忆传感器：指令把挂起工具请求接入循环，记忆把长期记忆读进感知。
        List<AgentSensor> base = new java.util.ArrayList<>(sensors);
        base.add(new AgentDirectiveSensor(() -> this.pendingRequest));
        base.add(new AgentMemorySensor(this.memory));
        this.sensors = List.copyOf(base);
        this.classifier = classifier;
        this.planner = planner;
        this.toolRegistry = createDefaultRegistry();
        this.executor = executor == null
                ? new DefaultAgentExecutor(this.toolRegistry)
                : executor;
        this.taskPlanner = new DefaultAgentTaskPlanner();
        this.taskQueue = createDefaultTaskQueue();
        this.reflector = new DefaultAgentReflector(this.memory);
        this.decisionLog = new AgentDecisionLog();
    }

    /** 每 tick 由实体调用；内部自行降频、门控与校验。 */
    public void tick(HeroEntity hero) {
        if (hero == null || hero.level() == null || hero.level().isClientSide) {
            return;
        }
        if (hero.isRemoved() || !hero.isAlive()) {
            return;
        }
        if (hero.tickCount % CYCLE_TICKS != 0) {
            return;
        }
        runCycle(hero);
    }

    /**
     * 任务队列逐 tick 驱动。由 {@link HeroEntity} 每 tick 调用（在服务端·非挑战维度门内）。
     * 内部不做扫描，开销可忽略；战斗态由队列自行暂停。
     */
    public void tickTasks(HeroEntity hero) {
        if (hero == null || hero.level() == null || hero.level().isClientSide) {
            return;
        }
        if (hero.isRemoved() || !hero.isAlive()) {
            return;
        }
        taskQueue.tick(new AgentTaskContext(hero));
    }

    /** 单次完整循环：感知 → 思考 → 规划 → 执行 → 记录 → 反思。 */
    private void runCycle(HeroEntity hero) {
        ensureMemory(hero);
        AgentFrame frame = AgentFrame.capture(hero);
        AgentToolRequest request = pendingRequest;

        // 感知（含指令传感器：把挂起工具请求变成观测）
        AgentPerceptionCollector collector = new AgentPerceptionCollector();
        for (AgentSensor sensor : sensors) {
            sensor.collect(frame, collector);
        }
        List<AgentObservation> observations = collector.takeAll();

        // 思考（意图 + 路由）
        AgentIntentionClassifier.Classification classification = classifier.classify(frame, observations);

        // 规划
        AgentPlan plan = planner.plan(frame, classification);

        // 执行（工具请求一次性消费）
        AgentExecutor.Outcome outcome = executor.execute(hero, frame, plan, request);
        this.pendingRequest = null;

        // 自主长任务（P4 起配置化，默认开；队列空闲且意图适合 → 由规划器入队一段真实任务序列）。
        // 冷却：避免同一意图在队列刚空闲时立刻重排，导致"检查完成"等播报刷屏。
        if (Config.heroAutonomyEnabled && taskQueue.isIdle()) {
            long gameTime = frame.gameTime();
            if (gameTime - lastAutonomousPlanTick >= AUTONOMY_REPLAN_COOLDOWN_TICKS) {
                List<PlannedTask> planned = taskPlanner.plan(frame, classification);
                if (!planned.isEmpty()) {
                    taskQueue.enqueueAll(planned);
                    lastAutonomousPlanTick = gameTime;
                }
            }
        }

        // 记录（解释自己；队列非空时附带当前任务进度）
        String outcomeDescription = outcome.description();
        if (!taskQueue.isIdle()) {
            outcomeDescription = outcomeDescription + " | 队列: " + taskQueue.currentProgress();
        }
        this.lastIntention = plan.intention();
        AgentDecision decision = new AgentDecision(
                frame.gameTime(), observations, plan.intention(), plan.channel(),
                classification.reason(), plan.actionNote(),
                outcome.acted(), outcomeDescription);
        decisionLog.record(decision);

        // 反思 / 学习：把本次决策结果回填记忆与心智
        AgentReflector.Reflection reflection = reflector.reflect(hero, decision);
        if (!reflection.isEmpty()) {
            decisionLog.record(new AgentDecision(
                    frame.gameTime(), List.of(), plan.intention(), plan.channel(),
                    "反思写入教训", String.join("; ", reflection.lessons()),
                    false, "已记入长期记忆"));
        }
    }

    /** 懒加载 per-owner 长期记忆（配置门控）。 */
    private void ensureMemory(HeroEntity hero) {
        if (memoryLoaded || !Config.heroMemoryEnabled || hero.getOwnerUUID() == null) {
            return;
        }
        memoryLoaded = true;
        if (hero.level() instanceof ServerLevel serverLevel) {
            CompoundTag tag = HeroWorldData.get(serverLevel).getHeroMemory(hero.getOwnerUUID());
            if (tag != null && !tag.isEmpty()) {
                memory.read(tag);
            }
        }
    }

    /** 三层记忆（调试 / 未来 LLM 上下文注入入口）。 */
    public HeroMemory memory() {
        return memory;
    }

    /** 注入一段任务序列；队列空闲时立即开始执行（M4 对话/LLM 入口，与 requestTool 平行）。 */
    public void requestTasks(List<PlannedTask> tasks) {
        taskQueue.enqueueAll(tasks);
    }

    /** 任务队列（调试 / 存档入口）。 */
    public HeroTaskQueue taskQueue() {
        return taskQueue;
    }

    /** 任务规划器（调试 / 展示任务序列来源）。 */
    public AgentTaskPlanner taskPlanner() {
        return taskPlanner;
    }

    /** 任务队列存档（随 HeroEntity 的 addAdditionalSaveData 调用）。 */
    public void saveTasks(CompoundTag tag) {
        taskQueue.write(tag);
    }

    /** 任务队列恢复（随 HeroEntity 的 readAdditionalSaveData 调用）。 */
    public void loadTasks(CompoundTag tag) {
        taskQueue.read(tag);
    }

    /** 注入一个待处理的工具请求；下一个循环周期被路由并执行（M4 对话/LLM 入口）。 */
    public void requestTool(String toolId,
                            com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolArgs args,
                            UUID requesterUuid,
                            boolean playerApproved,
                            UUID requestId) {
        this.pendingRequest = new AgentToolRequest(toolId, args, requesterUuid, playerApproved, requestId);
    }

    /**
     * 接收客户端回流的对话结果(M4 Phase 1):把一段对话的最小摘要写入情景记忆,
     * 让 agent 真正"记得住"聊天。由 {@link AgentChatOutcomePacket} 路由调用。
     *
     * <p>只写记忆、不改变世界状态;持久化交给 {@code DefaultAgentReflector} 的
     * 周期落盘(1200 tick),此处不做写穿以免高频磁盘 I/O。</p>
     */
    public void acceptChatOutcome(HeroEntity hero, String message, String reply, int kind) {
        if (hero == null || !Config.heroMemoryEnabled || hero.getOwnerUUID() == null) {
            return;
        }
        ensureMemory(hero);
        long gameTime = hero.level() == null ? 0L : hero.level().getGameTime();
        String text = buildOutcomeText(message, reply, kind);
        if (text.isBlank()) {
            return;
        }
        int importance = switch (kind) {
            case AgentChatOutcomePacket.KIND_ACTOR_DIALOGUE -> 2;
            default -> 1; // 玩家聊天
        };
        memory.rememberEvent(text, importance, gameTime);
    }

    /** 把一段对话蒸馏成一行记忆文本(截断超长内容,避免记忆无限膨胀)。 */
    private static String buildOutcomeText(String message, String reply, int kind) {
        StringBuilder sb = new StringBuilder();
        String m = message == null ? "" : message.trim();
        String r = reply == null ? "" : reply.trim();
        boolean actor = kind == AgentChatOutcomePacket.KIND_ACTOR_DIALOGUE;
        if (!m.isEmpty()) {
            sb.append(actor ? "场景: " : "玩家说: ").append(truncateText(m, 200));
        }
        if (!r.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append("我回应: ").append(truncateText(r, 300));
        }
        return sb.toString();
    }

    private static String truncateText(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /**
     * 构建长期记忆 digest(M4 Phase 1):玩家偏好(facts,始终注入) + 重要情景(episodes,分层),
     * 带"历史不定义身份"框定语,供注入聊天 LLM 上下文。由 {@code RequestHeroMemoryDigestPacket} 调用。
     *
     * <p>框定语是本方案对抗"改人设后记忆与设定冲突"的第一道防线:记忆当背景史料,
     * 不当作身份绑定;真正定义"我现在是谁"的仍是当前人设 prompt。</p>
     */
    public String buildMemoryDigest(HeroEntity hero) {
        if (hero == null || !Config.heroMemoryEnabled || hero.getOwnerUUID() == null) {
            return "";
        }
        ensureMemory(hero);
        if (memory.episodeCount() == 0 && memory.facts().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("（你过往的记忆。它们不定义你现在的身份——请以当前人设为准，仅把它们当作背景参考。）");
        Map<String, String> facts = memory.facts();
        if (!facts.isEmpty()) {
            sb.append("\n玩家偏好:");
            int shown = 0;
            for (Map.Entry<String, String> fact : facts.entrySet()) {
                if (++shown > DIGEST_FACT_CAP) {
                    break;
                }
                sb.append("\n- ").append(fact.getKey()).append(" = ").append(fact.getValue());
            }
        }
        List<MemoryEpisode> episodes = memory.topEpisodes(DIGEST_EPISODE_CAP);
        if (!episodes.isEmpty()) {
            sb.append("\n过往经历:");
            for (MemoryEpisode episode : episodes) {
                sb.append("\n- ").append(episode.isLesson() ? "[教训] " : "").append(episode.text());
            }
        }
        return sb.toString();
    }

    /**
     * 清理长期记忆(记忆管理器按钮):keepFacts 为 true 只清叙事层(保留玩家偏好),
     * 否则清空全部。清理后立即落盘。由 {@code ClearHeroMemoryPacket} 调用。
     */
    public void clearMemory(HeroEntity hero, boolean keepFacts) {
        if (hero == null || !Config.heroMemoryEnabled || hero.getOwnerUUID() == null) {
            return;
        }
        ensureMemory(hero);
        if (keepFacts) {
            memory.clearEpisodes();
        } else {
            memory.clearAll();
        }
        persistMemory(hero);
    }

    /** 立即把内存中的记忆写穿到玩家档案(记忆管理器 / 清理后即时落盘用)。 */
    public void persistMemory(HeroEntity hero) {
        if (hero == null || hero.getOwnerUUID() == null || !(hero.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        memory.write(tag);
        HeroWorldData.get(serverLevel).setHeroMemory(hero.getOwnerUUID(), tag);
    }

    /** 统一工具注册表（目录 / 审计 / 调试入口）。 */
    public AgentToolRegistry toolRegistry() {
        return toolRegistry;
    }

    /** 最近一次决定的高层意图。 */
    public AgentIntention lastIntention() {
        return lastIntention;
    }

    /** "解释自己"：返回最近一次决策的单行文本。 */
    public String explain() {
        return decisionLog.explainLast();
    }

    /** 决策日志（调试 / 可观测面板使用）。 */
    public AgentDecisionLog decisionLog() {
        return decisionLog;
    }

    private static AgentToolRegistry createDefaultRegistry() {
        AgentToolRegistry registry = new AgentToolRegistry(new com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolAudit());
        registry.register(new HeroInspectTool());
        registry.register(new HeroLocateCompanionTool());
        registry.register(new HeroAscendTool());
        registry.register(new HeroDescendTool());
        registry.register(new HeroAcceptChallengeTool());
        registry.register(new HeroSetModeTool());
        registry.register(new HeroSummonTool());
        registry.register(new HeroUseSkillTool());
        return registry;
    }

    private static HeroTaskQueue createDefaultTaskQueue() {
        AgentTaskRegistry registry = new AgentTaskRegistry();
        registry.registerDefaults();
        return new HeroTaskQueue(registry);
    }
}