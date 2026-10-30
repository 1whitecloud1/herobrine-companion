package com.whitecloud233.herobrine_companion.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class HeroContentChannel {
    private HeroContentChannel() { }
    public static void register(PayloadRegistrar registrar) {
        registrar.optional().playToClient(HeroContentPacket.TYPE, HeroContentPacket.STREAM_CODEC, HeroContentPacket::handle);
    }
    public static void send(ServerPlayer player, byte[] data) {
        if (NetworkRegistry.hasChannel(player.connection.getConnection(), null, HeroContentPacket.TYPE.id())) {
            PacketHandler.sendToPlayer(new HeroContentPacket(data), player);
        }
    }
}
