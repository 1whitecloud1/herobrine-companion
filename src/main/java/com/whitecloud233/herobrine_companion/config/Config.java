package com.whitecloud233.herobrine_companion.config;

import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

// 1.21.1 更新：使用 NeoForge 的 EventBusSubscriber
@EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 1.21.1 更新：ForgeConfigSpec 被彻底重命名为 ModConfigSpec
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue POEM_OF_THE_END_EXPLOSION = BUILDER
            .comment("Whether the Poem of the End weapon causes explosions in its broken state")
            .comment("终末之诗在破境状态下是否启用爆炸")
            .define("poemOfTheEndExplosion_v2", false);

    public static final ModConfigSpec.BooleanValue HERO_KING_AURA_ENABLED = BUILDER
            .comment("Whether the Hero King Aura goal is enabled for Herobrine")
            .comment("是否启用 Herobrine 的怪物臣服AI")
            .define("heroKingAuraEnabled_v2", true);

    public static final ModConfigSpec.BooleanValue HERO_BLOCK_RESTORATION = BUILDER
            .comment("Whether Herobrine automatically restores broken blocks")
            .comment("Herobrine是否自动修复被破坏的方块")
            .define("heroBlockRestoration_v2", false);

    public static final ModConfigSpec.BooleanValue HERO_CLEAN_ITEMS = BUILDER
            .comment("Whether Herobrine automatically cleans up dropped items")
            .comment("Herobrine是否自动清理地上的掉落物")
            .define("heroCleanItems_v2", false);

    public static final ModConfigSpec.BooleanValue CLEAVE_SKILL_ENABLED = BUILDER
            .comment("Whether the World Cleave skill (5 key) is enabled")
            .comment("是否启用镰刀的 5 键存档毁灭术")
            .define("cleaveSkillEnabled", false);

    // ==========================================
    // 三个物品的开关配置
    // ==========================================
    public static final ModConfigSpec.BooleanValue SOUL_BOUND_PACT_ENABLED = BUILDER
            .comment("Whether the Soul Bound Pact item is enabled")
            .comment("是否启用魂缚之契物品")
            .define("soulBoundPactEnabled", true);

    public static final ModConfigSpec.BooleanValue ABYSSAL_GAZE_ENABLED = BUILDER
            .comment("Whether the Abyssal Gaze item is enabled")
            .comment("是否启用幽邃之视物品")
            .define("abyssalGazeEnabled", true);

    public static final ModConfigSpec.BooleanValue TRANSCENDENCE_PERMIT_ENABLED = BUILDER
            .comment("Whether the Transcendence Permit item is enabled")
            .comment("是否启用凌越之允物品")
            .define("transcendencePermitEnabled", true);

    // 1.21.1 更新：对应修改为 ModConfigSpec
    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean poemOfTheEndExplosion;
    public static boolean heroKingAuraEnabled;
    public static boolean heroBlockRestoration;
    public static boolean heroCleanItems;
    public static boolean cleaveSkillEnabled;

    public static boolean soulBoundPactEnabled;
    public static boolean abyssalGazeEnabled;
    public static boolean transcendencePermitEnabled;

    // 1.21.1 更新：监听配置加载和重载事件
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

        // 更新日志打印内容
        LOGGER.info("Herobrine Companion Config Loaded: Explosion={}, Aura={}, BlockRestoration={}, CleanItems={}, CleaveSkill={}, Pact={}, Gaze={}, Permit={}",
                poemOfTheEndExplosion, heroKingAuraEnabled, heroBlockRestoration, heroCleanItems, cleaveSkillEnabled, soulBoundPactEnabled, abyssalGazeEnabled, transcendencePermitEnabled);
    }
}



