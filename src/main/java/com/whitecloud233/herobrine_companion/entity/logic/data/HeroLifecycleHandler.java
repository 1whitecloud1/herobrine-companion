package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.herobrine_companion.util.EndRingContext;
import com.whitecloud233.herobrine_companion.init.ModEntities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

public class HeroLifecycleHandler {
    public static void beforeRemove(HeroEntity hero, Entity.RemovalReason reason) {
        if (!hero.level().isClientSide && !hero.isRemoved() && hero.isAlive()
                && (hero.isLoadedFromDisk() || hero.getPersistenceState().isEquipmentRestored())
                && (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                    || reason == Entity.RemovalReason.CHANGED_DIMENSION)) {
            HeroStateManager.backupToGlobal(hero);
        }
    }

    public static void afterRemove(HeroEntity hero) {
        if (!hero.level().isClientSide) {
            hero.getHeroAgent().flushMemoryOnDetach(hero);
            hero.getHeroAgent().onDetached();
        }
        HeroBrain.ACTIVE_HEROES.remove(hero);
    }

    public static void onDeath(HeroEntity hero) {
        if (hero.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            UUID owner = hero.getOwnerUUID();
            if (owner != null && hero.getUUID().equals(data.getActiveHeroUUID(owner))) {
                data.setActiveHeroUUID(owner, null);
                data.setLastKnownHeroPos(owner, null);
            }
        }
    }

    public static void checkUniqueness(HeroEntity hero) {
        if (hero.level().isClientSide || hero.isRemoved()) return;

        MinecraftServer server = hero.getServer();
        if (server == null) return;

        UUID myOwner = hero.getOwnerUUID();
        boolean iAmSafe = hero.getTags().contains(EndRingContext.TAG_RESPAWNED_SAFE);
        HeroEntity canonical = hero.level() instanceof ServerLevel level ? findActiveHero(level, myOwner) : null;

        // 🚨 核心优化：直接遍历已缓存的 ACTIVE_HEROES，时间复杂度从 O(N) 降为 O(1)
        for (HeroEntity other : HeroBrain.ACTIVE_HEROES) {
            if (other == hero || !other.isAlive() || other.isRemoved() || other.getServer() != server) continue;

            UUID otherOwner = other.getOwnerUUID();

            boolean sameOwner = (myOwner != null && myOwner.equals(otherOwner));

            if (sameOwner) {
                // 已登记的本体优先，不能按 tickCount 把刚加载的空副本当成真身。
                if (canonical == hero) {
                    other.discard();
                    continue;
                } else if (canonical != null) {
                    hero.discard();
                    return;
                }
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
        for (HeroEntity existing : getLoadedHeroes(safeHero.getServer(), owner)) {
            if (existing != safeHero && existing.isAlive()) {
                if (owner != null && owner.equals(existing.getOwnerUUID())) {
                    syncDataBeforeDiscard(existing, safeHero);
                    existing.remove(Entity.RemovalReason.DISCARDED);
                }
            }
        }
    }

    public static boolean checkForDuplicates(HeroEntity newHero) {
        if (newHero.getServer() == null) return false;
        UUID owner = newHero.getOwnerUUID();
        if (owner == null) return false;

        // 🚨 核心优化：直接遍历高速缓存池
        UUID canonicalId = HeroWorldData.get((ServerLevel) newHero.level()).getActiveHeroUUID(owner);
        for (HeroEntity existing : getLoadedHeroes(newHero.getServer(), owner)) {
            if (existing != newHero && existing.isAlive() && !existing.isRemoved()) {
                if (owner != null && owner.equals(existing.getOwnerUUID())) {
                    if (newHero.getUUID().equals(canonicalId)) {
                        existing.discard();
                        continue;
                    }
                    syncDataBeforeDiscard(newHero, existing);
                    return true;
                }
            }
        }
        return false;
    }

    /** 加载/传送时实体可能尚未 tick，不能只查询 ACTIVE_HEROES。 */
    public static List<HeroEntity> getLoadedHeroes(MinecraftServer server, UUID owner) {
        List<HeroEntity> heroes = new ArrayList<>();
        if (owner == null) return heroes;
        for (ServerLevel level : server.getAllLevels()) {
            heroes.addAll(level.getEntities(ModEntities.HERO.get(),
                    hero -> hero.isAlive() && !hero.isRemoved() && owner.equals(hero.getOwnerUUID())));
        }
        return heroes;
    }

    public static HeroEntity findOwnedHero(ServerLevel preferredLevel, UUID owner) {
        HeroEntity canonical = findActiveHero(preferredLevel, owner);
        if (canonical != null) return canonical;
        return getLoadedHeroes(preferredLevel.getServer(), owner).stream()
                .sorted(Comparator.comparing((HeroEntity hero) -> hero.level() != preferredLevel)
                        .thenComparing(HeroEntity::getUUID))
                .findFirst().orElse(null);
    }

    public static HeroEntity findActiveHero(ServerLevel level, UUID owner) {
        if (owner == null) return null;
        UUID activeId = HeroWorldData.get(level).getActiveHeroUUID(owner);
        if (activeId == null) return null;
        for (ServerLevel candidateLevel : level.getServer().getAllLevels()) {
            Entity entity = candidateLevel.getEntity(activeId);
            if (entity instanceof HeroEntity hero && hero.isAlive() && !hero.isRemoved()
                    && owner.equals(hero.getOwnerUUID())) {
                return hero;
            }
        }
        return null;
    }
}
