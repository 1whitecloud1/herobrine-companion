package com.whitecloud233.herobrine_companion.entity.ai.agent;

import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork.MindState;

import java.util.List;

/**
 * 规则驱动的意图分类器（M2）。在 M1"意图"之上新增"路由"：
 * 挑战/战斗让位 → 外部指令路由到工具 → 威胁 → 主人陪伴 → 心智基调 → 静默。
 *
 * <p>规则确定、可读、可单测，是未来换 LLM 分类器之前的确定性底座。</p>
 */
public final class AutonomyIntentionClassifier implements AgentIntentionClassifier {

    @Override
    public Classification classify(AgentFrame frame, List<AgentObservation> observations) {
        // P5：附带最近教训规则摘要（若有），让决策原因"记得住"结构化教训。
        String lessons = findLessonRulesSummary(observations);

        // 0. 挑战 / 战斗态：让位给既有状态机，不路由。
        if (frame.isChallengeActive() || frame.isBattleActive()) {
            return Classification.world(AgentIntention.DEFER, "挑战/战斗进行中，交给既有层" + lessons);
        }

        // 1. 外部工具指令优先路由（M2 工具路由入口）。
        String toolId = findDirectiveTool(observations);
        if (toolId != null) {
            return Classification.tool(AgentIntention.COMPUTE, toolId, "存在待处理工具请求: " + toolId);
        }

        // 2. 威胁优先。
        if (frame.nearbyThreats() > 0) {
            return Classification.world(AgentIntention.ATTEND,
                    "周边有 " + frame.nearbyThreats() + " 个敌对怪物，倾向看护" + lessons);
        }

        // 3. 主人在附近 → 随心智基调陪伴 / 观察。
        if (frame.isOwnerWithin(64.0D)) {
            AgentIntention base = baseIntentionFor(frame.mindState());
            return Classification.world(base,
                    "主人在附近，按心智(" + frame.mindState() + ")采取 " + base + lessons);
        }

        // 4. 无主人 / 主人远 → 漫游或静止。
        return Classification.world(AgentIntention.WANDER, "无可陪伴对象，保持漫游/静默" + lessons);
    }

    /** 从观测集提取记忆传感器注入的教训规则摘要（没有则返回空串）。 */
    private static String findLessonRulesSummary(List<AgentObservation> observations) {
        for (AgentObservation observation : observations) {
            if (observation.kind() == AgentSenseKind.MEMORY_BACKLOG
                    && observation.summary() != null
                    && observation.summary().contains("教训规则")) {
                int idx = observation.summary().indexOf("教训规则");
                String suffix = observation.summary().substring(idx);
                return " | " + suffix.trim();
            }
        }
        return "";
    }

    /** 从观测集中找到第一条携带工具指向的指令观测；没有则返回 null。 */
    private static String findDirectiveTool(List<AgentObservation> observations) {
        for (AgentObservation observation : observations) {
            if (observation.kind() == AgentSenseKind.DIRECTIVE && observation.toolId() != null) {
                return observation.toolId();
            }
        }
        return null;
    }

    /** 心智状态 → 基调意图。 */
    private static AgentIntention baseIntentionFor(MindState state) {
        switch (state) {
            case PROTECTOR:
            case MONSTER_KING:
                return AgentIntention.ATTEND;
            case JUDGE:
            case PRANKSTER:
                return AgentIntention.INTERACT;
            case MAINTAINER:
                return AgentIntention.REMEDIATE;
            case GLITCH_LORD:
                return AgentIntention.INVESTIGATE;
            case REMINISCING:
                return AgentIntention.WANDER;
            case OBSERVER:
            default:
                return AgentIntention.WATCH;
        }
    }
}