package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.modid.herobrine_companion.util.EndRingContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public class HeroLifecycleHandler {
    public static void checkUniqueness(HeroEntity hero) {
        if (hero.level().isClientSide) return;

        MinecraftServer server = hero.getServer();
        if (server == null) return;

        UUID myOwner = hero.getOwnerUUID();
        boolean iAmSafe = hero.getTags().contains(EndRingContext.TAG_RESPAWNED_SAFE);

        // 🚨 核心优化：直接遍历已缓存的 ACTIVE_HEROES，时间复杂度从 O(N) 降为 O(1)
        for (HeroEntity other : HeroBrain.ACTIVE_HEROES) {
            if (other == hero || !other.isAlive() || other.isRemoved()) continue;

            UUID otherOwner = other.getOwnerUUID();

            boolean sameOwner = (myOwner != null && myOwner.equals(otherOwner));

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

        if (iAmSafe) {
            hero.removeTag(EndRingContext.TAG_RESPAWNED_SAFE);
        }
    }

    private static void syncDataBeforeDiscard(HeroEntity source, HeroEntity target) {
        HeroStateManager.syncEntityToEntity(source, target);
    }

    public static void discardAllOtherHeroes(HeroEntity safeHero) {
        if (safeHero.getServer() == null) return;
        UUID owner = safeHero.getOwnerUUID();

        // 🚨 核心优化：直接遍历高速缓存池
        for (HeroEntity existing : HeroBrain.ACTIVE_HEROES) {
            if (existing != safeHero && existing.isAlive()) {
                if (owner != null && owner.equals(existing.getOwnerUUID())) {
                    existing.remove(Entity.RemovalReason.DISCARDED);
                }
            }
        }
    }

    public static boolean checkForDuplicates(HeroEntity newHero) {
        if (newHero.getServer() == null) return false;
        UUID owner = newHero.getOwnerUUID();

        // 🚨 核心优化：直接遍历高速缓存池
        for (HeroEntity existing : HeroBrain.ACTIVE_HEROES) {
            if (existing != newHero && existing.isAlive() && !existing.isRemoved()) {
                if (owner != null && owner.equals(existing.getOwnerUUID())) {
                    return true;
                }
            }
        }
        return false;
    }
}