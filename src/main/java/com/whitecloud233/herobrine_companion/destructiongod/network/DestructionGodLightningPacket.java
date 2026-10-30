package com.whitecloud233.herobrine_companion.destructiongod.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.destructiongod.client.network.DestructionGodClientPacketHandler;
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
        buf.writeVec3(startPos);
        buf.writeVec3(endPos);
        buf.writeFloat(width);
    }

    private static DestructionGodLightningPacket decode(FriendlyByteBuf buf) {
        return new DestructionGodLightningPacket(
                buf.readVec3(),
                buf.readVec3(),
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
