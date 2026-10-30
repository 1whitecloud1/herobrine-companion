package com.whitecloud233.herobrine_companion.util;

import net.minecraft.util.Mth;

/** Cubic easing used by the existing poses; Mth.smoothstep is a different, quintic curve. */
public final class Ease {
    private Ease() { }

    public static float smoothStep(float value) { return value * value * (3.0F - 2.0F * value); }

    public static float window(float value, float start, float end) {
        if (end <= start) return value >= end ? 1.0F : 0.0F;
        return smoothStep(Mth.clamp((value - start) / (end - start), 0.0F, 1.0F));
    }
}
