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

    /** 协议上限：message 超过它会让 writeUtf 抛 EncoderException，必须在构造时截断。 */
    private static final int MAX_TOOL_ID = 64;
    private static final int MAX_MESSAGE = 512;

    private final UUID requestId;
    private final String toolId;
    private final boolean ok;
    private final String message;

    public AgentToolResultPacket(UUID requestId, String toolId, boolean ok, String message) {
        this.requestId = requestId == null ? new UUID(0L, 0L) : requestId;
        this.toolId = clamp(toolId, MAX_TOOL_ID);
        this.ok = ok;
        // 工具回执可能带容器清单 / 进度描述，超长会让 writeUtf 抛异常、整包发不出去，
        // 客户端于是只能等超时。这里按协议上限截断，并保留"被截断"的可读提示。
        this.message = clamp(message, MAX_MESSAGE);
    }

    public AgentToolResultPacket(FriendlyByteBuf buf) {
        this.requestId = buf.readUUID();
        this.toolId = buf.readUtf(MAX_TOOL_ID);
        this.ok = buf.readBoolean();
        this.message = buf.readUtf(MAX_MESSAGE);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.requestId);
        buf.writeUtf(this.toolId, MAX_TOOL_ID);
        buf.writeBoolean(this.ok);
        buf.writeUtf(this.message, MAX_MESSAGE);
    }

    /**
     * 截断到协议允许的字符数。
     *
     * <p>按 {@code writeUtf} 的判定口径（UTF-16 字符数）截断，并在末尾留一个省略号，
     * 让读到回执的一方知道"这里被截过"而不是数据莫名变短。</p>
     */
    private static String clamp(String value, int max) {
        String text = value == null ? "" : value;
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)) + "…";
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueClient(context, () -> NetworkClientBridge.acceptAgentToolResult(
                this.requestId, this.toolId, this.ok, this.message));
    }
}
