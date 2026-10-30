package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.Connection;
import net.minecraftforge.network.NetworkDirection;

/** Optional acknowledgement channel; the published main-channel packet IDs stay stable. */
public final class CompanionGuideChannel extends OptionalChannel {
    private static final CompanionGuideChannel INSTANCE = new CompanionGuideChannel();

    private CompanionGuideChannel() {
        super("companion_guide", "1");
    }

    public static void register() {
        INSTANCE.registerMessage(0, CompanionGuideOpenedPacket.class, CompanionGuideOpenedPacket::encode,
                CompanionGuideOpenedPacket::new, CompanionGuideOpenedPacket::handle, NetworkDirection.PLAY_TO_SERVER);
    }

    public static void notifyScreenOpened(Connection connection, int heroId) {
        if (INSTANCE.remoteSupports(connection)) {
            INSTANCE.sendToServer(new CompanionGuideOpenedPacket(heroId));
        }
    }
}
