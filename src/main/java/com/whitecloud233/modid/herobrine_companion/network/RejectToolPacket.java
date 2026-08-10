package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolConfirmationStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * C→S 审批拒绝（P3）：玩家在确认屏点"拒绝"后，丢弃待确认调用并补发失败结果，
 * 让客户端等待的合成调用及时结束（不会谎称已执行）。
 *
 * <p><b>单一职责</b>：只做"丢弃 + 回执"。</p>
 */
public class RejectToolPacket {

    private final UUID requestId;

    public RejectToolPacket(UUID requestId) {
        this.requestId = requestId;
    }

    public RejectToolPacket(FriendlyByteBuf buf) {
        this.requestId = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.requestId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                reject(player, this.requestId);
            }
        });
    }

    private static void reject(ServerPlayer player, UUID requestId) {
        long gameTime = player.getServer() == null ? 0L : player.getServer().overworld().getGameTime();
        AgentToolConfirmationStore.ConfirmationEntry entry = AgentToolConfirmationStore.take(requestId, gameTime);
        if (requestId != null) {
            PacketHandler.sendToPlayer(new AgentToolResultPacket(
                    requestId, entry == null ? "" : entry.toolId(), false, "已取消该操作"), player);
        }
    }
}
