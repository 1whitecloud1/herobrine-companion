package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.companion.BattleModePolicy;
import com.whitecloud233.herobrine_companion.entity.companion.BattleModeService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

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

            boolean desired = !hero.isBattleModeActive();
            BattleModePolicy.Result result = BattleModeService.changeMode(serverPlayer.getUUID(), hero, desired);
            if (!result.successful()) {
                if (result == BattleModePolicy.Result.NOT_OWNER) {
                    serverPlayer.sendSystemMessage(hero.createNotYourHeroMessage());
                } else if (result == BattleModePolicy.Result.TRUST_TOO_LOW) {
                    serverPlayer.sendSystemMessage(Component.translatable(result.messageKey(),
                            BattleModeService.trustThreshold(), hero.getTrustLevel()));
                } else if (result == BattleModePolicy.Result.COOLDOWN) {
                    serverPlayer.sendSystemMessage(Component.translatable(result.messageKey(),
                            BattleModeService.cooldownRemainingTicks(hero) / 20L + 1L));
                } else {
                    serverPlayer.sendSystemMessage(Component.translatable(result.messageKey()));
                }
                return;
            }

            if (desired) {
                serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.battle_mode_on"));
                int durationSeconds = BattleModeService.durationTicks() / 20;
                if (durationSeconds > 0) {
                    serverPlayer.sendSystemMessage(Component.translatable(
                            "message.herobrine_companion.battle_mode_on_timed", durationSeconds));
                }
            } else {
                serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.battle_mode_off"));
            }
        });
    }
}
