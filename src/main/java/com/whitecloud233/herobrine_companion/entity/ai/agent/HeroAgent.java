package com.whitecloud233.herobrine_companion.entity.ai.agent;

import com.whitecloud233.herobrine_companion.config.Config;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.memory.HeroMemory;
import com.whitecloud233.herobrine_companion.entity.ai.agent.memory.MemoryEpisode;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.AgentTaskContext;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.AgentTaskRegistry;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.HeroTaskQueue;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.PlannedTask;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolArgs;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolAudit;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolFactory;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolRequestService;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolRegistry;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.herobrine_companion.network.AgentChatOutcomePacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 主控：编排（时序 + 数据流）。感知 / 分类 / 规划 / 执行 / 工具 / 任务 / 记忆 / 反思各自独立。
 *
 * <p><b>显式工具请求不走自主分类</b>（交接 A1）：客户端或玩家发来的工具 ID 已经明确，
 * 直接交给 {@link AgentToolRequestService}——有界 FIFO、去重、立刻执行短动作、每条都有终态回执。
 * 自主循环（{@link #CYCLE_TICKS}）只负责低频的自我驱动与决策记录，不决定显式请求是否被消费。</p>
 *
 * <p><b>记忆归属</b>（交接 A5）：缓存记录实际 owner UUID，不再只靠一个布尔值；
 * 所有落盘都走 {@link #persistMemory} 这一个入口，变更置 dirty。</p>
 */
public final class HeroAgent {

    /** 完整循环周期：每 20 tick（1 秒）运行一次感知→决策。 */
    public static final int CYCLE_TICKS = 20;

    /** 显式请求每 tick 最多执行条数：够快又不至于一次抽干主线程。 */
    private static final int REQUESTS_PER_TICK = 2;

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

    /** 显式工具请求的有界执行入口（直接服务，不经分类器）。 */
    private final AgentToolRequestService toolRequests;

    /** 任务规划器：意图 → 任务序列。 */
    private final AgentTaskPlanner taskPlanner;

    /** 任务队列：多步目标的顺序执行器（tick 预算 / 失败 / 存档）。 */
    private final HeroTaskQueue taskQueue;

    /** 三层记忆（per-owner，长期存 PlayerProfile）。 */
    private final HeroMemory memory;

    /** 反思 / 学习：把决策结果回填记忆与心智。 */
    private final AgentReflector reflector;

    /** 当前缓存记忆的实际归属 owner（null = 尚未加载）。 */
    private UUID memoryOwner;

    /** 记忆是否有未落盘变更。 */
    private boolean memoryDirty;

    private AgentIntention lastIntention = AgentIntention.IDLE;

    /** 上一次自主入队任务序列的 game tick（配合冷却防刷屏）。 */
    private long lastAutonomousPlanTick = -AUTONOMY_REPLAN_COOLDOWN_TICKS;

    /** 使用默认装配构造（规则分类器 + 默认工具清单 + P5 感知/记忆传感器）。 */
    public HeroAgent() {
        this(List.of(new AgentMindSensor(), new AgentWorldSensor(),
                        new AgentTargetSensor(), new AgentInventorySensor(), new AgentEnvironmentSensor()),
                new AutonomyIntentionClassifier(),
                new DefaultAgentPlanner(),
                null);
    }

    /** 面向测试 / 替换的注入构造（依赖倒置）。 */
    public HeroAgent(List<AgentSensor> sensors,
                     AgentIntentionClassifier classifier,
                     AgentPlanner planner,
                     AgentExecutor executor) {
        // 先建记忆，记忆传感器才可注入（P5 记忆→感知闭环）。
        this.memory = new HeroMemory();
        List<AgentSensor> base = new ArrayList<>(sensors);
        base.add(new AgentMemorySensor(this.memory));
        this.sensors = List.copyOf(base);
        this.classifier = classifier;
        this.planner = planner;
        this.toolRegistry = createDefaultRegistry();
        this.toolRequests = new AgentToolRequestService(new AgentToolInbox(), this.toolRegistry);
        this.executor = executor == null
                ? new DefaultAgentExecutor()
                : executor;
        this.taskPlanner = new DefaultAgentTaskPlanner();
        this.taskQueue = createDefaultTaskQueue();
        this.reflector = new DefaultAgentReflector(this.memory, this::persistMemory);
        this.decisionLog = new AgentDecisionLog();
    }

    /**
     * 每 tick 由实体调用。
     *
     * <p>显式工具请求<b>每 tick</b>推进（不等感知周期），自主感知循环仍按 {@link #CYCLE_TICKS} 降频。</p>
     */
    public void tick(HeroEntity hero) {
        if (!isServiceable(hero)) {
            return;
        }
        // A1：显式请求的有界执行入口与服务端线程同步执行，短动作当 tick 出结果。
        toolRequests.drain(hero, REQUESTS_PER_TICK);
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
        if (!isServiceable(hero)) {
            return;
        }
        taskQueue.tick(new AgentTaskContext(hero));
    }

    private static boolean isServiceable(HeroEntity hero) {
        return hero != null && hero.level() != null && !hero.level().isClientSide
                && !hero.isRemoved() && hero.isAlive();
    }

    /** 单次完整循环：感知 → 思考 → 规划 → 记录 → 反思（显式请求已在 tick 内处理）。 */
    private void runCycle(HeroEntity hero) {
        ensureMemory(hero);
        AgentFrame frame = AgentFrame.capture(hero);

        AgentPerceptionCollector collector = new AgentPerceptionCollector();
        for (AgentSensor sensor : sensors) {
            sensor.collect(frame, collector);
        }
        List<AgentObservation> observations = collector.takeAll();

        AgentIntentionClassifier.Classification classification = classifier.classify(frame, observations);
        AgentPlan plan = planner.plan(frame, classification);

        // 自主通道：显式请求不再经过这里，执行器只处理"世界行为 / 空闲"说明。
        AgentExecutor.Outcome outcome = executor.execute(hero, frame, plan, null);

        // 自主长任务（配置化，默认开；队列空闲且意图适合 → 由规划器入队一段真实任务序列）。
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

        AgentReflector.Reflection reflection = reflector.reflect(hero, decision);
        if (!reflection.isEmpty()) {
            decisionLog.record(new AgentDecision(
                    frame.gameTime(), List.of(), plan.intention(), plan.channel(),
                    "反思写入教训", String.join("; ", reflection.lessons()),
                    false, "已记入长期记忆"));
        }
    }

    // ---- 显式工具请求（A1/A2/A3） ----

    /**
     * 受理一条显式工具请求：去重 + 有界排队 + 立刻尝试执行。
     *
     * @return 投递结果；{@link AgentToolInbox.Offer#FULL} 时已向请求者回了 busy 回执
     */
    public AgentToolInbox.Offer submitToolRequest(HeroEntity hero,
                                                  String toolId,
                                                  AgentToolArgs args,
                                                  UUID requesterUuid,
                                                  boolean playerApproved,
                                                  UUID requestId) {
        if (hero == null) {
            return AgentToolInbox.Offer.DUPLICATE;
        }
        return toolRequests.submit(hero, new AgentToolRequest(toolId, args, requesterUuid, playerApproved, requestId));
    }

    /** 请求收件箱（状态页 / 测试 / 重复确认判定）。 */
    public AgentToolInbox inbox() {
        return toolRequests.inbox();
    }

    /** 请求执行服务（测试注入 / 状态展示）。 */
    public AgentToolRequestService toolRequests() {
        return toolRequests;
    }

    /** 实体移除 / 换世界 / 归属切换时的统一收尾：释放排队请求。 */
    public void onDetached() {
        toolRequests.clear();
    }

    // ---- 记忆（A5） ----

    /**
     * 懒加载 per-owner 长期记忆。
     *
     * <p>缓存记录<b>实际 owner UUID</b>：同一实体运行期归属变化时，先把旧主人的脏数据落盘，
     * 再切换到新主人的档案，避免"旧缓存被当作新主人的记忆使用 / 保存"。</p>
     */
    private void ensureMemory(HeroEntity hero) {
        if (!Config.heroMemoryEnabled || hero.getOwnerUUID() == null) {
            return;
        }
        UUID owner = hero.getOwnerUUID();
        if (owner.equals(memoryOwner)) {
            return;
        }
        if (memoryOwner != null) {
            // 归属切换：旧主人的数据必须先按旧 UUID 落盘，再丢弃缓存。
            flushMemory(hero, memoryOwner);
        }
        memory.clearAll();
        memoryDirty = false;
        memoryOwner = owner;
        if (hero.level() instanceof ServerLevel serverLevel) {
            CompoundTag tag = HeroWorldData.get(serverLevel).getHeroMemory(owner);
            if (tag != null && !tag.isEmpty()) {
                memory.read(tag);
            }
        }
    }

    /** 标记记忆有未落盘变更（写入侧统一调用）。 */
    public void markMemoryDirty() {
        memoryDirty = true;
    }

    /** 三层记忆（调试 / LLM 上下文注入入口）。 */
    public HeroMemory memory() {
        return memory;
    }

    /** 当前缓存记忆的归属 owner（调试 / 归属校验）。 */
    public UUID memoryOwner() {
        return memoryOwner;
    }

    /** 缓存是否有未落盘变更。 */
    public boolean memoryDirty() {
        return memoryDirty;
    }

    /**
     * <b>唯一落盘入口</b>：把缓存记忆写给 {@code owner} 的玩家档案并清 dirty。
     *
     * <p>周期刷新、实体卸载、归属切换、手动清理都走这里，避免两份各写一半的存档组装。</p>
     */
    public void persistMemory(HeroEntity hero) {
        if (hero == null) {
            return;
        }
        UUID owner = hero.getOwnerUUID() != null ? hero.getOwnerUUID() : memoryOwner;
        if (owner == null) {
            return;
        }
        flushMemory(hero, owner);
        if (owner.equals(memoryOwner)) {
            memoryDirty = false;
        }
    }

    private void flushMemory(HeroEntity hero, UUID owner) {
        if (owner == null || !(hero.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        memory.write(tag);
        HeroWorldData.get(serverLevel).setHeroMemory(owner, tag);
        memoryDirty = false;
    }

    /** 实体生命周期结束（移除 / 卸载）时刷写记忆，缩小"最近对话丢失窗口"。 */
    public void flushMemoryOnDetach(HeroEntity hero) {
        if (hero == null || !Config.heroMemoryEnabled) {
            return;
        }
        if (memoryOwner != null) {
            flushMemory(hero, memoryOwner);
        }
    }

    // ---- 任务队列 ----

    /** 注入一段任务序列；队列空闲时立即开始执行（对话/LLM 入口）。 */
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

    // ---- 对话结果回流 ----

    /**
     * 接收客户端回流的对话结果：把一段对话的最小摘要写入情景记忆，让 agent 真正"记得住"聊天。
     * 由 {@code AgentChatOutcomePacket} 路由调用。
     *
     * <p>只写记忆、不改变世界状态；持久化交给周期刷新与卸载刷写，此处不做写穿以免高频磁盘 I/O。</p>
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
        markMemoryDirty();
    }

    /** 把一段对话蒸馏成一行记忆文本（截断超长内容，避免记忆无限膨胀）。 */
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
     * 构建长期记忆 digest：玩家偏好（facts，始终注入）+ 重要情景（episodes，分层），
     * 带"历史不定义身份"框定语，供注入聊天 LLM 上下文。由 {@code RequestHeroMemoryDigestPacket} 调用。
     *
     * <p>框定语是本方案对抗"改人设后记忆与设定冲突"的第一道防线：记忆当背景史料，
     * 不当作身份绑定；真正定义"我现在是谁"的仍是当前人设 prompt。</p>
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
     * 清理长期记忆（记忆管理器按钮）：keepFacts 为 true 只清叙事层（保留玩家偏好），
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
        markMemoryDirty();
        persistMemory(hero);
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
        AgentToolRegistry registry = new AgentToolRegistry(new AgentToolAudit());
        AgentToolFactory.createDefaults().forEach(registry::register);
        return registry;
    }

    private static HeroTaskQueue createDefaultTaskQueue() {
        AgentTaskRegistry registry = new AgentTaskRegistry();
        registry.registerDefaults();
        return new HeroTaskQueue(registry);
    }
}
