package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.client.animation.StandalonePoemAnimation;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The sender chooses no entity, damage, animation index or playback speed. */
public record PoemAnimationPacket(int entityId, UUID playerId, int slot, int mode, int step,
                                  long startTick, long serverTick) implements CustomPacketPayload {
    public static final Type<PoemAnimationPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("herobrine_companion", "poem_animation"));
    public static final StreamCodec<FriendlyByteBuf, PoemAnimationPacket> STREAM_CODEC = new StreamCodec<>() {
        public PoemAnimationPacket decode(FriendlyByteBuf buf) {
            return new PoemAnimationPacket(buf.readVarInt(), buf.readUUID(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readLong(), buf.readLong());
        }
        public void encode(FriendlyByteBuf buf, PoemAnimationPacket packet) {
            buf.writeVarInt(packet.entityId); buf.writeUUID(packet.playerId); buf.writeByte(packet.slot);
            buf.writeByte(packet.mode); buf.writeByte(packet.step); buf.writeLong(packet.startTick); buf.writeLong(packet.serverTick);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(PoemAnimationPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> StandalonePoemAnimation.receive(packet));
    }
}
