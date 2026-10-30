package com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.whitecloud233.modid.herobrine_companion.util.DebugLog;

/** Shared diagnostics for cooking state changes. */
final class KitchenDiagnostics {
    private KitchenDiagnostics() {
    }

    /** 厨具卡死类问题的排查开关：默认 DEBUG 级，只在 debug.log 中出现，不污染 latest.log。 */
    static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("HeroKaleidoscopeCook");

    private static String lastTickSignature = "";

    private static long lastTickLogMillis = 0L;

    // 厨具卡死排查：INFO 级、按状态串去重，只在状态变化时输出一行，定位「卡住不干活」。
    private static final org.slf4j.Logger COOK_DIAG =
            org.slf4j.LoggerFactory.getLogger("HeroCookDiag");

    private static final java.util.Map<String, String> COOK_DIAG_STATE =
            new java.util.concurrent.ConcurrentHashMap<>();

    static void cookDiag(String key, String state) {
        if (state.equals(COOK_DIAG_STATE.put(key, state))) {
            return;
        }
        DebugLog.info(COOK_DIAG, "[CookDiag] {} :: {}", key, state);
    }

    static void tick(BlockPos pos, BlockEntity blockEntity) {
        String signature = pos.toShortString() + "|" + (blockEntity == null ? "null" : blockEntity.getClass().getSimpleName());
        long nowMillis = System.currentTimeMillis();
        if (!signature.equals(lastTickSignature) || nowMillis - lastTickLogMillis > 5000L) {
            lastTickSignature = signature;
            lastTickLogMillis = nowMillis;
            DebugLog.info(LOGGER,
                    "[CookDiag] kaleidoscope.tick :: {}", signature);
        }
    }
}
