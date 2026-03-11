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
            .define("heroKingAuraEnabled_v2", true);

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

    // ==========================================
    // 【新增】三个物品的开关配置
    // ==========================================
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

    public static final ForgeConfigSpec SPEC = BUILDER.build();
    public static boolean poemOfTheEndExplosion;
    public static boolean heroKingAuraEnabled;
    public static boolean heroBlockRestoration;
    public static boolean heroCleanItems;
    public static boolean cleaveSkillEnabled;

    // 【新增】静态变量
    public static boolean soulBoundPactEnabled;
    public static boolean abyssalGazeEnabled;
    public static boolean transcendencePermitEnabled;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        poemOfTheEndExplosion = POEM_OF_THE_END_EXPLOSION.get();
        heroKingAuraEnabled = HERO_KING_AURA_ENABLED.get();
        heroBlockRestoration = HERO_BLOCK_RESTORATION.get();
        heroCleanItems = HERO_CLEAN_ITEMS.get();
        cleaveSkillEnabled = CLEAVE_SKILL_ENABLED.get();

        // 【新增】赋值
        soulBoundPactEnabled = SOUL_BOUND_PACT_ENABLED.get();
        abyssalGazeEnabled = ABYSSAL_GAZE_ENABLED.get();
        transcendencePermitEnabled = TRANSCENDENCE_PERMIT_ENABLED.get();

        // 更新日志打印内容
        LOGGER.info("Herobrine Companion Config Loaded: Explosion={}, Aura={}, BlockRestoration={}, CleanItems={}, CleaveSkill={}, Pact={}, Gaze={}, Permit={}",
                poemOfTheEndExplosion, heroKingAuraEnabled, heroBlockRestoration, heroCleanItems, cleaveSkillEnabled, soulBoundPactEnabled, abyssalGazeEnabled, transcendencePermitEnabled);
    }
}
