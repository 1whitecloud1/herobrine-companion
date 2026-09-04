package com.whitecloud233.herobrine_companion.config;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ... [中间的 1~8 武器和技能的配置保持你的原样不变] ...
    public static final ModConfigSpec.BooleanValue POEM_OF_THE_END_EXPLOSION = BUILDER.comment("终末之诗在破境状态下是否启用爆炸").define("poemOfTheEndExplosion_v2", false);
    public static final ModConfigSpec.BooleanValue HERO_KING_AURA_ENABLED = BUILDER.comment("是否启用 Herobrine 的怪物臣服AI").define("heroKingAuraEnabled_v2", true);
    public static final ModConfigSpec.BooleanValue HERO_BLOCK_RESTORATION = BUILDER.comment("Herobrine是否自动修复被破坏的方块").define("heroBlockRestoration_v2", false);
    public static final ModConfigSpec.BooleanValue HERO_CLEAN_ITEMS = BUILDER.comment("Herobrine是否自动清理地上的掉落物").define("heroCleanItems_v2", false);
    public static final ModConfigSpec.BooleanValue HERO_LEAF_VANISH_ENABLED = BUILDER.comment("Herobrine是否允许在恶作剧状态下拔掉附近树叶").define("heroLeafVanishEnabled", true);
    public static final ModConfigSpec.BooleanValue CLEAVE_SKILL_ENABLED = BUILDER.comment("是否启用镰刀的 5 键存档毁灭术").define("cleaveSkillEnabled", false);
    public static final ModConfigSpec.BooleanValue SOUL_BOUND_PACT_ENABLED = BUILDER.comment("是否启用魂缚之契物品").define("soulBoundPactEnabled", true);
    public static final ModConfigSpec.BooleanValue ABYSSAL_GAZE_ENABLED = BUILDER.comment("是否启用幽邃之视物品").define("abyssalGazeEnabled", true);
    public static final ModConfigSpec.BooleanValue TRANSCENDENCE_PERMIT_ENABLED = BUILDER.comment("是否启用凌越之允物品").define("transcendencePermitEnabled", true);

    // ==========================================
    // Herobrine agent：长期记忆 / 自主行为 / 工具确认
    // ==========================================
    public static final ModConfigSpec.BooleanValue HERO_MEMORY_ENABLED = BUILDER
            .comment("Whether the Herobrine agent long-term memory and reflection learning are enabled")
            .comment("是否启用 Herobrine 长期记忆与反思学习")
            .define("heroMemoryEnabled", true);

    public static final ModConfigSpec.BooleanValue HERO_AUTONOMY_ENABLED = BUILDER
            .comment("Whether the Herobrine agent may autonomously plan and execute real tasks (repair/inspect) on its own initiative")
            .comment("Herobrine agent 是否可自主规划并执行真实任务（修复/探查）")
            .define("heroAutonomyEnabled", true);

    public static final ModConfigSpec.BooleanValue AGENT_TOOL_CONFIRMATION = BUILDER
            .comment("Whether agent tools requiring confirmation show an approval screen before executing")
            .comment("需要确认的 agent 工具是否在执行前弹出确认屏（关闭后视为已确认，回到无 UI 时代）")
            .define("agentToolConfirmation", true);

    public static final ModConfigSpec.BooleanValue AGENT_TOOL_RESULT_FEEDBACK = BUILDER
            .comment("Whether agent tool execution results are fed back to the LLM via a synthesis call")
            .comment("agent 工具执行结果是否经合成调用回喂给 LLM")
            .define("agentToolResultFeedback", true);

    public static final ModConfigSpec.BooleanValue HERO_SKILL_SESSION = BUILDER
            .comment("Whether the hero_use_skill tool tracks skill cooldown and auto-exits battle mode after a forced skill ends")
            .comment("hero_use_skill 工具是否启用技能冷却与强制技能结束后的战斗态退出")
            .define("heroSkillSession", true);

    // ==========================================
    // 全知视觉 (AI环境感知) 的开关与间隔
    // ==========================================
    public static final ModConfigSpec.BooleanValue AI_VISION_ENABLED = BUILDER
            .comment("是否启用全知视觉（AI感知）")
            .define("aiVisionEnabled", true);

    public static final ModConfigSpec.IntValue AI_VISION_INTERVAL = BUILDER
            .comment("全知视觉的每次主动发话最小间隔（秒），建议在 10 ~ 300 之间")
            .defineInRange("aiVisionInterval", 30, 5, 600);

    public static final ModConfigSpec.BooleanValue AWAKENED_MOB_AI_DIALOGUE_ENABLED = BUILDER
            .comment("是否启用觉醒怪物的AI对话")
            .define("awakenedMobAiDialogueEnabled", true);

    public static final ModConfigSpec.IntValue AWAKENED_DIALOGUE_MIN_INTERVAL = BUILDER
            .comment("所有觉醒对话共享的最小 LLM 调用间隔（秒，任意说话者之间），防止触发 API 频率限制；冷却期间改用预设台词")
            .defineInRange("awakenedDialogueMinIntervalSeconds", 30, 5, 600);

    public static final ModConfigSpec.IntValue AWAKENED_WORLD_CAP = BUILDER
            .comment("Max total awakened mobs per dimension (world). Design doc 5.2 recommends 20 ~ 40. Awakened mobs keep their state until they die or leave the dimension; new awakenings are refused once the cap is reached")
            .comment("单世界（维度）觉醒怪物总量上限（设计文档 5.2 建议 20 ~ 40）。达到上限后不再产生新的觉醒怪；已觉醒怪物保持觉醒直到死亡或离开本维度")
            .defineInRange("awakenedWorldCap", 30, 20, 40);

    public static final ModConfigSpec.IntValue AWAKENED_PLAYER_NEAR_CAP = BUILDER
            .comment("Max awakened mobs simultaneously near a single player. Design doc 5.2 recommends 2 ~ 4")
            .comment("单个玩家附近同时存在的觉醒怪物数量上限（设计文档 5.2 建议 2 ~ 4）")
            .defineInRange("awakenedPlayerNearCap", 3, 2, 4);

    public static final ModConfigSpec.IntValue AWAKENED_PLAYER_NEAR_RADIUS = BUILDER
            .comment("Radius (in blocks) around a player used to count nearby awakened mobs against the near cap")
            .comment("计算“玩家附近觉醒怪数量”的半径（格）")
            .defineInRange("awakenedPlayerNearRadius", 64, 16, 128);

    // 【新增】恢复更新检查器开关
    public static final ModConfigSpec.BooleanValue ENABLE_UPDATE_CHECKER = BUILDER
            .comment("加入世界时是否在 Modrinth 检查模组更新")
            .define("enableUpdateChecker", true);

    public static final ModConfigSpec.BooleanValue DESTRUCTION_GOD_TERRAIN_DAMAGE_ENABLED = BUILDER
            .comment("Whether Destruction God Herobrine terrain destruction skills can really break blocks")
            .comment("毁灭之神 Herobrine 的地形破坏技能是否允许真实破坏方块")
            .define("destructionGodTerrainDamageEnabled", true);

    public static final ModConfigSpec.ConfigValue<String> DESTRUCTION_GOD_TERRAIN_DAMAGE_MODE = BUILDER
            .comment("Terrain destruction mode for Destruction God Herobrine: visual / safe / divine")
            .comment("毁灭之神地形破坏模式：visual（纯视觉）/ safe（安全破坏）/ divine（神性破坏）")
            .define("destructionGodTerrainDamageMode", "divine");

    public static final ModConfigSpec.BooleanValue DESTRUCTION_GOD_BREAK_CONTAINERS = BUILDER
            .comment("Whether Destruction God Herobrine can break container or block-entity blocks")
            .comment("毁灭之神是否可破坏容器和带方块实体的方块")
            .define("destructionGodBreakContainers", true);

    public static final ModConfigSpec.BooleanValue DESTRUCTION_GOD_ARENA_RESTORE = BUILDER
            .comment("Whether terrain broken by Destruction God Herobrine should restore itself after a while")
            .comment("毁灭之神造成的地形破坏是否在一段时间后自动恢复")
            .define("destructionGodArenaRestore", false);

    public static final ModConfigSpec.IntValue DESTRUCTION_GOD_MAX_BROKEN_BLOCKS_PER_TICK = BUILDER
            .comment("Maximum number of blocks Destruction God Herobrine terrain skills may break per server tick")
            .comment("毁灭之神地形技能每服务器刻最多破坏的方块数")

            .defineInRange("destructionGodMaxBrokenBlocksPerTick", 4096, 64, 4096);

    public static final ModConfigSpec.BooleanValue DESTRUCTION_GOD_FINAL_PHASE_WORLD_COLLAPSE = BUILDER
            .comment("Whether Destruction God Herobrine may trigger final phase world collapse style terrain destruction")
            .comment("毁灭之神最终阶段是否允许触发世界崩塌式地形破坏")
            .define("destructionGodFinalPhaseWorldCollapse", true);


    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean poemOfTheEndExplosion;
    public static boolean heroKingAuraEnabled;
    public static boolean heroBlockRestoration;
    public static boolean heroCleanItems;
    public static boolean heroLeafVanishEnabled;
    public static boolean cleaveSkillEnabled;
    public static boolean soulBoundPactEnabled;
    public static boolean abyssalGazeEnabled;
    public static boolean transcendencePermitEnabled;
    public static boolean destructionGodTerrainDamageEnabled;
    public static String destructionGodTerrainDamageMode;
    public static boolean destructionGodBreakContainers;
    public static boolean destructionGodArenaRestore;
    public static int destructionGodMaxBrokenBlocksPerTick;
    public static boolean destructionGodFinalPhaseWorldCollapse;

    public static boolean heroMemoryEnabled;
    public static boolean heroAutonomyEnabled;
    public static boolean agentToolConfirmation;
    public static boolean agentToolResultFeedback;
    public static boolean heroSkillSession;

    public static boolean aiVisionEnabled;
    public static int aiVisionInterval;
    public static boolean awakenedMobAiDialogueEnabled;
    public static int awakenedDialogueMinIntervalSeconds;
    public static int awakenedWorldCap;
    public static int awakenedPlayerNearCap;
    public static int awakenedPlayerNearRadius;

    // 【新增】更新检查器变量
    public static boolean enableUpdateChecker;

    public static void onLoad(final ModConfigEvent event) {
        poemOfTheEndExplosion = POEM_OF_THE_END_EXPLOSION.get();
        heroKingAuraEnabled = HERO_KING_AURA_ENABLED.get();
        heroBlockRestoration = HERO_BLOCK_RESTORATION.get();
        heroCleanItems = HERO_CLEAN_ITEMS.get();
        heroLeafVanishEnabled = HERO_LEAF_VANISH_ENABLED.get();
        cleaveSkillEnabled = CLEAVE_SKILL_ENABLED.get();
        soulBoundPactEnabled = SOUL_BOUND_PACT_ENABLED.get();
        abyssalGazeEnabled = ABYSSAL_GAZE_ENABLED.get();
        transcendencePermitEnabled = TRANSCENDENCE_PERMIT_ENABLED.get();

        heroMemoryEnabled = HERO_MEMORY_ENABLED.get();
        heroAutonomyEnabled = HERO_AUTONOMY_ENABLED.get();
        agentToolConfirmation = AGENT_TOOL_CONFIRMATION.get();
        agentToolResultFeedback = AGENT_TOOL_RESULT_FEEDBACK.get();
        heroSkillSession = HERO_SKILL_SESSION.get();

        aiVisionEnabled = AI_VISION_ENABLED.get();
        aiVisionInterval = AI_VISION_INTERVAL.get();
        awakenedMobAiDialogueEnabled = AWAKENED_MOB_AI_DIALOGUE_ENABLED.get();
        awakenedDialogueMinIntervalSeconds = AWAKENED_DIALOGUE_MIN_INTERVAL.get();
        awakenedWorldCap = AWAKENED_WORLD_CAP.get();
        awakenedPlayerNearCap = AWAKENED_PLAYER_NEAR_CAP.get();
        awakenedPlayerNearRadius = AWAKENED_PLAYER_NEAR_RADIUS.get();

        destructionGodTerrainDamageEnabled = DESTRUCTION_GOD_TERRAIN_DAMAGE_ENABLED.get();
        destructionGodTerrainDamageMode = DESTRUCTION_GOD_TERRAIN_DAMAGE_MODE.get();
        destructionGodBreakContainers = DESTRUCTION_GOD_BREAK_CONTAINERS.get();
        destructionGodArenaRestore = DESTRUCTION_GOD_ARENA_RESTORE.get();
        destructionGodMaxBrokenBlocksPerTick = DESTRUCTION_GOD_MAX_BROKEN_BLOCKS_PER_TICK.get();
        destructionGodFinalPhaseWorldCollapse = DESTRUCTION_GOD_FINAL_PHASE_WORLD_COLLAPSE.get();

        // 【新增】获取更新检查器配置
        enableUpdateChecker = ENABLE_UPDATE_CHECKER.get();

        LOGGER.info("Herobrine Companion Config Loaded: Explosion={}, Aura={}, BlockRestoration={}, CleanItems={}, LeafVanish={}, CleaveSkill={}, DGTerrainEnabled={}, DGTerrainMode={}, DGBreakContainers={}, DGArenaRestore={}, DGMaxBreakPerTick={}, DGWorldCollapse={}, Pact={}, Gaze={}, Permit={}, AIVision={}, AIInterval={}, AwakenedMobAI={}",
                poemOfTheEndExplosion, heroKingAuraEnabled, heroBlockRestoration, heroCleanItems, heroLeafVanishEnabled, cleaveSkillEnabled,
                destructionGodTerrainDamageEnabled, destructionGodTerrainDamageMode, destructionGodBreakContainers,
                destructionGodArenaRestore, destructionGodMaxBrokenBlocksPerTick, destructionGodFinalPhaseWorldCollapse,
                soulBoundPactEnabled, abyssalGazeEnabled, transcendencePermitEnabled, aiVisionEnabled, aiVisionInterval,
                awakenedMobAiDialogueEnabled);
    }
}
