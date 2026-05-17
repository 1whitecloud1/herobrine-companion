package com.whitecloud233.herobrine_companion.destructiongod.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DestructionGodLightningArcPacket(Vec3 startPos, Vec3 endPos, float width, int lifetimeTicks) implements CustomPacketPayload {

    public static final Type<DestructionGodLightningArcPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "destruction_god_lightning_arc"));

    public static final StreamCodec<FriendlyByteBuf, DestructionGodLightningArcPacket> STREAM_CODEC = StreamCodec.ofMember(
            DestructionGodLightningArcPacket::encode,
            DestructionGodLightningArcPacket::decode
    );

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(startPos.x);
        buf.writeDouble(startPos.y);
        buf.writeDouble(startPos.z);
        buf.writeDouble(endPos.x);
        buf.writeDouble(endPos.y);
        buf.writeDouble(endPos.z);
        buf.writeFloat(width);
        buf.writeVarInt(lifetimeTicks);
    }

    private static DestructionGodLightningArcPacket decode(FriendlyByteBuf buf) {
        return new DestructionGodLightningArcPacket(
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(),
                buf.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> DestructionGodClientPacketHandler.handleLightningArc(this));
    }
}