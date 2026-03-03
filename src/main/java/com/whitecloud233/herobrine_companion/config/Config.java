package com.whitecloud233.herobrine_companion.config;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

@EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Renamed keys to "_v2" to force config refresh (ignoring old config file values)
    // 更改了键名以强制刷新配置（忽略旧配置文件的值）

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

    // 【新增】掉落物清理配置项
    public static final ModConfigSpec.BooleanValue HERO_CLEAN_ITEMS = BUILDER
            .comment("Whether Herobrine automatically cleans up dropped items")
            .comment("Herobrine是否自动清理地上的掉落物")
            .define("heroCleanItems_v2", false); // 默认开启
    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean poemOfTheEndExplosion;
    public static boolean heroKingAuraEnabled;
    public static boolean heroBlockRestoration;
    public static boolean heroCleanItems;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        poemOfTheEndExplosion = POEM_OF_THE_END_EXPLOSION.get();
        heroKingAuraEnabled = HERO_KING_AURA_ENABLED.get();
        heroBlockRestoration = HERO_BLOCK_RESTORATION.get();
        heroCleanItems = HERO_CLEAN_ITEMS.get(); // 【新增】
        LOGGER.info("Herobrine Companion Config Loaded: Explosion={}, Aura={}, BlockRestoration={}, CleanItems={}",
                poemOfTheEndExplosion, heroKingAuraEnabled, heroBlockRestoration, heroCleanItems);
    }
}




