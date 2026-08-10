package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import java.util.List;

/**
 * 单次 Agent 循环的决策记录（不可变），用于"解释自己"的决策日志。
 */
public record AgentDecision(
        long gameTime,
        List<AgentObservation> observations,
        AgentIntention intention,
        AgentChannel channel,
        String rationale,
        String actionNote,
        boolean acted,
        String outcome
) {
    /** 压缩为单行可读文本。 */
    public String line() {
        return String.format(
                "[t=%d] 意图=%s/%s 理由[%s] 计划[%s] 结果=%s 观测%d条",
                gameTime, intention, channel, rationale, actionNote, outcome, observations.size());
    }
}