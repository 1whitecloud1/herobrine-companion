package com.whitecloud233.herobrine_companion.entity.ai.agent;

/**
 * 感知来源分类。用于给 {@link AgentObservation} 打标签，方便后续按来源聚合、过滤、裁剪上下文。
 *
 * <p>单一职责：只定义"感知出自哪里"，不携带任何数据或逻辑。</p>
 */
public enum AgentSenseKind {

    /** 来自心智状态机（mind state）的宏观姿态。 */
    MIND_STATE,

    /** 来自玩家 / 主人状态的感知。 */
    OWNER_PRESENCE,

    /** 来自周围威胁（怪物、火、落雷等危险信号）的感知。 */
    WORLD_THREAT,

    /** 来自周围实体/方块的分布密度感知。 */
    WORLD_POPULATION,

    /** 来自长期记忆 / 待办积压（M4 后接 HeroMemory）。 */
    MEMORY_BACKLOG,

    /** 来自当前目标实体（P5）。 */
    TARGET,

    /** 来自 Hero 自身装备/物品（P5，如主手武器 → 可用技能）。 */
    INVENTORY,

    /** 来自环境状态（天气等）（P5）。 */
    ENVIRONMENT,

    /** 来自对话队列的意图（M2 后接入对话系统）。 */
    DIALOGUE_QUEUE,

    /** 来自外部注入的指令（如工具调用请求），M2 的工具路由入口。 */
    DIRECTIVE,

    /** 未能归类的杂散输入。 */
    MISC
}