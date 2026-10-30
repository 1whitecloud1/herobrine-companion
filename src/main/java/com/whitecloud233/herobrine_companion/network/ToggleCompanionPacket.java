package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import com.whitecloud233.herobrine_companion.entity.companion.CompanionModePolicy;
import com.whitecloud233.herobrine_companion.entity.companion.CompanionModeService;
import com.whitecloud233.herobrine_companion.util.HeroMenuValidity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleCompanionPacket(int entityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleCompanionPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "toggle_companion"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleCompanionPacket> STREAM_CODEC = 
        StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, ToggleCompanionPacket::entityId,
            ToggleCompanionPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ToggleCompanionPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Entity entity = serverPlayer.level().getEntity(payload.entityId);
                if (entity instanceof HeroEntity hero) {
                    if (!serverPlayer.isAlive() || !HeroMenuValidity.isHeroInPlayerLoadedArea(hero, serverPlayer)) {
                        serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.companion_too_far"));
                        return;
                    }
                    boolean desiredMode = !hero.isCompanionMode();
                    CompanionModePolicy.Result result = CompanionModeService.changeMode(serverPlayer.getUUID(), hero, desiredMode);
                    if (result.successful()) {
                        String key = desiredMode ? "message.herobrine_companion.companion_on" : "message.herobrine_companion.companion_off";
                        serverPlayer.sendSystemMessage(Component.translatable(key));
                    } else if (result == CompanionModePolicy.Result.NOT_OWNER) {
                        serverPlayer.sendSystemMessage(hero.createNotYourHeroMessage());
                    } else {
                        serverPlayer.sendSystemMessage(Component.translatable(result.messageKey()));
                    }
                }
            }
        });
    }
}
