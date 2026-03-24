package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.util.EndRingContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public class HeroLifecycleHandler {

    public static void checkUniqueness(HeroEntity hero) {
        if (hero.level().isClientSide) return;

        ServerLevel currentLevel = (ServerLevel) hero.level();
        MinecraftServer server = currentLevel.getServer();
        if (server == null) return;

        UUID myOwner = hero.getOwnerUUID();
        boolean iAmSafe = hero.getTags().contains(EndRingContext.TAG_RESPAWNED_SAFE);

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.getId() == hero.getId()) continue;

                if (entity instanceof HeroEntity other && other.isAlive()) {
                    UUID otherOwner = other.getOwnerUUID();
                    boolean sameOwner = (myOwner == null && otherOwner == null) || (myOwner != null && myOwner.equals(otherOwner));

                    if (sameOwner) {
                        boolean otherIsSafe = other.getTags().contains(EndRingContext.TAG_RESPAWNED_SAFE);

                        if (iAmSafe && !otherIsSafe) {
                            syncDataBeforeDiscard(other, hero);
                            other.discard();
                            continue;
                        } else if (!iAmSafe && otherIsSafe) {
                            syncDataBeforeDiscard(hero, other);
                            hero.discard();
                            return;
                        }

                        if (myOwner != null) {
                            ServerPlayer owner = server.getPlayerList().getPlayer(myOwner);
                            if (owner != null) {
                                boolean iAmWithPlayer = hero.level() == owner.level();
                                boolean otherIsWithPlayer = other.level() == owner.level();

                                if (iAmWithPlayer && !otherIsWithPlayer) {
                                    syncDataBeforeDiscard(other, hero);
                                    other.discard();
                                    continue;
                                } else if (!iAmWithPlayer && otherIsWithPlayer) {
                                    syncDataBeforeDiscard(hero, other);
                                    hero.discard();
                                    return;
                                }
                            }
                        }

                        if (hero.tickCount < other.tickCount) {
                            syncDataBeforeDiscard(other, hero);
                            other.discard();
                        } else {
                            syncDataBeforeDiscard(hero, other);
                            hero.discard();
                            return;
                        }
                    }
                }
            }
        }

        if (iAmSafe) {
            hero.removeTag(EndRingContext.TAG_RESPAWNED_SAFE);
        }
    }

    // ============== [重构精简] 委托给状态管理器进行实体间数据转移 ==============
    private static void syncDataBeforeDiscard(HeroEntity source, HeroEntity target) {
        HeroStateManager.syncEntityToEntity(source, target);
    }
    // =====================================================================
}