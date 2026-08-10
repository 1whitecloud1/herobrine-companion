package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

/**
 * 装备传感器（P5）：把 Hero 主手武器暴露给感知层——供"可用夜幕技能"感知，
 * 是 {@code hero_use_skill} 技能可用的前置信息。
 *
 * <p><b>单一职责</b>：只反映"主手拿的什么"，数据来自 {@link AgentFrame}。</p>
 */
public final class AgentInventorySensor implements AgentSensor {

    @Override
    public void collect(AgentFrame frame, AgentPerceptionCollector sink) {
        if (!frame.mainHandItemPath().isEmpty()) {
            sink.add(AgentObservation.of(
                    AgentSenseKind.INVENTORY,
                    source(),
                    frame.gameTime(),
                    "主手武器: " + frame.mainHandItemPath(),
                    1));
        }
    }

    @Override
    public String source() {
        return "inventory";
    }
}
