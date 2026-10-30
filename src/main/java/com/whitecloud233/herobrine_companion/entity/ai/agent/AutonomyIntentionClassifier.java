package com.whitecloud233.herobrine_companion.entity.ai.agent;

import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork.MindState;

import java.util.List;

/**
 * 规则驱动的<b>自主</b>意图分类器。
 *
 * <p>显式工具请求不再经过这里（交接 A1）：它由
 * {@link com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolRequestService}
 * 直接执行。本类只决定"没人指使的时候，Herobrine 自己想干什么"，因此规则里不再出现
 * 工具路由分支，也不存在"战斗态把玩家指令一起吞掉"的历史坑。</p>
 *
 * <p>规则确定、可读、可单测，是未来换 LLM 分类器之前的确定性底座。</p>
 */
public final class AutonomyIntentionClassifier implements AgentIntentionClassifier {

    @Override
    public Classification classify(AgentFrame frame, List<AgentObservation> observations) {
        // P5：附带最近教训规则摘要（若有），让决策原因"记得住"结构化教训。
        String lessons = findLessonRulesSummary(observations);

        // 1. 挑战 / 战斗态：让位给既有状态机，不路由（仅作用于自主行为）。
        if (frame.isChallengeActive() || frame.isBattleActive()) {
            return Classification.world(AgentIntention.DEFER, "挑战/战斗进行中，交给既有层" + lessons);
        }

        // 2. 威胁优先。
        if (frame.nearbyThreats() > 0) {
            return Classification.world(AgentIntention.ATTEND,
                    "周边有 " + frame.nearbyThreats() + " 个敌对怪物，倾向看护" + lessons);
        }

        // 3. 同行者在附近 → 随心智基调陪伴 / 观察。
        if (frame.isOwnerWithin(64.0D)) {
            AgentIntention base = baseIntentionFor(frame.mindState());
            return Classification.world(base,
                    "同行者在附近，按心智(" + frame.mindState() + ")采取 " + base + lessons);
        }

        // 4. 未结伴 / 同行者远 → 漫游或静止。
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
