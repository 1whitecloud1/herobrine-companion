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

    /** 协议上限（与既有布局一致）；超过会让流编解码抛异常，必须在构造时截断。 */
    public static final int MAX_TOOL_ID = 64;
    public static final int MAX_MESSAGE = 512;

    private static final StreamCodec<FriendlyByteBuf, UUID> UUID_STREAM_CODEC =
            StreamCodec.<FriendlyByteBuf, UUID>of((buf, uuid) -> buf.writeUUID(uuid), buf -> buf.readUUID());

    public static final StreamCodec<FriendlyByteBuf, AgentToolResultPacket> STREAM_CODEC = StreamCodec.composite(
            UUID_STREAM_CODEC,
            AgentToolResultPacket::requestId,
            ByteBufCodecs.stringUtf8(MAX_TOOL_ID),
            AgentToolResultPacket::toolId,
            ByteBufCodecs.BOOL,
            AgentToolResultPacket::ok,
            ByteBufCodecs.stringUtf8(MAX_MESSAGE),
            AgentToolResultPacket::message,
            AgentToolResultPacket::new
    );

    public AgentToolResultPacket {
        toolId = clamp(toolId, MAX_TOOL_ID);
        // 工具回执可能带容器清单 / 进度描述，超长会让编解码抛异常、整包发不出去，
        // 客户端于是只能等超时。这里按协议上限截断，并保留"被截断"的可读提示。
        message = clamp(message, MAX_MESSAGE);
    }

    /** 截断到协议允许的字符数，末尾留省略号让读的一方知道被截过。 */
    private static String clamp(String value, int max) {
        String text = value == null ? "" : value;
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)) + "…";
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
