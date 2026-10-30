package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.config.ServerConfigAccess;
import com.whitecloud233.herobrine_companion.config.ServerConfigService;
import com.whitecloud233.herobrine_companion.config.ServerConfigUpdate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;

/** 可选配置通道：空补丁查询当前值，非空补丁只修改显式字段。 */
public record ServerConfigRequest(int requestId, ServerConfigUpdate update) implements CustomPacketPayload {
    public static final Type<ServerConfigRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "server_config_request"));

    public static final StreamCodec<FriendlyByteBuf, ServerConfigRequest> STREAM_CODEC =
            StreamCodec.ofMember(ServerConfigRequest::encode, ServerConfigRequest::new);

    public ServerConfigRequest(FriendlyByteBuf buffer) { this(buffer.readVarInt(), ServerConfigCodec.readUpdate(buffer)); }

    public static ServerConfigRequest query(int id) { return new ServerConfigRequest(id, new ServerConfigUpdate(Map.of())); }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        ServerConfigCodec.writeUpdate(buffer, update);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ServerConfigRequest payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerConfigService.Result result = payload.update().changes().isEmpty()
                    ? new ServerConfigService.Result(ServerConfigAccess.snapshot(), true, false)
                    : ServerConfigAccess.update(player, payload.update());
            ConfigSyncChannel.reply(player, payload.requestId(), result.authorized());
            if (result.changed()) ConfigSyncChannel.broadcast(player.getServer());
        });
    }
}
