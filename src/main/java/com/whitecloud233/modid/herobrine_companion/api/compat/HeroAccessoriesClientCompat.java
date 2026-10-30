package com.whitecloud233.modid.herobrine_companion.api.compat;

import com.whitecloud233.modid.herobrine_companion.client.render.HeroRenderer;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;

/** Bundled optional integration API. Callers never need third-party types. */
public final class HeroAccessoriesClientCompat {
    private static final String TARGET = "accessories.HeroAccessoriesClientCompat";

    private HeroAccessoriesClientCompat() { }

    public static void attachRenderLayer(HeroRenderer renderer) {
        OptionalCompat.call(TARGET, "attachRenderLayer", new Class<?>[]{HeroRenderer.class}, renderer);
    }

    public static boolean openAccessoriesScreen(HeroEntity target) {
        Object result = OptionalCompat.call(TARGET, "openAccessoriesScreen", new Class<?>[]{HeroEntity.class}, target);
        return Boolean.TRUE.equals(result);
    }

}
