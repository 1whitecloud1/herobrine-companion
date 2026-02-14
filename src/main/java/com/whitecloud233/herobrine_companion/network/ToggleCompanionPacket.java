package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
                    // 再次在服务端校验信任度，防止作弊
                    if (hero.getTrustLevel() >= 50) {
                        boolean newState = !hero.isCompanionMode();
                        hero.setCompanionMode(newState);
                        
                        // 发送反馈消息
                        String msgKey = newState ? "message.herobrine_companion.companion_on" : "message.herobrine_companion.companion_off";
                        serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable(msgKey));
                    }
                }
            }
        });
    }
}
