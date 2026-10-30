package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.client.network.ClientStateSync;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Authoritative protection state sent only to the affected player. */
public record SyncPeacefulPacket(boolean peaceful) implements CustomPacketPayload {
    public static final Type<SyncPeacefulPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "sync_peaceful"));
    public static final StreamCodec<ByteBuf, SyncPeacefulPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncPeacefulPacket::peaceful, SyncPeacefulPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncPeacefulPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStateSync.applyPeacefulProtection(packet.peaceful()));
    }
}
