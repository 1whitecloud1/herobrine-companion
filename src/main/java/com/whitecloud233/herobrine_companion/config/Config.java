package com.whitecloud233.herobrine_companion.config;

import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

@EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ... [中间的 1~8 武器和技能的配置保持你的原样不变] ...
    public static final ModConfigSpec.BooleanValue POEM_OF_THE_END_EXPLOSION = BUILDER.comment("终末之诗在破境状态下是否启用爆炸").define("poemOfTheEndExplosion_v2", false);
    public static final ModConfigSpec.BooleanValue HERO_KING_AURA_ENABLED = BUILDER.comment("是否启用 Herobrine 的怪物臣服AI").define("heroKingAuraEnabled_v2", true);
    public static final ModConfigSpec.BooleanValue HERO_BLOCK_RESTORATION = BUILDER.comment("Herobrine是否自动修复被破坏的方块").define("heroBlockRestoration_v2", false);
    public static final ModConfigSpec.BooleanValue HERO_CLEAN_ITEMS = BUILDER.comment("Herobrine是否自动清理地上的掉落物").define("heroCleanItems_v2", false);
    public static final ModConfigSpec.BooleanValue CLEAVE_SKILL_ENABLED = BUILDER.comment("是否启用镰刀的 5 键存档毁灭术").define("cleaveSkillEnabled", false);
    public static final ModConfigSpec.BooleanValue SOUL_BOUND_PACT_ENABLED = BUILDER.comment("是否启用魂缚之契物品").define("soulBoundPactEnabled", true);
    public static final ModConfigSpec.BooleanValue ABYSSAL_GAZE_ENABLED = BUILDER.comment("是否启用幽邃之视物品").define("abyssalGazeEnabled", true);
    public static final ModConfigSpec.BooleanValue TRANSCENDENCE_PERMIT_ENABLED = BUILDER.comment("是否启用凌越之允物品").define("transcendencePermitEnabled", true);

    // ==========================================
    // 全知视觉 (AI环境感知) 的开关与间隔
    // ==========================================
    public static final ModConfigSpec.BooleanValue AI_VISION_ENABLED = BUILDER
            .comment("是否启用全知视觉（AI感知）")
            .define("aiVisionEnabled", true);

    public static final ModConfigSpec.IntValue AI_VISION_INTERVAL = BUILDER
            .comment("全知视觉的每次主动发话最小间隔（秒），建议在 10 ~ 300 之间")
            .defineInRange("aiVisionInterval", 30, 5, 600);

    public static final ModConfigSpec.ConfigValue<String> AI_LANGUAGE_STYLE = BUILDER
            .comment("Herobrine的AI语言风格与语气")
            .define("aiLanguageStyle_v2", "理性且宽容");

    // 【新增】恢复更新检查器开关
    public static final ModConfigSpec.BooleanValue ENABLE_UPDATE_CHECKER = BUILDER
            .comment("加入世界时是否在 Modrinth 检查模组更新")
            .define("enableUpdateChecker", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean poemOfTheEndExplosion;
    public static boolean heroKingAuraEnabled;
    public static boolean heroBlockRestoration;
    public static boolean heroCleanItems;
    public static boolean cleaveSkillEnabled;
    public static boolean soulBoundPactEnabled;
    public static boolean abyssalGazeEnabled;
    public static boolean transcendencePermitEnabled;

    public static boolean aiVisionEnabled;
    public static int aiVisionInterval;
    public static String aiLanguageStyle;

    // 【新增】更新检查器变量
    public static boolean enableUpdateChecker;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        poemOfTheEndExplosion = POEM_OF_THE_END_EXPLOSION.get();
        heroKingAuraEnabled = HERO_KING_AURA_ENABLED.get();
        heroBlockRestoration = HERO_BLOCK_RESTORATION.get();
        heroCleanItems = HERO_CLEAN_ITEMS.get();
        cleaveSkillEnabled = CLEAVE_SKILL_ENABLED.get();
        soulBoundPactEnabled = SOUL_BOUND_PACT_ENABLED.get();
        abyssalGazeEnabled = ABYSSAL_GAZE_ENABLED.get();
        transcendencePermitEnabled = TRANSCENDENCE_PERMIT_ENABLED.get();

        aiVisionEnabled = AI_VISION_ENABLED.get();
        aiVisionInterval = AI_VISION_INTERVAL.get();
        aiLanguageStyle = AI_LANGUAGE_STYLE.get();

        // 【新增】获取更新检查器配置
        enableUpdateChecker = ENABLE_UPDATE_CHECKER.get();

        LOGGER.info("Herobrine Companion Config Loaded: Explosion={}, Aura={}, BlockRestoration={}, CleanItems={}, CleaveSkill={}, Pact={}, Gaze={}, Permit={}, AIVision={}, AIInterval={}, aiLanguageStyle={}, UpdateChecker={}",
                poemOfTheEndExplosion, heroKingAuraEnabled, heroBlockRestoration, heroCleanItems, cleaveSkillEnabled, soulBoundPactEnabled, abyssalGazeEnabled, transcendencePermitEnabled, aiVisionEnabled, aiVisionInterval, aiLanguageStyle, enableUpdateChecker);
    }
}