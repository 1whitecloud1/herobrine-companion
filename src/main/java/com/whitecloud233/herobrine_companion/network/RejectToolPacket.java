package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolConfirmationStore;
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
 * C→S 审批拒绝（P3）：玩家在确认屏点"拒绝"后，丢弃待确认调用并补发失败结果，
 * 让客户端等待的合成调用及时结束（不会谎称已执行）。
 */
public record RejectToolPacket(UUID requestId) implements CustomPacketPayload {

    public static final Type<RejectToolPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "reject_tool"));

    private static final StreamCodec<FriendlyByteBuf, UUID> UUID_STREAM_CODEC =
            StreamCodec.<FriendlyByteBuf, UUID>of((buf, uuid) -> buf.writeUUID(uuid), buf -> buf.readUUID());

    public static final StreamCodec<FriendlyByteBuf, RejectToolPacket> STREAM_CODEC = StreamCodec.composite(
            UUID_STREAM_CODEC,
            RejectToolPacket::requestId,
            RejectToolPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RejectToolPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player != null) {
                reject(player, packet.requestId());
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
