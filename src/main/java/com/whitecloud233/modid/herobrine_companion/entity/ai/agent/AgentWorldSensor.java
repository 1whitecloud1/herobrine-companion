package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

/**
 * 世界感知传感器：把快照里预计算好的环境事实（威胁、同行者距离）翻译成观测。
 *
 * <p>与 {@link AgentFrame} 的分工：快照负责"读一次实体/世界"，本传感器负责"把读数表达为观测"。
 * 因此这里不做任何重复扫描，性能代价为零。</p>
 */
public final class AgentWorldSensor implements AgentSensor {

    @Override
    public void collect(AgentFrame frame, AgentPerceptionCollector sink) {
        if (frame.isOwnerWithin(64.0D)) {
            sink.add(AgentObservation.of(
                    AgentSenseKind.OWNER_PRESENCE,
                    source(),
                    frame.gameTime(),
                    "同行者在8格内, 距离平方=" + String.format("%.1f", frame.ownerDistanceSqr()),
                    1));
        }

        if (frame.nearbyThreats() > 0) {
            sink.add(AgentObservation.of(
                    AgentSenseKind.WORLD_THREAT,
                    source(),
                    frame.gameTime(),
                    "周边有 " + frame.nearbyThreats() + " 个敌对怪物",
                    Math.min(3, 1 + frame.nearbyThreats() / 5)));
        }
    }

    @Override
    public String source() {
        return "world";
    }
}