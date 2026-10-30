package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.SyncPeacefulPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Server-owned protection state, including its client mirror. */
public final class PeacefulProtection {
    public static final String TAG = "herobrine_companion_peaceful";

    private PeacefulProtection() {
    }

    public static boolean isEnabled(Player player) {
        return player != null && player.getTags().contains(TAG);
    }

    public static void setEnabled(ServerPlayer player, boolean enabled) {
        if (enabled) {
            player.addTag(TAG);
        } else {
            player.removeTag(TAG);
        }
        syncToPlayer(player);
    }

    public static void syncToPlayer(ServerPlayer player) {
        PacketHandler.sendToPlayer(new SyncPeacefulPacket(isEnabled(player)), player);
    }
}
