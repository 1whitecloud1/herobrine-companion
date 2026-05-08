package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;

public record ToggleBattleModePacket(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ToggleBattleModePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "toggle_battle_mode"));

    public static final StreamCodec<ByteBuf, ToggleBattleModePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ToggleBattleModePacket::entityId,
            ToggleBattleModePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleBattleModePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }

            Entity entity = serverPlayer.level().getEntity(payload.entityId());
            if (!(entity instanceof HeroEntity hero)) {
                return;
            }

            if (hero.getOwnerUUID() != null && !Objects.equals(hero.getOwnerUUID(), serverPlayer.getUUID())) {
                serverPlayer.sendSystemMessage(hero.createNotYourHeroMessage());
                return;
            }

            if (hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
                serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.battle_mode_blocked_challenge"));
                return;
            }

            boolean newState = !hero.isBattleModeActive();
            hero.setBattleModeActive(newState);

            String key = newState
                    ? "message.herobrine_companion.battle_mode_on"
                    : "message.herobrine_companion.battle_mode_off";
            serverPlayer.sendSystemMessage(Component.translatable(key));
        });
    }
}

