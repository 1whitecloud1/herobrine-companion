package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.client.network.ClientFxHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PaleLightningArcPacket implements CustomPacketPayload {
    // 1.21.1 标准的包类型定义
    public static final Type<PaleLightningArcPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "pale_lightning_arc"));

    // 1.21.1 标准的流编解码器 (StreamCodec)
    public static final StreamCodec<FriendlyByteBuf, PaleLightningArcPacket> STREAM_CODEC = StreamCodec.ofMember(PaleLightningArcPacket::encode, PaleLightningArcPacket::new);

    public final Vec3 startPos;
    public final Vec3 endPos;

    public PaleLightningArcPacket(Vec3 startPos, Vec3 endPos) {
        this.startPos = startPos;
        this.endPos = endPos;
    }

    public PaleLightningArcPacket(FriendlyByteBuf buf) {
        this.startPos = buf.readVec3();
        this.endPos = buf.readVec3();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVec3(this.startPos);
        buf.writeVec3(this.endPos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 1.21.1 标准的包处理方法
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientFxHandler.handlePaleLightningArc(this));
    }
}