package com.whitecloud233.modid.herobrine_companion.entity.family;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public final class HerobrineFamilySummonValidator {
    private HerobrineFamilySummonValidator() {
    }

    public static Component validateStart(HeroEntity hero, ServerLevel level, HerobrineFamilySummonStructure structure) {
        Component readinessFailure = validateHeroReadiness(hero);
        return readinessFailure != null ? readinessFailure : validateStructureUse(hero, level, structure);
    }

    public static Component validateContinuation(HeroEntity hero) {
        return validateHeroReadiness(hero);
    }

    public static Component validateStructureUse(HeroEntity hero, ServerLevel level, HerobrineFamilySummonStructure structure) {
        int trust = resolveCurrentTrust(hero, level);
        if (trust < structure.memberType().requiredTrust()) {
            return HerobrineFamilySummonFeedback.lowTrust(structure.memberType(), trust);
        }

        HerobrineFamilyWorldData familyData = HerobrineFamilyWorldData.get(level);
        long now = level.getGameTime();
        if (!familyData.isCooldownReady(structure.memberType(), now)) {
            long remaining = familyData.getRemainingCooldown(structure.memberType(), now) / 20L;
            return HerobrineFamilySummonFeedback.cooldown(structure.memberType(), remaining);
        }

        if (findExistingFamilyMember(level, structure.memberType()) != null) {
            return HerobrineFamilySummonFeedback.alreadyExists(structure.memberType());
        }

        if (!hasSpawnSpace(level, structure)) {
            return HerobrineFamilySummonFeedback.noSpace();
        }

        return null;
    }

    public static int resolveCurrentTrust(HeroEntity hero, ServerLevel level) {
        if (hero == null) {
            return 0;
        }
        if (hero.getOwnerUUID() == null) {
            return hero.getTrustLevel();
        }

        int entityTrust = hero.getTrustLevel();
        int savedTrust = HeroWorldData.get(level).getTrust(hero.getOwnerUUID());
        return Math.max(entityTrust, savedTrust);
    }

    private static Component validateHeroReadiness(HeroEntity hero) {
        if (hero == null || !hero.isAlive() || hero.isRemoved()) {
            return HerobrineFamilySummonFeedback.busy();
        }
        if (hero.isChallengeActiveState() || hero.isBattleModeActive()) {
            return HerobrineFamilySummonFeedback.busy();
        }
        if (hero.getTarget() != null && hero.getTarget().isAlive()) {
            return HerobrineFamilySummonFeedback.busy();
        }
        if (hero.getLastHurtByMob() != null && hero.getLastHurtByMob().isAlive()
                && hero.distanceToSqr(hero.getLastHurtByMob()) < 144.0D) {
            return HerobrineFamilySummonFeedback.busy();
        }
        return null;
    }

    private static Mob findExistingFamilyMember(ServerLevel level, HerobrineFamilyMemberType type) {
        for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
            for (Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof Mob mob && mob.isAlive() && HerobrineFamilyMembers.isFamilyMember(mob, type)) {
                    return mob;
                }
            }
        }
        return null;
    }

    private static boolean hasSpawnSpace(ServerLevel level, HerobrineFamilySummonStructure structure) {
        Mob probe = structure.memberType().entityType().create(level);
        if (probe == null) {
            return false;
        }

        probe.moveTo(structure.spawnPos().x, structure.spawnPos().y, structure.spawnPos().z, 0.0F, 0.0F);
        return level.noCollision(probe, probe.getBoundingBox());
    }
}
