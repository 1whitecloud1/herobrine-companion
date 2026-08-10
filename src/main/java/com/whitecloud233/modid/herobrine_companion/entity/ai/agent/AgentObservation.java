package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

/**
 * 单条感知观测。不可变值对象，是 Agent 感知层的"最小事实单元"。
 *
 * <p>{@code weight} 表示该条观测的相对重要性（注意力权重），用于后续上下文裁剪与意图打分。
 * {@code toolId}（可空）携带该条观测指向的工具请求（M2 工具路由用），非工具观测为 null。
 * 本包内所有阶段（分类 / 规划 / 执行 / 记录）都只消费这类值对象，不反向修改。</p>
 */
public record AgentObservation(AgentSenseKind kind, String source, long gameTime, String summary, int weight,
                               String toolId) {

    /** 便捷构造：默认权重 1，无工具指向。 */
    public static AgentObservation of(AgentSenseKind kind, String source, long gameTime, String summary) {
        return new AgentObservation(kind, source, gameTime, summary, 1, null);
    }

    /** 便捷构造：显式权重（注意力权重），无工具指向。 */
    public static AgentObservation of(AgentSenseKind kind, String source, long gameTime, String summary, int weight) {
        return new AgentObservation(kind, source, gameTime, summary, weight, null);
    }

    /** 便捷构造：指向某个工具请求（工具路由观测用）。 */
    public static AgentObservation directive(String source, long gameTime, String summary, String toolId) {
        return new AgentObservation(AgentSenseKind.DIRECTIVE, source, gameTime, summary, 3, toolId);
    }

    @Override
    public String toString() {
        String tool = toolId == null ? "" : " ->tool:" + toolId;
        return "[" + kind + "|" + source + "|" + gameTime + "] " + summary + tool + " (w=" + weight + ")";
    }
}