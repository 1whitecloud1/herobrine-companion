package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

/**
 * Agent 当前"想要做什么"的高层意图。
 *
 * <p>意图是感知与行动计划之间的语义桥梁：分类器产出意图，规划器把意图翻译成行动计划。</p>
 */
public enum AgentIntention {
    /** 看护 / 陪伴同行者。 */
    ATTEND,
    /** 好奇地观察周围。 */
    WATCH,
    /** 修复世界异常 / 善后。 */
    REMEDIATE,
    /** 探查某处异常 / 可疑点。 */
    INVESTIGATE,
    /** 与环境或生物主动互动。 */
    INTERACT,
    /** 漫游探索。 */
    WANDER,
    /** 处理计算 / 工具类请求（M2 接入）。 */
    COMPUTE,
    /** 把控制权让给既有层（挑战 / 战斗），当前不干预。 */
    DEFER,
    /** 无事可做，保持静默。 */
    IDLE
}