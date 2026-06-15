package com.whitecloud233.herobrine_companion.entity.awakened.containment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class AwakenedVesselActionGuard {
    private static final int POST_CAPTURE_RELEASE_LOCK_TICKS = 4;
    private static final String LAST_ENTITY_INTERACTION_TICK_TAG = "HerobrineCompanionAwakenedVesselEntityInteractionTick";

    private AwakenedVesselActionGuard() {
    }

    public static void lockAfterCapture(ServerPlayer player, ItemStack vessel) {
        if (player == null || vessel.isEmpty()) {
            return;
        }

        player.getCooldowns().addCooldown(vessel.getItem(), POST_CAPTURE_RELEASE_LOCK_TICKS);
    }

    public static boolean blocksImmediateFollowUp(Player player, ItemStack vessel, Level level) {
        if (player != null && player.getCooldowns().isOnCooldown(vessel.getItem())) {
            return true;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        long now = serverLevel.getGameTime();
        return AwakenedVesselStorage.read(vessel)
                .map(captured -> captured.capturedGameTime() >= now)
                .orElse(false);
    }

    public static boolean blocksDuplicateEntityInteraction(Player player, Level level) {
        if (!(player instanceof ServerPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        long now = serverLevel.getGameTime();
        CompoundTag data = player.getPersistentData();
        if (data.contains(LAST_ENTITY_INTERACTION_TICK_TAG)
                && data.getLong(LAST_ENTITY_INTERACTION_TICK_TAG) >= now) {
            return true;
        }

        data.putLong(LAST_ENTITY_INTERACTION_TICK_TAG, now);
        return false;
    }
}
