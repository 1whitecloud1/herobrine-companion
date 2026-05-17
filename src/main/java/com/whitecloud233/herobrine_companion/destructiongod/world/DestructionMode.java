package com.whitecloud233.herobrine_companion.destructiongod.world;

import com.whitecloud233.herobrine_companion.config.Config;

import java.util.Locale;

enum DestructionMode {
    VISUAL,
    SAFE,
    DIVINE;

    static DestructionMode current() {
        String raw = Config.destructionGodTerrainDamageMode == null ? "safe" : Config.destructionGodTerrainDamageMode.trim().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "visual" -> VISUAL;
            case "divine" -> DIVINE;
            default -> SAFE;
        };
    }
}

