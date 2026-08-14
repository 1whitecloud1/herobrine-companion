package com.whitecloud233.herobrine_companion.network;

import io.netty.buffer.ByteBuf;
import com.whitecloud233.herobrine_companion.client.network.ClientStateSync;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SyncHeroVisitPacket implements CustomPacketPayload {
    public static final Type<SyncHeroVisitPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "sync_hero_visit"));

    public static final StreamCodec<ByteBuf, SyncHeroVisitPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            SyncHeroVisitPacket::hasVisited,
            SyncHeroVisitPacket::new
    );

    private final boolean visited;

    public SyncHeroVisitPacket(boolean visited) {
        this.visited = visited;
    }

    public boolean hasVisited() {
        return visited;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncHeroVisitPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStateSync.setVisitedHeroDimension(packet.visited));
    }
}
