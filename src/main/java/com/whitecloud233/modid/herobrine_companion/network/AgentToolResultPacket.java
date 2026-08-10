package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * S→C 工具结果（M4 共享基础设施）：把服务端 agent 工具执行结果送回发起请求的客户端，
 * 供 {@code ClientAgentToolRequestStore} 存留（P2 起注入 LLM 上下文 / 关联合成调用）。
 *
 * <p><b>单一职责</b>：只做搬运。requestId 由客户端生成，贯穿
 * {@code AgentRequestPacket} → {@code AgentToolRequest} → {@code DefaultAgentExecutor} → 本包，用于关联；
 * 客户端落地经 {@link NetworkClientBridge}（唯一 DistExecutor 边界）。</p>
 */
public class AgentToolResultPacket {

    private final UUID requestId;
    private final String toolId;
    private final boolean ok;
    private final String message;

    public AgentToolResultPacket(UUID requestId, String toolId, boolean ok, String message) {
        this.requestId = requestId;
        this.toolId = toolId == null ? "" : toolId;
        this.ok = ok;
        this.message = message == null ? "" : message;
    }

    public AgentToolResultPacket(FriendlyByteBuf buf) {
        this.requestId = buf.readUUID();
        this.toolId = buf.readUtf(64);
        this.ok = buf.readBoolean();
        this.message = buf.readUtf(512);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.requestId);
        buf.writeUtf(this.toolId, 64);
        buf.writeBoolean(this.ok);
        buf.writeUtf(this.message, 512);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.assertClient(context);
        context.enqueueWork(() -> NetworkClientBridge.acceptAgentToolResult(
                this.requestId, this.toolId, this.ok, this.message));
        context.setPacketHandled(true);
    }
}
