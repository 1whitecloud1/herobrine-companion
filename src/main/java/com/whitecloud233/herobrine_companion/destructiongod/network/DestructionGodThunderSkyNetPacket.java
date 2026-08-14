package com.whitecloud233.herobrine_companion.destructiongod.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.destructiongod.client.network.DestructionGodClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DestructionGodThunderSkyNetPacket(Vec3 center, double cloudY, float radius, int lifetime, int seed) implements CustomPacketPayload {

    public static final Type<DestructionGodThunderSkyNetPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "destruction_god_thunder_sky_net"));

    public static final StreamCodec<FriendlyByteBuf, DestructionGodThunderSkyNetPacket> STREAM_CODEC = StreamCodec.ofMember(
            DestructionGodThunderSkyNetPacket::encode,
            DestructionGodThunderSkyNetPacket::decode
    );

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(center.x);
        buf.writeDouble(center.y);
        buf.writeDouble(center.z);
        buf.writeDouble(cloudY);
        buf.writeFloat(radius);
        buf.writeVarInt(lifetime);
        buf.writeInt(seed);
    }

    private static DestructionGodThunderSkyNetPacket decode(FriendlyByteBuf buf) {
        return new DestructionGodThunderSkyNetPacket(
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readDouble(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> DestructionGodClientPacketHandler.handleThunderSkyNet(this));
    }
}
