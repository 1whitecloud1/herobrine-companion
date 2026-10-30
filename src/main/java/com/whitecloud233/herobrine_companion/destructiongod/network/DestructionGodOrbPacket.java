package com.whitecloud233.herobrine_companion.destructiongod.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.destructiongod.client.network.DestructionGodClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DestructionGodOrbPacket(Vec3 startPos, Vec3 impactPos, int fallTicks, float startRadius, float maxRadius, float apexHeight) implements CustomPacketPayload {

    public static final Type<DestructionGodOrbPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "destruction_god_orb"));

    public static final StreamCodec<FriendlyByteBuf, DestructionGodOrbPacket> STREAM_CODEC = StreamCodec.ofMember(
            DestructionGodOrbPacket::encode,
            DestructionGodOrbPacket::decode
    );

    public void encode(FriendlyByteBuf buf) {
        buf.writeVec3(startPos);
        buf.writeVec3(impactPos);
        buf.writeInt(fallTicks);
        buf.writeFloat(startRadius);
        buf.writeFloat(maxRadius);
        buf.writeFloat(apexHeight);
    }

    private static DestructionGodOrbPacket decode(FriendlyByteBuf buf) {
        return new DestructionGodOrbPacket(
                buf.readVec3(),
                buf.readVec3(),
                buf.readInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> DestructionGodClientPacketHandler.handleOrb(this));
    }
}
