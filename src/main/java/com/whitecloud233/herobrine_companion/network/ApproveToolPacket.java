package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolConfirmationStore;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * C→S 审批同意（P3）：玩家在确认屏点"确认"后，按 requestId 取回待确认工具调用，
 * 以 {@code playerApproved=true} 重新注入 agent 循环执行；结果经 {@link AgentToolResultPacket} 回喂。
 */
public record ApproveToolPacket(UUID requestId) implements CustomPacketPayload {

    public static final Type<ApproveToolPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "approve_tool"));

    private static final StreamCodec<FriendlyByteBuf, UUID> UUID_STREAM_CODEC =
            StreamCodec.<FriendlyByteBuf, UUID>of((buf, uuid) -> buf.writeUUID(uuid), buf -> buf.readUUID());

    public static final StreamCodec<FriendlyByteBuf, ApproveToolPacket> STREAM_CODEC = StreamCodec.composite(
            UUID_STREAM_CODEC,
            ApproveToolPacket::requestId,
            ApproveToolPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ApproveToolPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player != null) {
                approve(player, packet.requestId());
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
