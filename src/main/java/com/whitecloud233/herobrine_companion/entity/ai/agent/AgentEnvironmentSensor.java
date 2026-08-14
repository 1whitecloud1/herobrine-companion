package com.whitecloud233.herobrine_companion.entity.ai.agent;

/**
 * 环境传感器（P5）：把环境状态（天气等）暴露给感知层。
 *
 * <p><b>单一职责</b>：只反映"世界此刻的环境信号"，数据来自 {@link AgentFrame}。</p>
 */
public final class AgentEnvironmentSensor implements AgentSensor {

    @Override
    public void collect(AgentFrame frame, AgentPerceptionCollector sink) {
        if (frame.isRaining()) {
            sink.add(AgentObservation.of(
                    AgentSenseKind.ENVIRONMENT,
                    source(),
                    frame.gameTime(),
                    "正在下雨",
                    1));
        }
    }

    @Override
    public String source() {
        return "environment";
    }
}
