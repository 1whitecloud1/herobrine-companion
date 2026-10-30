package com.whitecloud233.herobrine_companion.entity.ai.agent;

import java.util.List;

/**
 * 单次 Agent 循环的决策记录（不可变），用于"解释自己"的决策日志。
 *
 * <p>{@code worldFailure()} 区分"世界事实失败"与"什么都没做"（交接 A3）：
 * 反思只允许把世界事实写进经历，等待确认 / 软件拒绝不进入这条路。</p>
 */
public record AgentDecision(
        long gameTime,
        List<AgentObservation> observations,
        AgentIntention intention,
        AgentChannel channel,
        String rationale,
        String actionNote,
        boolean acted,
        String outcome,
        boolean worldFailure
) {

    public AgentDecision {
        observations = observations == null ? List.of() : List.copyOf(observations);
    }

    /** 兼容旧调用点：未标注世界失败时视为"非世界失败"。 */
    public AgentDecision(long gameTime, List<AgentObservation> observations, AgentIntention intention,
                         AgentChannel channel, String rationale, String actionNote,
                         boolean acted, String outcome) {
        this(gameTime, observations, intention, channel, rationale, actionNote, acted, outcome, false);
    }

    /** 压缩为单行可读文本。 */
    public String line() {
        return String.format(
                "[t=%d] 意图=%s/%s 理由[%s] 计划[%s] 结果=%s 观测%d条",
                gameTime, intention, channel, rationale, actionNote, outcome, observations.size());
    }
}
