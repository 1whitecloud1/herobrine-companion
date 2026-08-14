package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientAgentToolResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * S→C 工具结果（M4 共享基础设施）：把服务端 agent 工具执行结果送回发起请求的客户端，
 * 供 {@code ClientAgentToolRequestStore} 存留（P2 起注入 LLM 上下文 / 关联合成调用）。
 */
public record AgentToolResultPacket(UUID requestId, String toolId, boolean ok, String message) implements CustomPacketPayload {

    public static final Type<AgentToolResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "agent_tool_result"));

    private static final StreamCodec<FriendlyByteBuf, UUID> UUID_STREAM_CODEC =
            StreamCodec.<FriendlyByteBuf, UUID>of((buf, uuid) -> buf.writeUUID(uuid), buf -> buf.readUUID());

    public static final StreamCodec<FriendlyByteBuf, AgentToolResultPacket> STREAM_CODEC = StreamCodec.composite(
            UUID_STREAM_CODEC,
            AgentToolResultPacket::requestId,
            ByteBufCodecs.stringUtf8(64),
            AgentToolResultPacket::toolId,
            ByteBufCodecs.BOOL,
            AgentToolResultPacket::ok,
            ByteBufCodecs.stringUtf8(512),
            AgentToolResultPacket::message,
            AgentToolResultPacket::new
    );

    public AgentToolResultPacket {
        toolId = toolId == null ? "" : toolId;
        message = message == null ? "" : message;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AgentToolResultPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientAgentToolResult.accept(packet.requestId(), packet.toolId(), packet.ok(), packet.message());
            }
        });
    }
}
