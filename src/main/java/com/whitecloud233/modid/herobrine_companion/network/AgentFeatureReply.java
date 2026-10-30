package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端的扩展能力回执（交接 4.2 / T22）。
 *
 * <p>客户端在连接建立后请求一次，服务端回自己实现的能力位；交集才是"这一局真的可用"
 * 的能力集合。旧服务端根本不会注册本包，channel 协商失败 → 客户端不展示新工具。</p>
 *
 * <p><b>单一职责</b>：只搬运两个字段。</p>
 */
public class AgentFeatureReply {

    private final UUID requestId;
    private final int features;

    public AgentFeatureReply(UUID requestId, int features) {
        this.requestId = requestId == null ? new UUID(0L, 0L) : requestId;
        this.features = features;
    }

    public AgentFeatureReply(FriendlyByteBuf buf) {
        this.requestId = buf.readUUID();
        this.features = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.requestId);
        buf.writeVarInt(this.features);
    }

    public UUID requestId() {
        return requestId;
    }

    public int features() {
        return features;
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueClient(context, () -> NetworkClientBridge.acceptAgentFeatures(
                this.requestId, this.features));
    }
}
