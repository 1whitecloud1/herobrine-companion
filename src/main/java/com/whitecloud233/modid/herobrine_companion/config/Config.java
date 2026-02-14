package com.whitecloud233.modid.herobrine_companion.config;

import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // Renamed keys to "_v2" to force config refresh (ignoring old config file values)
    // 更改了键名以强制刷新配置（忽略旧配置文件的值）

    public static final ForgeConfigSpec.BooleanValue POEM_OF_THE_END_EXPLOSION = BUILDER
            .comment("Whether the Poem of the End weapon causes explosions in its broken state")
            .comment("终末之诗在破境状态下是否启用爆炸")
            .define("poemOfTheEndExplosion_v2", false);

    public static final ForgeConfigSpec.BooleanValue HERO_KING_AURA_ENABLED = BUILDER
            .comment("Whether the Hero King Aura goal is enabled for Herobrine")
            .comment("是否启用 Herobrine 的怪物臣服AI")
            .define("heroKingAuraEnabled_v2", true);

    // 在 Config.java 中
    public static final ForgeConfigSpec SPEC = BUILDER.build(); // 添加 public
    public static boolean poemOfTheEndExplosion;
    public static boolean heroKingAuraEnabled;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        poemOfTheEndExplosion = POEM_OF_THE_END_EXPLOSION.get();
        heroKingAuraEnabled = HERO_KING_AURA_ENABLED.get();
        LOGGER.info("Herobrine Companion Config Loaded: Explosion={}, Aura={}", poemOfTheEndExplosion, heroKingAuraEnabled);
    }
}


