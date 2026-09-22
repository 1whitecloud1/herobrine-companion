package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.combat.poem.StandalonePoemController;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Ordinary attack intent. A negative slot cancels both animation and server blade collision. */
public record PoemAnimationRequestPacket(int slot, int mode) implements CustomPacketPayload {
    public static final Type<PoemAnimationRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("herobrine_companion", "poem_animation_request"));
    public static final StreamCodec<ByteBuf, PoemAnimationRequestPacket> STREAM_CODEC = new StreamCodec<>() {
        public PoemAnimationRequestPacket decode(ByteBuf buf) { return new PoemAnimationRequestPacket(buf.readByte(), buf.readByte()); }
        public void encode(ByteBuf buf, PoemAnimationRequestPacket packet) { buf.writeByte(packet.slot); buf.writeByte(packet.mode); }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(PoemAnimationRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (packet.slot == -1) StandalonePoemController.stop(player);
                else StandalonePoemController.request(player, packet.slot, packet.mode);
            }
        });
    }
}
