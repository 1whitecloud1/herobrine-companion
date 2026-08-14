package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientAgentToolConfirmation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * S→C 审批提示（P3）：当工具 {@code requiresConfirmation} 且未获玩家确认时，把待确认详情
 * （工具 / 参数 / 描述）送到客户端弹确认屏。requestId 与 {@code AgentRequestPacket} / {@code AgentToolResultPacket} 贯通。
 * description 为<b>玩家可见的本地化组件</b>，以 {@link Component.Serializer} JSON 形式跨线程传输。
 */
public record ToolApprovalPromptPacket(UUID requestId, String toolId, Map<String, String> args, Component description) implements CustomPacketPayload {

    private static final int MAX_DESCRIPTION_JSON = 1024;

    public static final Type<ToolApprovalPromptPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "tool_approval_prompt"));

    public static final StreamCodec<FriendlyByteBuf, ToolApprovalPromptPacket> STREAM_CODEC =
            StreamCodec.ofMember(ToolApprovalPromptPacket::encode, ToolApprovalPromptPacket::new);

    public ToolApprovalPromptPacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUtf(64), readArgs(buf), readComponent(buf));
    }

    public ToolApprovalPromptPacket {
        toolId = toolId == null ? "" : toolId;
        args = args == null ? Map.of() : Map.copyOf(args);
        description = description == null ? Component.empty() : description;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.requestId);
        buf.writeUtf(this.toolId, 64);
        buf.writeVarInt(this.args.size());
        for (Map.Entry<String, String> entry : this.args.entrySet()) {
            buf.writeUtf(entry.getKey(), 64);
            buf.writeUtf(entry.getValue(), 256);
        }
        buf.writeUtf(Component.Serializer.toJson(this.description, RegistryAccess.EMPTY), MAX_DESCRIPTION_JSON);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToolApprovalPromptPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientAgentToolConfirmation.accept(packet.requestId(), packet.toolId(), packet.args(), packet.description());
            }
        });
    }

    private static Map<String, String> readArgs(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, String> args = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            args.put(buf.readUtf(64), buf.readUtf(256));
        }
        return Map.copyOf(args);
    }

    /** 反序列化本地化组件；空串 / 解析失败回退为空白组件，保证网络线程不抛异常。 */
    private static Component readComponent(FriendlyByteBuf buf) {
        String json = buf.readUtf(MAX_DESCRIPTION_JSON);
        if (json.isEmpty()) {
            return Component.empty();
        }
        try {
            Component parsed = Component.Serializer.fromJsonLenient(json, RegistryAccess.EMPTY);
            return parsed == null ? Component.empty() : parsed;
        } catch (RuntimeException e) {
            return Component.empty();
        }
    }
}
