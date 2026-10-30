package com.whitecloud233.herobrine_companion.compat.kaleidoscope;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;

/** Measures elapsed game ticks even when the AI skips the exact cooldown deadline. */
final class KitchenActionCooldown {
    private static final String DATA_KEY = "HeroKaleidoscopeActionCooldown";

    private KitchenActionCooldown() {
    }

    static boolean tryAcquire(CompoundTag entityData, String dimension, long stationPos,
                              long gameTime, int interval) {
        if (interval <= 0) {
            throw new IllegalArgumentException("Action interval must be positive");
        }

        CompoundTag previous = entityData.getCompound(DATA_KEY);
        if (previous.contains("LastTick") && dimension.equals(previous.getString("Dimension"))
                && stationPos == previous.getLong("StationPos")) {
            long lastTick = previous.getLong("LastTick");
            if (gameTime >= lastTick && gameTime - lastTick < interval) {
                return false;
            }
        }

        CompoundTag current = new CompoundTag();
        current.putString("Dimension", dimension);
        current.putLong("StationPos", stationPos);
        current.putLong("LastTick", gameTime);
        entityData.put(DATA_KEY, current);
        return true;
    }

    static void clear(CompoundTag entityData) {
        entityData.remove(DATA_KEY);
    }

    static boolean tryCookAction(HeroEntity hero, ServerLevel level, BlockPos pos, int interval) {
        // GoalSelector normally updates this goal every two ticks. A world-time modulo
        // can miss every deadline on odd ticks; elapsed time also tolerates skipped ticks.
        return KitchenActionCooldown.tryAcquire(hero.getPersistentData(),
                level.dimension().location().toString(), pos.asLong(), level.getGameTime(), interval);
    }
}
