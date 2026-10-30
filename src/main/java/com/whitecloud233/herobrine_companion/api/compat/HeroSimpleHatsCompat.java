package com.whitecloud233.herobrine_companion.api.compat;

import com.whitecloud233.herobrine_companion.client.render.HeroRenderer;

/** Bundled optional integration API. Callers never need third-party types. */
public final class HeroSimpleHatsCompat {
    private static final String TARGET = "simplehats.HeroSimpleHatsCompat";

    private HeroSimpleHatsCompat() { }

    public static boolean isLoaded() {
        Object result = OptionalCompat.call(TARGET, "isLoaded", new Class<?>[]{});
        return Boolean.TRUE.equals(result);
    }

    public static void attachRenderLayer(HeroRenderer renderer) {
        OptionalCompat.call(TARGET, "attachRenderLayer", new Class<?>[]{HeroRenderer.class}, renderer);
    }

}
