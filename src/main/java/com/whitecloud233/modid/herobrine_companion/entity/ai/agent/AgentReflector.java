package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;

import java.util.List;

/**
 * 反思 / 学习抽象（依赖倒置）。HeroAgent 在每周期记录决策后调用，把"结果"回填记忆与心智。
 *
 * <p>M4 提供温和的规则实现 {@link DefaultAgentReflector}；后续可扩展为更复杂的自我评估。</p>
 */
public interface AgentReflector {

    /**
     * 收到一次已记录的决策，据此更新记忆与心智分数。
     *
     * @param hero     本次循环的 Hero
     * @param decision 刚记录的决策（含是否生效、结果文本）
     * @return 本次写下的教训（供决策日志展示）
     */
    Reflection reflect(HeroEntity hero, AgentDecision decision);

    /** 反思结果：本次写下的 lesson 列表。 */
    record Reflection(List<String> lessons) {

        public static Reflection none() {
            return new Reflection(List.of());
        }

        public boolean isEmpty() {
            return lessons.isEmpty();
        }
    }
}