package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端的扩展能力查询（交接 4.2 / T22）。
 *
 * <p>服务端回自己的能力位；请求本身不需要参数（除了用于关联的 requestId）。
 * 旧服务端不注册本包 → 客户端的 channel 协商失败 → 不发送、也不展示新能力。</p>
 */
public class AgentFeatureRequest {

    private final UUID requestId;

    public AgentFeatureRequest(UUID requestId) {
        this.requestId = requestId == null ? UUID.randomUUID() : requestId;
    }

    public AgentFeatureRequest(FriendlyByteBuf buf) {
        this.requestId = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.requestId);
    }

    public UUID requestId() {
        return requestId;
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                AgentFeatureChannel.reply(player, this.requestId, AgentFeatureChannel.ALL_FEATURES);
            }
        });
    }
}
