package com.whitecloud233.modid.herobrine_companion.client.render;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HeroClientSkinCache {
    private static final Map<UUID, byte[]> SYNCED_SKIN_BYTES = new HashMap<>();

    private HeroClientSkinCache() {}

    public static void put(UUID heroId, byte[] skinData) {
        if (heroId == null) {
            return;
        }
        SYNCED_SKIN_BYTES.put(heroId, skinData != null ? skinData : new byte[0]);
    }

    public static byte[] get(UUID heroId) {
        return heroId != null ? SYNCED_SKIN_BYTES.getOrDefault(heroId, new byte[0]) : new byte[0];
    }

    public static int getHash(UUID heroId) {
        return Arrays.hashCode(get(heroId));
    }

    public static void clear(UUID heroId) {
        if (heroId != null) {
            SYNCED_SKIN_BYTES.remove(heroId);
        }
    }
}


