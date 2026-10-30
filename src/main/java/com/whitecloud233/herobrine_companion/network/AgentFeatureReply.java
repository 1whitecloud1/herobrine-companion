package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 服务端 → 客户端的扩展能力回执（交接 4.2 / T22），NeoForge 1.21.1 版本。
 *
 * <p>客户端连接后请求一次，服务端回自己实现的能力位；交集才是"这一局真的可用"的集合。
 * 旧服务端不注册本载荷 → {@link AgentFeatureChannel#supported} 返回 false →
 * 客户端不发包、也不展示扩展工具。</p>
 */
public record AgentFeatureReply(UUID requestId, int features) implements CustomPacketPayload {

    public static final Type<AgentFeatureReply> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "agent_feature_reply"));

    private static final StreamCodec<FriendlyByteBuf, UUID> UUID_STREAM_CODEC =
            StreamCodec.<FriendlyByteBuf, UUID>of((buf, uuid) -> buf.writeUUID(uuid), buf -> buf.readUUID());

    public static final StreamCodec<FriendlyByteBuf, AgentFeatureReply> STREAM_CODEC = StreamCodec.composite(
            UUID_STREAM_CODEC,
            AgentFeatureReply::requestId,
            ByteBufCodecs.VAR_INT,
            AgentFeatureReply::features,
            AgentFeatureReply::new
    );

    public AgentFeatureReply {
        requestId = requestId == null ? new UUID(0L, 0L) : requestId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AgentFeatureReply packet, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.whitecloud233.herobrine_companion.client.network.ClientAgentFeatureHandler
                .accept(packet.features()));
    }
}
