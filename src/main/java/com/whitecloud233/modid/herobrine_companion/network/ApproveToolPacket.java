package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolConfirmationStore;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * C→S 审批同意（P3）：玩家在确认屏点"确认"后，按 requestId 取回待确认工具调用，
 * 以 {@code playerApproved=true} 重新注入 agent 循环执行；结果经 {@link AgentToolResultPacket} 回喂。
 *
 * <p><b>单一职责</b>：只做"审批通过 → 重新入队"。校验与执行全在服务端 agent 内。</p>
 */
public class ApproveToolPacket {

    private final UUID requestId;

    public ApproveToolPacket(UUID requestId) {
        this.requestId = requestId;
    }

    public ApproveToolPacket(FriendlyByteBuf buf) {
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
                approve(player, this.requestId);
            }
        });
    }

    private static void approve(ServerPlayer player, UUID requestId) {
        long gameTime = player.getServer() == null ? 0L : player.getServer().overworld().getGameTime();
        AgentToolConfirmationStore.ConfirmationEntry entry = AgentToolConfirmationStore.take(requestId, gameTime);
        if (entry == null) {
            // 已过期 / 不存在：补发失败结果，避免客户端等待悬挂。
            if (requestId != null) {
                PacketHandler.sendToPlayer(new AgentToolResultPacket(requestId, "", false, "确认请求已过期"), player);
            }
            return;
        }
        if (player.getServer() == null) {
            return;
        }
        HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(player.getServer(), player.getUUID());
        if (hero == null || !hero.isAlive()) {
            PacketHandler.sendToPlayer(new AgentToolResultPacket(requestId, entry.toolId(), false, "我暂时不在，无法处理"), player);
            return;
        }
        // 以确认者身份重新注入，confirmed=true；结果送回确认者。
        hero.getHeroAgent().requestTool(entry.toolId(), entry.args(), player.getUUID(), true, requestId);
    }
}
