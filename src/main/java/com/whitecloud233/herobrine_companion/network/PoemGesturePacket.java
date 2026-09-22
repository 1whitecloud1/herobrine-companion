package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Intent only: the server selects the clip and owns its blade hit phases. */
public record PoemGesturePacket(int gesture, int slot, int mode) implements CustomPacketPayload {
    public static final Type<PoemGesturePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("herobrine_companion", "poem_gesture"));
    public static final StreamCodec<ByteBuf, PoemGesturePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public PoemGesturePacket decode(ByteBuf buffer) {
            return new PoemGesturePacket(buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readUnsignedByte());
        }
        @Override public void encode(ByteBuf buffer, PoemGesturePacket packet) {
            buffer.writeByte(packet.gesture()); buffer.writeByte(packet.slot()); buffer.writeByte(packet.mode());
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PoemGesturePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                HeroEpicFightCompat.queuePoemGesture(player, packet.gesture(), packet.slot(), packet.mode());
            }
        });
    }
}
