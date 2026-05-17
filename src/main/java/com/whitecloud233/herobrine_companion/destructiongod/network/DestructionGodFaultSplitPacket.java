package com.whitecloud233.herobrine_companion.destructiongod.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DestructionGodFaultSplitPacket(Vec3 origin, Vec3 direction, float length, int terrainHalfWidth, int splitDistance, int chargeTicks, int lifetime) implements CustomPacketPayload {

    public static final Type<DestructionGodFaultSplitPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "destruction_god_fault_split"));

    public static final StreamCodec<FriendlyByteBuf, DestructionGodFaultSplitPacket> STREAM_CODEC = StreamCodec.ofMember(
            DestructionGodFaultSplitPacket::encode,
            DestructionGodFaultSplitPacket::decode
    );

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(origin.x);
        buf.writeDouble(origin.y);
        buf.writeDouble(origin.z);
        buf.writeDouble(direction.x);
        buf.writeDouble(direction.y);
        buf.writeDouble(direction.z);
        buf.writeFloat(length);
        buf.writeVarInt(terrainHalfWidth);
        buf.writeVarInt(splitDistance);
        buf.writeVarInt(chargeTicks);
        buf.writeVarInt(lifetime);
    }

    private static DestructionGodFaultSplitPacket decode(FriendlyByteBuf buf) {
        return new DestructionGodFaultSplitPacket(
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> DestructionGodClientPacketHandler.handleFaultSplit(this));
    }
}