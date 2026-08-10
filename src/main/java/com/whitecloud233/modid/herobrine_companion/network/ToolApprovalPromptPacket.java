package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * S→C 审批提示（P3）：当工具 {@code requiresConfirmation} 且未获玩家确认时，把待确认详情
 * （工具 / 参数 / 描述）送到客户端弹确认屏。requestId 与 {@code AgentRequestPacket} / {@code AgentToolResultPacket} 贯通。
 *
 * <p><b>单一职责</b>：只做搬运。description 为<b>玩家可见的本地化组件</b>（聊天/确认屏渲染，
 * 不复用 LLM 用工具 description），以 {@link Component.Serializer} JSON 形式跨线程传输，
 * 客户端渲染时按其语言解析。</p>
 */
public class ToolApprovalPromptPacket {

    private static final int MAX_DESCRIPTION_JSON = 1024;

    private final UUID requestId;
    private final String toolId;
    private final Map<String, String> args;
    private final Component description;

    public ToolApprovalPromptPacket(UUID requestId, String toolId, Map<String, String> args, Component description) {
        this.requestId = requestId;
        this.toolId = toolId == null ? "" : toolId;
        this.args = args == null ? Map.of() : Map.copyOf(args);
        this.description = description == null ? Component.empty() : description;
    }

    public ToolApprovalPromptPacket(FriendlyByteBuf buf) {
        this.requestId = buf.readUUID();
        this.toolId = buf.readUtf(64);
        int size = buf.readVarInt();
        Map<String, String> args = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            args.put(buf.readUtf(64), buf.readUtf(256));
        }
        this.args = Map.copyOf(args);
        this.description = readComponent(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.requestId);
        buf.writeUtf(this.toolId, 64);
        buf.writeVarInt(this.args.size());
        for (Map.Entry<String, String> entry : this.args.entrySet()) {
            buf.writeUtf(entry.getKey(), 64);
            buf.writeUtf(entry.getValue(), 256);
        }
        buf.writeUtf(Component.Serializer.toJson(this.description), MAX_DESCRIPTION_JSON);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.assertClient(context);
        context.enqueueWork(() -> NetworkClientBridge.acceptToolApprovalPrompt(
                this.requestId, this.toolId, this.args, this.description));
        context.setPacketHandled(true);
    }

    /** 反序列化本地化组件；空串 / 解析失败回退为空白组件，保证网络线程不抛异常。 */
    private static Component readComponent(FriendlyByteBuf buf) {
        String json = buf.readUtf(MAX_DESCRIPTION_JSON);
        if (json.isEmpty()) {
            return Component.empty();
        }
        try {
            Component parsed = Component.Serializer.fromJson(json);
            return parsed == null ? Component.empty() : parsed;
        } catch (RuntimeException e) {
            return Component.empty();
        }
    }
}
