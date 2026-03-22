package com.whitecloud233.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext; // 别忘了导入这个

public record PaleLightningPacket(double x, double y, double z, float width) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PaleLightningPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "pale_lightning"));

    public static final StreamCodec<FriendlyByteBuf, PaleLightningPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, PaleLightningPacket::x,
            ByteBufCodecs.DOUBLE, PaleLightningPacket::y,
            ByteBufCodecs.DOUBLE, PaleLightningPacket::z,
            ByteBufCodecs.FLOAT, PaleLightningPacket::width,
            PaleLightningPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // [新增] 添加 handle 方法，将调用隔离在方法体内
    public void handle(IPayloadContext context) {
        ClientPacketHandler.handlePaleLightning(this, context);
    }
}