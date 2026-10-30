package com.whitecloud233.herobrine_companion.entity.ai.agent;

import com.whitecloud233.herobrine_companion.config.Config;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.memory.HeroMemory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 默认反思实现：把每次决策的结果温和地回填记忆与心智。
 *
 * <p><b>只把世界事实写为经历</b>（交接 A3）：等待确认、参数被拒、未知工具、队列满、
 * 执行期异常等<b>软件流程结果</b>一律不写失败教训、不触发心智失败反馈。
 * 工具通道现在由 {@code AgentToolRequestService} 直接执行，决策日志只承载自主通道，
 * 因此本类只消费"自主决策 + 世界动作失败"这一条真实链路。</p>
 *
 * <p>反馈量级对齐现有调用（如 {@code HeroObserver} 的 0.02~0.05），且整体由
 * {@link Config#heroMemoryEnabled} 门控——关闭即完全无行为影响（无回归逃生门）。</p>
 */
public final class DefaultAgentReflector implements AgentReflector {

    /** 记忆压缩 + 落盘周期（镜像 HeroBrain.saveMemories 的 1200 tick 节奏）。 */
    private static final long FLUSH_INTERVAL_TICKS = 1200L;

    private final HeroMemory memory;

    /** 统一落盘入口（HeroAgent.persistMemory），避免两份各写一半的存档组装。 */
    private final Consumer<HeroEntity> persist;

    private long lastFlushTick = 0L;

    public DefaultAgentReflector(HeroMemory memory, Consumer<HeroEntity> persist) {
        this.memory = memory;
        this.persist = persist;
    }

    @Override
    public Reflection reflect(HeroEntity hero, AgentDecision decision) {
        if (!Config.heroMemoryEnabled || hero.getOwnerUUID() == null) {
            return Reflection.none();
        }
        UUID owner = hero.getOwnerUUID();
        List<String> lessons = new ArrayList<>();
        long gameTime = decision.gameTime();

        if (decision.channel() == AgentChannel.TOOL_INVOCATION) {
            if (decision.acted()) {
                memory.rememberEvent("工具执行: " + decision.outcome(), 1, gameTime);
                hero.getHeroBrain().inputExploration(owner, 0.02f);
            } else if (decision.worldFailure()) {
                // 只有"世界事实"失败才写教训；软件流程失败已在上游被标成非世界原因。
                String lesson = "lesson:tool_rejected|detail=" + decision.outcome();
                memory.learnLesson(lesson, gameTime);
                hero.getHeroBrain().inputFailure(owner, 0.05f);
                lessons.add(lesson);
            }
        }

        // 周期压缩 + 落盘
        if (gameTime - lastFlushTick >= FLUSH_INTERVAL_TICKS) {
            lastFlushTick = gameTime;
            memory.prune(gameTime);
            if (persist != null) {
                persist.accept(hero);
            }
        }

        return lessons.isEmpty() ? Reflection.none() : new Reflection(lessons);
    }
}
