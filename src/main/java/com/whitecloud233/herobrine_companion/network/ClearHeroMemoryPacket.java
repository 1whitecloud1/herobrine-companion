package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.HeroAgent;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C→S 记忆清理（记忆管理器按钮）：清空 HeroMemory 的叙事层（保留玩家偏好）或全部。
 * 清理后立即落盘，并回发最新（通常为空）摘要让客户端缓存同步。
 */
public record ClearHeroMemoryPacket(int mode) implements CustomPacketPayload {

    /** 只清叙事（episodes），保留玩家偏好 facts。 */
    public static final int MODE_KEEP_FACTS = 0;

    /** 清空全部记忆。 */
    public static final int MODE_CLEAR_ALL = 1;

    public static final Type<ClearHeroMemoryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "clear_hero_memory"));

    public static final StreamCodec<ByteBuf, ClearHeroMemoryPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ClearHeroMemoryPacket::mode,
            ClearHeroMemoryPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClearHeroMemoryPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player != null) {
                clear(player, packet.mode());
            }
        });
    }

    private static void clear(ServerPlayer player, int mode) {
        if (player.getServer() == null) {
            return;
        }
        HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(player.getServer(), player.getUUID());
        if (hero == null || !hero.isAlive()) {
            return;
        }
        HeroAgent agent = hero.getHeroAgent();
        if (agent != null) {
            agent.clearMemory(hero, mode != MODE_CLEAR_ALL);
        }
        // 清理后回发最新摘要（此时通常为空），让客户端缓存同步。
        HeroAgent cleared = agent;
        PacketHandler.sendToPlayer(new HeroMemoryDigestPacket(
                cleared == null ? "" : cleared.buildMemoryDigest(hero)), player);
    }
}
