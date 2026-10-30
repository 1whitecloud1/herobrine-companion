package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * 客户端 → 服务端的扩展能力查询（交接 4.2 / T22），NeoForge 1.21.1 版本。
 *
 * <p>服务端回自己的能力位；请求只需 requestId 做关联。旧服务端不注册本载荷，
 * 客户端根本不会发（{@link AgentFeatureChannel#supported} 为 false）。</p>
 */
public record AgentFeatureRequest(UUID requestId) implements CustomPacketPayload {

    public static final Type<AgentFeatureRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "agent_feature_request"));

    public static final StreamCodec<FriendlyByteBuf, AgentFeatureRequest> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeUUID(packet.requestId()),
            buf -> new AgentFeatureRequest(buf.readUUID()));

    public AgentFeatureRequest {
        requestId = requestId == null ? UUID.randomUUID() : requestId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AgentFeatureRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player != null) {
                AgentFeatureChannel.reply(player, packet.requestId(), AgentFeatureChannel.ALL_FEATURES);
            }
        });
    }
}
