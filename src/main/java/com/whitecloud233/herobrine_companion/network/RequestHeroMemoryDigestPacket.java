package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.HeroAgent;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C→S 记忆摘要请求：客户端进入聊天 / 需要时向服务端索要 {@code HeroMemory} 的
 * 长期记忆 digest（玩家偏好 + 重要情景 + 框定语），供注入聊天 LLM 上下文。
 * 包体无载荷 —— 目标 Hero 由发送者 UUID 反查；无 Hero 时静默返回空摘要。
 */
public record RequestHeroMemoryDigestPacket() implements CustomPacketPayload {

    public static final Type<RequestHeroMemoryDigestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "request_hero_memory_digest"));

    public static final StreamCodec<ByteBuf, RequestHeroMemoryDigestPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestHeroMemoryDigestPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestHeroMemoryDigestPacket packet, IPayloadContext context) {
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
            PacketHandler.sendToPlayer(new HeroMemoryDigestPacket(""), player);
            return;
        }
        HeroAgent agent = hero.getHeroAgent();
        String digest = agent == null ? "" : agent.buildMemoryDigest(hero);
        PacketHandler.sendToPlayer(new HeroMemoryDigestPacket(digest), player);
    }
}
