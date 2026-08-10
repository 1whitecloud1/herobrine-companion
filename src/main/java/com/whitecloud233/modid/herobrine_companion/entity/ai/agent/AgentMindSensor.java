package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork.MindState;

/**
 * 心智姿态传感器：把 {@link AgentFrame} 中的宏观心智状态翻译成一条观测。
 *
 * <p>单一职责：只反映"此刻 Hero 处于什么心智姿态"，不触发任何对话或行为。</p>
 */
public final class AgentMindSensor implements AgentSensor {

    @Override
    public void collect(AgentFrame frame, AgentPerceptionCollector sink) {
        sink.add(AgentObservation.of(
                AgentSenseKind.MIND_STATE,
                source(),
                frame.gameTime(),
                "心智状态: " + frame.mindState(),
                mindWeight(frame.mindState())));
    }

    private static int mindWeight(MindState state) {
        // 越"主动作为"的态度权重越高，便于分类器优先采纳。
        switch (state) {
            case PROTECTOR:
            case JUDGE:
            case MONSTER_KING:
            case GLITCH_LORD:
                return 2;
            case MAINTAINER:
            case PRANKSTER:
            case REMINISCING:
                return 1;
            default:
                return 0;
        }
    }

    @Override
    public String source() {
        return "mind";
    }
}