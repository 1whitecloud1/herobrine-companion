package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.fight.HeroChallengeManager;
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
    public static final String ACTION_CHALLENGE_ACCEPT = "action:challenge_accept";
    public static final String ACTION_HERO_FLY_UP = "action:hero_fly_up";
    public static final String ACTION_HERO_LAND = "action:hero_land";
    private static final int DEFAULT_CHALLENGE_MODE = 1;
    public static final String ACTION_DISCARD_ENTITIES = "action:discard_entities";
    public static final String ACTION_DISCARD = "action:discard";
    private static final double DISCARD_ENTITY_RADIUS = 32.0D;
    private static final int DISCARD_CHUNK_RADIUS = 10;

    public static final Type<HeroAIActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "hero_ai_action"));

    public static final StreamCodec<ByteBuf, HeroAIActionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
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
        return ACTION_CHALLENGE_ACCEPT.equals(normalizedAction)
                || ACTION_HERO_FLY_UP.equals(normalizedAction)
                || ACTION_HERO_LAND.equals(normalizedAction)
                || ACTION_DISCARD_ENTITIES.equals(normalizedAction)
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
            case ACTION_CHALLENGE_ACCEPT -> startChallenge(player, hero);
            case ACTION_HERO_FLY_UP -> makeHeroFlyUp(hero);
            case ACTION_HERO_LAND -> makeHeroLand(hero);
            case ACTION_DISCARD_ENTITIES -> discardNearbyEntities(player, hero);
            case ACTION_DISCARD -> discardChunksAroundPlayer(player, hero);
            default -> false;
        };
    }
    private static String normalizeAction(String action) {
        return action == null ? "" : action.trim();
    }

    private static boolean startChallenge(ServerPlayer player, HeroEntity hero) {
        if (hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
            return true;
        }
        HeroChallengeManager.startChallenge(hero, player, DEFAULT_CHALLENGE_MODE);
        return true;
    }

    private static boolean makeHeroFlyUp(HeroEntity hero) {
        if (hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
            return false;
        }

        hero.setFloating(true);
        hero.setNoGravity(true);
        hero.getNavigation().stop();
        hero.setTarget(null);

        double targetY = Math.min(hero.getY() + 6.0D, hero.level().getMaxBuildHeight() - 2.0D);
        hero.teleportTo(hero.getX(), targetY, hero.getZ());
        hero.setDeltaMovement(0.0D, 0.0D, 0.0D);
        hero.setFallDistance(0.0F);
        return true;
    }

    private static boolean makeHeroLand(HeroEntity hero) {
        if (hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
            return false;
        }

        hero.setFloating(false);
        hero.setNoGravity(false);
        hero.getNavigation().stop();
        hero.setTarget(null);
        hero.setDeltaMovement(0.0D, 0.0D, 0.0D);
        hero.setFallDistance(0.0F);
        return true;
    }
    private static boolean discardNearbyEntities(ServerPlayer player, HeroEntity hero) {
        AABB bounds = new AABB(player.blockPosition()).inflate(DISCARD_ENTITY_RADIUS, 12.0D, DISCARD_ENTITY_RADIUS);
        player.sendSystemMessage(Component.literal("§5[Herobrine] Entity discard protocol initiated."));

        int discardedEntities = 0;
        for (Entity entity : player.serverLevel().getEntities(null, bounds)) {
            if (entity == null || entity.isRemoved() || entity == player || entity == hero
                    || entity instanceof ServerPlayer || entity instanceof HeroEntity) {
                continue;
            }
            entity.discard();
            discardedEntities++;
        }

        player.sendSystemMessage(Component.literal("§d[Herobrine] Nearby entity discard complete. Erased " + discardedEntities + " entities."));
        return discardedEntities > 0;
    }

    private static boolean discardChunksAroundPlayer(ServerPlayer player, HeroEntity hero) {
        hero.getNavigation().stop();
        hero.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.sendSystemMessage(Component.literal("§5[Herobrine] Discard protocol escalated: chunk voidification."));
        return ClearAreaPacket.startVoidDomain(player, DISCARD_CHUNK_RADIUS, false, false,
                Component.literal("§5[Herobrine] Reality is being discarded in great swathes."));
    }
}

