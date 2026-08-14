package com.whitecloud233.herobrine_companion.entity.ai.agent;

import com.whitecloud233.herobrine_companion.config.Config;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.memory.HeroMemory;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 默认反思实现（M4）：把每次决策的结果温和地回填记忆与心智。
 *
 * <p>反馈量级对齐现有调用（如 {@code HeroObserver} 的 0.02~0.05），且整体由
 * {@link Config#heroMemoryEnabled} 门控——关闭即完全无行为影响（无回归逃生门）。</p>
 */
public final class DefaultAgentReflector implements AgentReflector {

    /** 记忆压缩 + 落盘周期（镜像 HeroBrain.saveMemories 的 1200 tick 节奏）。 */
    private static final long FLUSH_INTERVAL_TICKS = 1200L;

    private final HeroMemory memory;
    private long lastFlushTick = 0L;

    public DefaultAgentReflector(HeroMemory memory) {
        this.memory = memory;
    }

    @Override
    public Reflection reflect(HeroEntity hero, AgentDecision decision) {
        if (!Config.heroMemoryEnabled || hero.getOwnerUUID() == null) {
            return Reflection.none();
        }
        UUID owner = hero.getOwnerUUID();
        List<String> lessons = new ArrayList<>();
        long gameTime = decision.gameTime();

        // 工具结果 → 记忆 + 心智（温和）
        if (decision.channel() == AgentChannel.TOOL_INVOCATION) {
            if (decision.acted()) {
                memory.rememberEvent("工具执行: " + decision.outcome(), 1, gameTime);
                hero.getHeroBrain().inputExploration(owner, 0.02f);
            } else {
                // P5：结构化教训（lesson:key=value|...），供 HeroLessonRules 解析消费。
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
            saveToProfile(hero);
        }

        return lessons.isEmpty() ? Reflection.none() : new Reflection(lessons);
    }

    private void saveToProfile(HeroEntity hero) {
        if (hero.getOwnerUUID() == null || !(hero.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        memory.write(tag);
        HeroWorldData.get(serverLevel).setHeroMemory(hero.getOwnerUUID(), tag);
    }
}