package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.fight.HeroChallengeManager;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class HeroAIActionPacket {
    public static final String ACTION_CHALLENGE_ACCEPT = "action:challenge_accept";
    public static final String ACTION_HERO_FLY_UP = "action:hero_fly_up";
    public static final String ACTION_HERO_LAND = "action:hero_land";
    public static final String ACTION_DISCARD_ENTITIES = "action:discard_entities";
    public static final String ACTION_DISCARD = "action:discard";
    private static final int DEFAULT_CHALLENGE_MODE = 1;
    private static final double DISCARD_ENTITY_RADIUS = 32.0D;
    private static final int DISCARD_CHUNK_RADIUS = 10;

    private final String action;

    public HeroAIActionPacket(String action) {
        this.action = action == null ? "" : action;
    }

    public HeroAIActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.action, 64);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                performAction(player, this.action);
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

        hero.setBattleModeActiveFrom("HeroAIActionPacket.makeHeroFlyUp", false);
        hero.setFloating(true);
        hero.setNoGravity(true);
        hero.getNavigation().stop();

        double targetY = Math.min(hero.getY() + 6.0D, hero.level().getMaxBuildHeight() - 2.0D);
        hero.teleportTo(hero.getX(), targetY, hero.getZ());
        hero.setDeltaMovement(0.0D, 0.0D, 0.0D);
        hero.fallDistance = 0.0F;
        return true;
    }

    private static boolean makeHeroLand(HeroEntity hero) {
        if (hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
            return false;
        }

        hero.setFloating(false);
        hero.setNoGravity(false);
        hero.getNavigation().stop();
        hero.fallDistance = 0.0F;
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

