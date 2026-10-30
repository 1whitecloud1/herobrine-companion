package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;

public final class HeroContentChannel extends OptionalChannel {
    private static final HeroContentChannel INSTANCE = new HeroContentChannel();
    private HeroContentChannel() { super("content_snapshot", "1"); }
    public static void register() {
        INSTANCE.registerMessage(0, HeroContentPacket.class, HeroContentPacket::encode, HeroContentPacket::new,
                HeroContentPacket::handle, NetworkDirection.PLAY_TO_CLIENT);
    }
    public static void send(ServerPlayer player, byte[] data) {
        if (INSTANCE.remoteSupports(player.connection.connection)) INSTANCE.sendToPlayer(player, new HeroContentPacket(data));
    }
}
