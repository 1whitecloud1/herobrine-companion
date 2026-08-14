package com.whitecloud233.herobrine_companion.entity.ai.agent;

import java.util.List;

/**
 * 意图分类器抽象（依赖倒置）。HeroAgent 只依赖本接口做"感知 → 意图 + 路由"。
 *
 * <p>M1 只产意图；M2 扩展为同时给出路由目标 {@link AgentChannel} 与建议工具 {@code suggestedToolId}。
 * M2 提供基于规则的 {@link AutonomyIntentionClassifier}；后续可换 LLM/语义驱动实现，核心循环不变。</p>
 */
public interface AgentIntentionClassifier {

    /**
     * 基于状态快照与本次感知观测集产出当前最可能的意图。
     *
     * @param frame        本次快照
     * @param observations 感知桶中取出的观测（只读）
     * @return 分类结果（意图 + 路由 + 建议工具 + 理由）
     */
    Classification classify(AgentFrame frame, List<AgentObservation> observations);

    /** 分类结果：意图、路由目标、建议工具（路由为 TOOL 时非空）、策略理由。 */
    record Classification(AgentIntention intention, AgentChannel channel, String suggestedToolId, String reason) {

        /** 世界 / 空闲类别的便捷构造（无工具指向）。 */
        public static Classification world(AgentIntention intention, String reason) {
            return new Classification(intention, AgentChannel.WORLD_BEHAVIOR, null, reason);
        }

        /** 工具调用类别的便捷构造。 */
        public static Classification tool(AgentIntention intention, String toolId, String reason) {
            return new Classification(intention, AgentChannel.TOOL_INVOCATION, toolId, reason);
        }
    }
}