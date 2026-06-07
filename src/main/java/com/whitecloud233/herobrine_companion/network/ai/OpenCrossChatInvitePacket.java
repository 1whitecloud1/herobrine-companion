package com.whitecloud233.herobrine_companion.network.ai;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.network.ClientOnlyExecutor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public class OpenCrossChatInvitePacket implements CustomPacketPayload {
    public static final Type<OpenCrossChatInvitePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "open_cross_chat_invite"));
    public static final StreamCodec<FriendlyByteBuf, OpenCrossChatInvitePacket> STREAM_CODEC =
            StreamCodec.ofMember(OpenCrossChatInvitePacket::encode, OpenCrossChatInvitePacket::new);

    private final UUID requesterId;
    private final String requesterName;

    public OpenCrossChatInvitePacket(UUID requesterId, String requesterName) {
        this.requesterId = requesterId == null ? new UUID(0L, 0L) : requesterId;
        this.requesterName = requesterName == null ? "" : requesterName;
    }

    public OpenCrossChatInvitePacket(FriendlyByteBuf buf) {
        this.requesterId = buf.readUUID();
        this.requesterName = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.requesterId);
        buf.writeUtf(this.requesterName, 256);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenCrossChatInvitePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientOnlyExecutor.invoke(
                OpenCrossChatInvitePacket.ClientHandler.class.getName(),
                "handle",
                new Class<?>[]{OpenCrossChatInvitePacket.class},
                packet
        ));
    }

    private static final class ClientHandler {
        private static void handle(OpenCrossChatInvitePacket packet) {
            com.whitecloud233.herobrine_companion.client.event.ClientHooks.openCrossChatInvite(packet.requesterId, packet.requesterName);
        }
    }
}
