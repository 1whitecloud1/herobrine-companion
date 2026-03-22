package com.whitecloud233.herobrine_companion.network;

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
        this.startPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.endPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.startPos.x);
        buf.writeDouble(this.startPos.y);
        buf.writeDouble(this.startPos.z);
        buf.writeDouble(this.endPos.x);
        buf.writeDouble(this.endPos.y);
        buf.writeDouble(this.endPos.z);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 1.21.1 标准的包处理方法
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 安全投递给纯客户端处理器，防止物理服务端崩溃
            ClientPacketHandler.handlePaleLightningArc(this);
        });
    }
}