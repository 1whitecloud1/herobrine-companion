package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.memory.HeroMemory;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.memory.MemoryEpisode;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.HeroTaskQueue;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolDescriptor;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 状态采集器（M5）：把服务端 {@link HeroAgent} 各子系统的当前状态<b>读</b>成一份
 * {@link AgentStatusSnapshot}。
 *
 * <p><b>单一职责</b>：只做"读取 + 组装"。不改变任何 agent 状态（纯查询、无副作用），
 * 不做网络收发，不做渲染。因此可在任意时刻安全调用，不干扰 agent 循环。</p>
 *
 * <p>字段顺序与设计文档 4.9 的验收一致：当前意图 → 当前任务队列 → 上次决策原因。</p>
 */
public final class AgentStatusCollector {

    /** 记忆管理器视图:玩家偏好最多展示条数(与情景一起受快照 MAX_LINES 上限)。 */
    private static final int MEMORY_FACT_LINES = 6;

    /** 记忆管理器视图:顶层情景最多展示条数。 */
    private static final int MEMORY_EPISODE_LINES = 6;

    private AgentStatusCollector() {
    }

    /**
     * 采集一份快照。
     *
     * @param hero  目标 Hero（服务端实体）
     * @param agent 该 Hero 的 agent
     * @return 不可变快照；入参为 null 时返回 {@link AgentStatusSnapshot#empty()}
     */
    public static AgentStatusSnapshot collect(HeroEntity hero, HeroAgent agent) {
        if (hero == null || agent == null) {
            return AgentStatusSnapshot.empty();
        }

        int lines = AgentStatusSnapshot.MAX_LINES;
        HeroTaskQueue queue = agent.taskQueue();
        HeroMemory memory = agent.memory();
        AgentToolRegistry registry = agent.toolRegistry();

        long gameTime = hero.level() == null ? 0L : hero.level().getGameTime();
        AgentIntention intention = agent.lastIntention();

        return new AgentStatusSnapshot(
                gameTime,
                intention == null ? "—" : intention.name(),
                agent.explain(),
                // getMindState 有兜底（越界回 OBSERVER），不会为 null。
                hero.getMindState().name(),
                queue.size(),
                queue.currentProgress(),
                memory.episodeCount(),
                memory.facts().size(),
                agent.decisionLog().recent(lines),
                registry.audit().recent(lines),
                newestFirst(queue.recentLog(), lines),
                describeCatalog(registry),
                describeMemory(memory));
    }

    /** 工具目录：id + 是否需确认 + 参数摘要，供安全审计视图对照"允许做什么"。 */
    private static List<String> describeCatalog(AgentToolRegistry registry) {
        List<AgentToolDescriptor> descriptors = registry.catalog();
        List<String> catalog = new ArrayList<>(descriptors.size());
        for (AgentToolDescriptor descriptor : descriptors) {
            catalog.add(String.format("%s%s %s",
                    descriptor.id(),
                    descriptor.requiresConfirmation() ? " [需确认]" : "",
                    descriptor.parameterSummary()));
        }
        return catalog;
    }

    /** 记忆管理器视图:玩家偏好(facts,稳定) + 顶层情景(episodes,按重要度/新近)。 */
    private static List<String> describeMemory(HeroMemory memory) {
        List<String> lines = new ArrayList<>();
        int factShown = 0;
        for (Map.Entry<String, String> fact : memory.facts().entrySet()) {
            if (++factShown > MEMORY_FACT_LINES) {
                break;
            }
            lines.add("偏好: " + fact.getKey() + " = " + fact.getValue());
        }
        for (MemoryEpisode episode : memory.topEpisodes(MEMORY_EPISODE_LINES)) {
            lines.add((episode.isLesson() ? "[教训] " : "") + episode.text());
        }
        return lines;
    }

    /**
     * 取列表末尾 n 条并反转成"新的在前"，与 {@code decisionLog().recent()} /
     * {@code audit().recent()} 的语义对齐（队列日志本身是旧→新顺序）。
     */
    private static List<String> newestFirst(List<String> source, int n) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, source.size() - n);
        List<String> slice = new ArrayList<>(source.subList(from, source.size()));
        Collections.reverse(slice);
        return slice;
    }
}
