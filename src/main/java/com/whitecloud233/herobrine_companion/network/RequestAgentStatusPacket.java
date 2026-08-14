package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.AgentStatusCollector;
import com.whitecloud233.herobrine_companion.entity.ai.agent.AgentStatusSnapshot;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C→S 状态查询（M5）：客户端请求自己绑定 Hero 的 agent 状态快照。
 * 包体无载荷 —— 目标 Hero 由发送者 UUID 在服务端反查，客户端无法指定他人的 Hero。
 */
public record RequestAgentStatusPacket() implements CustomPacketPayload {

    public static final Type<RequestAgentStatusPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "request_agent_status"));

    public static final StreamCodec<ByteBuf, RequestAgentStatusPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestAgentStatusPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestAgentStatusPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player != null) {
                respond(player);
            }
        });
    }

    private static void respond(ServerPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(player.getServer(), player.getUUID());
        if (hero == null || !hero.isAlive()) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.agent.not_available"));
            return;
        }
        AgentStatusSnapshot snapshot = AgentStatusCollector.collect(hero, hero.getHeroAgent());
        PacketHandler.sendToPlayer(new AgentStatusPacket(snapshot), player);
    }
}
