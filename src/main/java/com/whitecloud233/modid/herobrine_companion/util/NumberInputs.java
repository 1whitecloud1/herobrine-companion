package com.whitecloud233.modid.herobrine_companion.util;

import net.minecraft.util.Mth;

/** 配置数值清洗；默认值规则不是普通 clamp 的一部分。 */
public final class NumberInputs {
    private NumberInputs() { }

    public static double finite(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Mth.clamp(value, min, max) : fallback;
    }

    public static int positive(int value, int min, int max, int fallback) {
        return value <= 0 ? fallback : Mth.clamp(value, min, max);
    }
}
