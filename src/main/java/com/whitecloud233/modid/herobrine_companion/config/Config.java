package com.whitecloud233.modid.herobrine_companion.config;

import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue POEM_OF_THE_END_EXPLOSION = BUILDER
            .comment("Whether the Poem of the End weapon causes explosions in its broken state")
            .comment("终末之诗在破境状态下是否启用爆炸")
            .define("poemOfTheEndExplosion_v2", false);

    public static final ForgeConfigSpec.BooleanValue HERO_KING_AURA_ENABLED = BUILDER
            .comment("Whether the Hero King Aura goal is enabled for Herobrine")
            .comment("是否启用 Herobrine 的怪物臣服AI")
            .define("heroKingAuraEnabled_v4", false);

    public static final ForgeConfigSpec.BooleanValue HERO_BLOCK_RESTORATION = BUILDER
            .comment("Whether Herobrine automatically restores broken blocks")
            .comment("Herobrine是否自动修复被破坏的方块")
            .define("heroBlockRestoration_v2", false);

    public static final ForgeConfigSpec.BooleanValue HERO_CLEAN_ITEMS = BUILDER
            .comment("Whether Herobrine automatically cleans up dropped items")
            .comment("Herobrine是否自动清理地上的掉落物")
            .define("heroCleanItems_v2", false);

    public static final ForgeConfigSpec.BooleanValue CLEAVE_SKILL_ENABLED = BUILDER
            .comment("Whether the World Cleave skill (5 key) is enabled")
            .comment("是否启用镰刀的 5 键存档毁灭术")
            .define("cleaveSkillEnabled", false);

    public static final ForgeConfigSpec.BooleanValue DESTRUCTION_GOD_TERRAIN_DAMAGE_ENABLED = BUILDER
            .comment("Whether Destruction God Herobrine terrain destruction skills can really break blocks")
            .comment("毁灭之神 Herobrine 的地形破坏技能是否允许真实破坏方块")
            .define("destructionGodTerrainDamageEnabled", true);

    public static final ForgeConfigSpec.ConfigValue<String> DESTRUCTION_GOD_TERRAIN_DAMAGE_MODE = BUILDER
            .comment("Terrain destruction mode for Destruction God Herobrine: visual / safe / divine")
            .comment("毁灭之神地形破坏模式：visual（纯视觉）/ safe（安全破坏）/ divine（神性破坏）")
            .define("destructionGodTerrainDamageMode", "divine");

    public static final ForgeConfigSpec.BooleanValue DESTRUCTION_GOD_BREAK_CONTAINERS = BUILDER
            .comment("Whether Destruction God Herobrine can break container or block-entity blocks")
            .comment("毁灭之神是否可破坏容器和带方块实体的方块")
            .define("destructionGodBreakContainers", true);

    public static final ForgeConfigSpec.BooleanValue DESTRUCTION_GOD_ARENA_RESTORE = BUILDER
            .comment("Whether terrain broken by Destruction God Herobrine should restore itself after a while")
            .comment("毁灭之神造成的地形破坏是否在一段时间后自动恢复")
            .define("destructionGodArenaRestore", false);

    public static final ForgeConfigSpec.IntValue DESTRUCTION_GOD_MAX_BROKEN_BLOCKS_PER_TICK = BUILDER
            .comment("Maximum number of blocks Destruction God Herobrine terrain skills may break per server tick")
            .comment("毁灭之神地形技能每服务器刻最多破坏的方块数")

            .defineInRange("destructionGodMaxBrokenBlocksPerTick", 4096, 64, 4096);

    public static final ForgeConfigSpec.BooleanValue DESTRUCTION_GOD_FINAL_PHASE_WORLD_COLLAPSE = BUILDER
            .comment("Whether Destruction God Herobrine may trigger final phase world collapse style terrain destruction")
            .comment("毁灭之神最终阶段是否允许触发世界崩塌式地形破坏")
            .define("destructionGodFinalPhaseWorldCollapse", true);

    public static final ForgeConfigSpec.BooleanValue SOUL_BOUND_PACT_ENABLED = BUILDER
            .comment("Whether the Soul Bound Pact item is enabled")
            .comment("是否启用魂缚之契物品")
            .define("soulBoundPactEnabled_v2", true);

    public static final ForgeConfigSpec.BooleanValue ABYSSAL_GAZE_ENABLED = BUILDER
            .comment("Whether the Abyssal Gaze item is enabled")
            .comment("是否启用幽邃之视物品")
            .define("abyssalGazeEnabled_v2", true);

    public static final ForgeConfigSpec.BooleanValue TRANSCENDENCE_PERMIT_ENABLED = BUILDER
            .comment("Whether the Transcendence Permit item is enabled")
            .comment("是否启用凌越之允物品")
            .define("transcendencePermitEnabled_v2", true);

    public static final ForgeConfigSpec.BooleanValue AI_VISION_ENABLED = BUILDER
            .comment("Whether the Omniscient Vision (AI environment perception) is enabled")
            .comment("是否启用全知视觉（AI计算机视觉环境感知）")
            .define("aiVisionEnabled", true);

    public static final ForgeConfigSpec.IntValue AI_VISION_INTERVAL = BUILDER
            .comment("The global minimum interval (in seconds) between AI observations and speech")
            .comment("全知视觉的每次主动发话最小间隔（秒），建议在 10 ~ 300 之间")
            .defineInRange("aiVisionInterval", 30, 5, 600);

    // 【新增】AI 语言风格配置
    public static final ForgeConfigSpec.ConfigValue<String> AI_LANGUAGE_STYLE = BUILDER
            .comment("The language style and tone for Herobrine's AI responses")
            .comment("Herobrine的AI语言风格与语气")
            .define("aiLanguageStyle_v1", "");
    // 【新增】更新检查器配置
    public static final ForgeConfigSpec.BooleanValue ENABLE_UPDATE_CHECKER = BUILDER
            .comment("Whether to check for mod updates on Modrinth when joining a world")
            .comment("加入世界时是否在 Modrinth 检查模组更新")
            .define("enableUpdateChecker", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();
    public static boolean poemOfTheEndExplosion;
    public static boolean heroKingAuraEnabled;
    public static boolean heroBlockRestoration;
    public static boolean heroCleanItems;
    public static boolean cleaveSkillEnabled;
    public static boolean destructionGodTerrainDamageEnabled;
    public static String destructionGodTerrainDamageMode;
    public static boolean destructionGodBreakContainers;
    public static boolean destructionGodArenaRestore;
    public static int destructionGodMaxBrokenBlocksPerTick;
    public static boolean destructionGodFinalPhaseWorldCollapse;

    public static boolean soulBoundPactEnabled;
    public static boolean abyssalGazeEnabled;
    public static boolean transcendencePermitEnabled;

    public static boolean aiVisionEnabled;
    public static int aiVisionInterval;
    // 【新增】更新检查器静态变量
    public static boolean enableUpdateChecker;
    // 【新增】语言风格静态变量
    public static String aiLanguageStyle;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        poemOfTheEndExplosion = POEM_OF_THE_END_EXPLOSION.get();
        heroKingAuraEnabled = HERO_KING_AURA_ENABLED.get();
        heroBlockRestoration = HERO_BLOCK_RESTORATION.get();
        heroCleanItems = HERO_CLEAN_ITEMS.get();
        cleaveSkillEnabled = CLEAVE_SKILL_ENABLED.get();
        destructionGodTerrainDamageEnabled = DESTRUCTION_GOD_TERRAIN_DAMAGE_ENABLED.get();
        destructionGodTerrainDamageMode = DESTRUCTION_GOD_TERRAIN_DAMAGE_MODE.get();
        destructionGodBreakContainers = DESTRUCTION_GOD_BREAK_CONTAINERS.get();
        destructionGodArenaRestore = DESTRUCTION_GOD_ARENA_RESTORE.get();
        destructionGodMaxBrokenBlocksPerTick = DESTRUCTION_GOD_MAX_BROKEN_BLOCKS_PER_TICK.get();
        destructionGodFinalPhaseWorldCollapse = DESTRUCTION_GOD_FINAL_PHASE_WORLD_COLLAPSE.get();

        soulBoundPactEnabled = SOUL_BOUND_PACT_ENABLED.get();
        abyssalGazeEnabled = ABYSSAL_GAZE_ENABLED.get();
        transcendencePermitEnabled = TRANSCENDENCE_PERMIT_ENABLED.get();

        aiVisionEnabled = AI_VISION_ENABLED.get();
        aiVisionInterval = AI_VISION_INTERVAL.get();
// 【新增】更新检查器赋值
        enableUpdateChecker = ENABLE_UPDATE_CHECKER.get();
        // 【新增】语言风格赋值
        aiLanguageStyle = AI_LANGUAGE_STYLE.get();

        LOGGER.info("Herobrine Companion Config Loaded: Explosion={}, Aura={}, BlockRestoration={}, CleanItems={}, CleaveSkill={}, DGTerrainEnabled={}, DGTerrainMode={}, DGBreakContainers={}, DGArenaRestore={}, DGMaxBreakPerTick={}, DGWorldCollapse={}, Pact={}, Gaze={}, Permit={}, AIVision={}, AIInterval={}, AIStyle={}",
                poemOfTheEndExplosion, heroKingAuraEnabled, heroBlockRestoration, heroCleanItems, cleaveSkillEnabled,
                destructionGodTerrainDamageEnabled, destructionGodTerrainDamageMode, destructionGodBreakContainers,
                destructionGodArenaRestore, destructionGodMaxBrokenBlocksPerTick, destructionGodFinalPhaseWorldCollapse,
                soulBoundPactEnabled, abyssalGazeEnabled, transcendencePermitEnabled, aiVisionEnabled, aiVisionInterval, aiLanguageStyle);
    }
}