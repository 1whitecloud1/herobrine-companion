package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

/**
 * 毁灭之神地形破坏的固定强度。
 *
 * <p>这些数值原先来自 common 配置（destructionGodTerrainDamageEnabled / Mode /
 * BreakContainers / MaxBrokenBlocksPerTick / FinalPhaseWorldCollapse）。
 * 它们现在固定写死，不再出现在 toml 与设置界面中，避免玩家把 boss 技能调成“完全无害”或
 * “每刻百万方块”导致卡服。</p>
 *
 * <p><b>单一职责</b>：只存放常量，不参与判定逻辑。</p>
 */
final class DestructionTuning {
    /** 地形破坏是否真实生效（对应旧 destructionGodTerrainDamageEnabled=true）。 */
    static final boolean TERRAIN_DAMAGE_ENABLED = true;

    /** 破坏模式：神性破坏，允许拆掉玩家建筑而不仅是自然地形。 */
    static final DestructionMode MODE = DestructionMode.DIVINE;

    /** 是否允许破坏容器/带方块实体的方块。 */
    static final boolean BREAK_CONTAINERS = true;

    /** 每服务器刻最多破坏的方块数。 */
    static final int MAX_BROKEN_BLOCKS_PER_TICK = 4096;

    /** 是否允许终局世界崩塌式地形破坏。 */
    static final boolean FINAL_PHASE_WORLD_COLLAPSE = true;

    private DestructionTuning() {
    }
}
