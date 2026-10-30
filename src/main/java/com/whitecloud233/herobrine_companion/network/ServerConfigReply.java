package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientServerConfig;
import com.whitecloud233.herobrine_companion.config.ServerConfigSnapshot;
import com.whitecloud233.herobrine_companion.config.ServerConfigUpdate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerConfigReply(int requestId, ServerConfigSnapshot snapshot, boolean canEdit, boolean accepted)
        implements CustomPacketPayload {
    public static final Type<ServerConfigReply> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "server_config_reply"));

    public static final StreamCodec<FriendlyByteBuf, ServerConfigReply> STREAM_CODEC =
            StreamCodec.ofMember(ServerConfigReply::encode, ServerConfigReply::new);

    public ServerConfigReply(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), ServerConfigCodec.readSnapshot(buffer), buffer.readBoolean(), buffer.readBoolean());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        ServerConfigCodec.writeUpdate(buffer, ServerConfigUpdate.full(snapshot));
        buffer.writeBoolean(canEdit);
        buffer.writeBoolean(accepted);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ServerConfigReply payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientServerConfig.accept(context.connection(), payload);
        });
    }
}
