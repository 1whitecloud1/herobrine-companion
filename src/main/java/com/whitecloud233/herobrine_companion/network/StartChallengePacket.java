package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.fight.HeroChallengeManager;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record StartChallengePacket(int entityId, int challengeMode) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StartChallengePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "start_challenge"));

    public static final StreamCodec<ByteBuf, StartChallengePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, StartChallengePacket::entityId,
            ByteBufCodecs.INT, StartChallengePacket::challengeMode,
            StartChallengePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // [核心修复] 添加 static 关键字，使其成为静态方法，这样 PacketHandler 里的方法引用才会生效
    public static void handle(StartChallengePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServerLevel level = (ServerLevel) player.level();
                Entity entity = level.getEntity(payload.entityId());

                if (entity instanceof HeroEntity hero) {
                    // [核心修复] 在静态方法中没有 this，必须改回通过 payload 获取 challengeMode()
                    HeroChallengeManager.startChallenge(hero, player, payload.challengeMode());
                }
            }
        });
    }
}