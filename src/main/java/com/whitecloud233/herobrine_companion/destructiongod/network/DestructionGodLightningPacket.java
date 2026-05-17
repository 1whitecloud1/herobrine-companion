package com.whitecloud233.herobrine_companion.destructiongod.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DestructionGodLightningPacket(Vec3 startPos, Vec3 endPos, float width) implements CustomPacketPayload {

    public static final Type<DestructionGodLightningPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "destruction_god_lightning"));

    public static final StreamCodec<FriendlyByteBuf, DestructionGodLightningPacket> STREAM_CODEC = StreamCodec.ofMember(
            DestructionGodLightningPacket::encode,
            DestructionGodLightningPacket::decode
    );

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(startPos.x);
        buf.writeDouble(startPos.y);
        buf.writeDouble(startPos.z);
        buf.writeDouble(endPos.x);
        buf.writeDouble(endPos.y);
        buf.writeDouble(endPos.z);
        buf.writeFloat(width);
    }

    private static DestructionGodLightningPacket decode(FriendlyByteBuf buf) {
        return new DestructionGodLightningPacket(
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> DestructionGodClientPacketHandler.handleLightning(this));
    }
}