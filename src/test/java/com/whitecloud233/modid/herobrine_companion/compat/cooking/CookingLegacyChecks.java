package com.whitecloud233.modid.herobrine_companion.compat.cooking;

import net.minecraft.nbt.CompoundTag;

public final class CookingLegacyChecks {
    public static void main(String[] args) {
        KaleidoscopeCookingBackend backend = new KaleidoscopeCookingBackend();
        CompoundTag data = new CompoundTag();
        data.putString("Unrelated", "keep");
        check(!backend.restoreLegacyDisplay(null, null, data), "Missing legacy snapshot reported as restored");
        data.put("HeroAutoCookKaleidoscopeSnapshot", new CompoundTag());
        check(backend.restoreLegacyDisplay(null, null, data), "Legacy key must be recognized");
        check(!data.contains("HeroAutoCookKaleidoscopeSnapshot"), "Legacy key must be consumed after cleanup");
        check(data.getString("Unrelated").equals("keep"), "Legacy cleanup must retain unrelated state");
        System.out.println("CookingLegacyChecks: 4 checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
