package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncHeroVisitPacket(boolean visited) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncHeroVisitPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "sync_hero_visit"));

    public static final StreamCodec<ByteBuf, SyncHeroVisitPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        SyncHeroVisitPacket::visited,
        SyncHeroVisitPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncHeroVisitPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Client-side handling
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.getPersistentData().putBoolean("HasVisitedHeroDimension", payload.visited);
            }
        });
    }
}
