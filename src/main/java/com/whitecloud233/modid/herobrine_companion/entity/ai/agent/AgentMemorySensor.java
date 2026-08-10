package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import com.whitecloud233.modid.herobrine_companion.config.Config;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.memory.HeroMemory;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.memory.MemoryEpisode;

import java.util.List;

/**
 * 记忆传感器（P5）：把长期记忆（近期情景 + 教训规则）读进感知桶，让 agent 的
 * 决策循环"记得住"——记忆 → 行为 的闭环读侧。
 *
 * <p><b>单一职责</b>：只做"记忆 → 观测"，不写记忆（写侧在 {@code DefaultAgentReflector}）。
 * 受 {@link Config#heroMemoryEnabled} 门控（关闭即无行为影响）。</p>
 */
public final class AgentMemorySensor implements AgentSensor {

    private static final int DIGEST_EPISODE_CAP = 2;

    private final HeroMemory memory;

    public AgentMemorySensor(HeroMemory memory) {
        this.memory = memory;
    }

    @Override
    public void collect(AgentFrame frame, AgentPerceptionCollector sink) {
        if (memory == null || !Config.heroMemoryEnabled) {
            return;
        }
        String summary = buildSummary();
        if (summary.isEmpty()) {
            return;
        }
        sink.add(AgentObservation.of(
                AgentSenseKind.MEMORY_BACKLOG,
                source(),
                frame.gameTime(),
                summary,
                1));
    }

    private String buildSummary() {
        StringBuilder builder = new StringBuilder("近期记忆:");
        List<MemoryEpisode> top = memory.topEpisodes(DIGEST_EPISODE_CAP);
        boolean any = false;
        for (MemoryEpisode episode : top) {
            builder.append("\n- ").append(episode.isLesson() ? "[教训] " : "").append(truncate(episode.text(), 60));
            any = true;
        }
        // 附加可被分类器消费的教训规则摘要。
        String rules = HeroLessonRules.summarize(HeroLessonRules.parse(memory, 12));
        if (!rules.isEmpty()) {
            builder.append("\n").append(rules);
            any = true;
        }
        return any ? builder.toString() : "";
    }

    private static String truncate(String text, int max) {
        return text == null ? "" : (text.length() <= max ? text : text.substring(0, max) + "…");
    }

    @Override
    public String source() {
        return "memory";
    }
}
