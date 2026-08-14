package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public record HeroAIActionPacket(String action) implements CustomPacketPayload {
    public static final String ACTION_DISCARD_ENTITIES = "action:discard_entities";
    public static final String ACTION_DISCARD = "action:discard";
    private static final double DISCARD_ENTITY_RADIUS = 32.0D;
    private static final int DISCARD_CHUNK_RADIUS = 10;

    public static final Type<HeroAIActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "hero_ai_action"));

    public static final StreamCodec<ByteBuf, HeroAIActionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64),
            HeroAIActionPacket::action,
            HeroAIActionPacket::new
    );

    public HeroAIActionPacket {
        action = action == null ? "" : action;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HeroAIActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player != null) {
                performAction(player, packet.action());
            }
        });
    }

    public static boolean isSupportedAction(String action) {
        String normalizedAction = normalizeAction(action);
        return ACTION_DISCARD_ENTITIES.equals(normalizedAction)
                || ACTION_DISCARD.equals(normalizedAction);
    }

    public static boolean performAction(ServerPlayer player, String action) {
        String normalizedAction = normalizeAction(action);
        if (player == null || player.getServer() == null || !isSupportedAction(normalizedAction)) {
            return false;
        }

        HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(player.getServer(), player.getUUID());
        if (hero == null || !hero.isAlive()) {
            return false;
        }

        return switch (normalizedAction) {
            case ACTION_DISCARD_ENTITIES -> discardNearbyEntities(player, hero);
            case ACTION_DISCARD -> discardChunksAroundPlayer(player, hero);
            default -> false;
        };
    }
    private static String normalizeAction(String action) {
        return action == null ? "" : action.trim();
    }

    private static boolean discardNearbyEntities(ServerPlayer player, HeroEntity hero) {
        AABB bounds = new AABB(player.blockPosition()).inflate(DISCARD_ENTITY_RADIUS, 12.0D, DISCARD_ENTITY_RADIUS);
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_ai.discard_start"));

        int discardedEntities = 0;
        for (Entity entity : player.serverLevel().getEntities(null, bounds)) {
            if (entity == null || entity.isRemoved() || entity == player || entity == hero
                    || entity instanceof ServerPlayer || entity instanceof HeroEntity) {
                continue;
            }
            entity.discard();
            discardedEntities++;
        }

        player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_ai.discard_complete", discardedEntities));
        return discardedEntities > 0;
    }

    private static boolean discardChunksAroundPlayer(ServerPlayer player, HeroEntity hero) {
        hero.getNavigation().stop();
        hero.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_ai.discard_escalated"));
        return ClearAreaPacket.startVoidDomain(player, DISCARD_CHUNK_RADIUS, false, false,
                Component.translatable("message.herobrine_companion.hero_ai.discard_reality"));
    }
}
