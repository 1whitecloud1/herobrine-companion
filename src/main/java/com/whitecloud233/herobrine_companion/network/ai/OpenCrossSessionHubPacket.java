package com.whitecloud233.herobrine_companion.network.ai;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.network.ClientOnlyExecutor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class OpenCrossSessionHubPacket implements CustomPacketPayload {
    public static final Type<OpenCrossSessionHubPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "open_cross_session_hub"));
    public static final StreamCodec<FriendlyByteBuf, OpenCrossSessionHubPacket> STREAM_CODEC =
            StreamCodec.ofMember(OpenCrossSessionHubPacket::encode, OpenCrossSessionHubPacket::new);

    private final int entityId;

    public OpenCrossSessionHubPacket(int entityId) {
        this.entityId = entityId;
    }

    public OpenCrossSessionHubPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenCrossSessionHubPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientOnlyExecutor.invoke(
                OpenCrossSessionHubPacket.ClientHandler.class.getName(),
                "handle",
                new Class<?>[]{OpenCrossSessionHubPacket.class},
                packet
        ));
    }

    private static final class ClientHandler {
        private static void handle(OpenCrossSessionHubPacket packet) {
            com.whitecloud233.herobrine_companion.client.event.ClientHooks.openCrossSessionHub(packet.entityId);
        }
    }
}
