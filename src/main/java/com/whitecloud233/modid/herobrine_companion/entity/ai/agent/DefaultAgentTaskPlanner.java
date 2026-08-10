package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.InspectAreaTask;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.PlannedTask;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.RepairAreaTask;

import java.util.List;
import java.util.Map;

/**
 * 规则任务规划器（M3 默认 + P4 真实任务）：按意图产出一段可执行的任务序列。
 *
 * <p>自主触发由 {@code Config.heroAutonomyEnabled} 门控（P4 起配置化，默认开）；
 * 本规划器是"任务序列长什么样"的唯一来源，调试命令 / LLM 的 {@code agent_task} 都经由它生成。</p>
 */
public final class DefaultAgentTaskPlanner implements AgentTaskPlanner {

    @Override
    public List<PlannedTask> plan(AgentFrame frame, AgentIntentionClassifier.Classification classification) {
        return switch (classification.intention()) {
            case REMEDIATE -> List.of(new RepairAreaTask(Map.of()));
            case INVESTIGATE -> List.of(new InspectAreaTask(Map.of()));
            default -> List.of();
        };
    }
}
