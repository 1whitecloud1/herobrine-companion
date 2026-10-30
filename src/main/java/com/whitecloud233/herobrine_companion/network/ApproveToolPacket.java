package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolConfirmationStore;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * C→S 审批同意：玩家在确认屏点"确认"后，按 requestId 取回待确认工具调用，
 * 以 {@code playerApproved=true} 重新投递给 agent 执行；结果经 {@link AgentToolResultPacket} 回喂。
 *
 * <p><b>有效性复核</b>（交接 A3）：确认时按<b>确认者 == 原请求者</b>、<b>维度一致</b>、
 * <b>未过期</b>重新核对，避免玩家在别的世界点下确认后操作到早已失效的对象；
 * 重复确认（条目已被取走）返回明确终态，不会第二次执行。</p>
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
