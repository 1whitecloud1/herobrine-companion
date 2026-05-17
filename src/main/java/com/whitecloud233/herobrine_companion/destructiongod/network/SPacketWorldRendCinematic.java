package com.whitecloud233.herobrine_companion.destructiongod.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.destructiongod.client.cinematic.ClientSpatialRendHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SPacketWorldRendCinematic(double x, double y, double z) implements CustomPacketPayload {

    public static final Type<SPacketWorldRendCinematic> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "world_rend_cinematic"));

    public static final StreamCodec<FriendlyByteBuf, SPacketWorldRendCinematic> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, SPacketWorldRendCinematic::x,
            ByteBufCodecs.DOUBLE, SPacketWorldRendCinematic::y,
            ByteBufCodecs.DOUBLE, SPacketWorldRendCinematic::z,
            SPacketWorldRendCinematic::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientSpatialRendHandler.startWorldRendCinematic(this.x(), this.y(), this.z()));
    }
}
