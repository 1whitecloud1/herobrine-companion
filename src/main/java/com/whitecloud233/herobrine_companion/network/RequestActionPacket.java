package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.HeroQuestHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestActionPacket(int entityId, int questId, Action action) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestActionPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "request_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestActionPacket> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.INT, RequestActionPacket::entityId,
            net.minecraft.network.codec.ByteBufCodecs.INT, RequestActionPacket::questId,
            net.minecraft.network.codec.ByteBufCodecs.idMapper(i -> Action.values()[i], Enum::ordinal), RequestActionPacket::action,
            RequestActionPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(packet.entityId);
                if (entity instanceof HeroEntity hero) {
                    if (packet.action == Action.ACCEPT) {
                        HeroQuestHandler.startQuest(hero, player, packet.questId);
                    } else if (packet.action == Action.CANCEL) {
                        HeroQuestHandler.cancelQuest(hero, player);
                    }
                }
            }
        });
    }

    public enum Action {
        ACCEPT,
        COMPLETE,
        CANCEL
    }
}
