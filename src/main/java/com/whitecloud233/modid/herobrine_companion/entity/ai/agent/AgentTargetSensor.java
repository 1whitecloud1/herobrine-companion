package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

/**
 * 目标传感器（P5）：把当前目标实体暴露给感知层。
 *
 * <p><b>单一职责</b>：只反映"Hero 此刻正盯着谁"，数据来自 {@link AgentFrame}（无额外扫描）。</p>
 */
public final class AgentTargetSensor implements AgentSensor {

    @Override
    public void collect(AgentFrame frame, AgentPerceptionCollector sink) {
        if (frame.targetUuid() != null) {
            sink.add(AgentObservation.of(
                    AgentSenseKind.TARGET,
                    source(),
                    frame.gameTime(),
                    "目标实体: " + frame.targetType(),
                    2));
        }
    }

    @Override
    public String source() {
        return "target";
    }
}
