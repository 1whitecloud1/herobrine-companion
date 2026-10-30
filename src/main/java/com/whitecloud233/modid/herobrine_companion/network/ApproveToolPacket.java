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
 * C→S 审批同意：玩家在确认屏点"确认"后，按 requestId 取回待确认工具调用，
 * 以 {@code playerApproved=true} 重新投递给 agent 执行；结果经 {@link AgentToolResultPacket} 回喂。
 *
 * <p><b>单一职责</b>：只做"审批通过 → 重新投递"。校验与执行全在服务端 agent 内。</p>
 *
 * <p><b>有效性复核</b>（交接 A3）：确认时按<b>确认者 == 原请求者</b>、<b>维度一致</b>、
 * <b>未过期</b>重新核对，避免玩家在别的世界点下确认后操作到早已失效的对象；
 * 重复确认（条目已被取走）返回明确终态，不会第二次执行。</p>
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
            // 已过期 / 已确认过 / 不存在：补发确定终态，避免客户端等待悬挂，也不重复执行。
            if (requestId != null) {
                PacketHandler.sendToPlayer(new AgentToolResultPacket(
                        requestId, "", false, "确认请求已过期或已处理，未执行"), player);
            }
            return;
        }
        if (entry.requesterUuid() != null && !entry.requesterUuid().equals(player.getUUID())) {
            // 不是原请求者：作废该条，不执行（防止他人替你确认已过期的对象）。
            PacketHandler.sendToPlayer(new AgentToolResultPacket(
                    requestId, entry.toolId(), false, "该确认请求不属于你，已作废"), player);
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
        if (!sameDimension(hero, entry)) {
            PacketHandler.sendToPlayer(new AgentToolResultPacket(
                    requestId, entry.toolId(), false, "场景已改变（维度不同），该操作未执行"), player);
            return;
        }
        // 以确认者身份重新投递，confirmed=true；结果送回确认者。
        hero.getHeroAgent().submitToolRequest(hero, entry.toolId(), entry.args(), player.getUUID(), true, requestId);
    }

    private static boolean sameDimension(HeroEntity hero, AgentToolConfirmationStore.ConfirmationEntry entry) {
        if (entry.dimensionId() == null || entry.dimensionId().isBlank() || hero.level() == null) {
            return true;
        }
        return entry.dimensionId().equals(hero.level().dimension().location().toString());
    }
}
