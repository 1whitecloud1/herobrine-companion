package com.whitecloud233.herobrine_companion.entity.awakened;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AwakenedMobPeerScuffleService {
    private static final int MIN_DURATION_TICKS = 80;
    private static final int MAX_DURATION_TICKS = 140;
    private static final double MAX_ACTIVE_DISTANCE_SQR = 144.0D;
    private static final float MIN_SURVIVING_HEALTH = 1.0F;
    private static final Set<String> SAFE_ROOTS = Set.of(
            "zombie",
            "skeleton",
            "spider",
            "raider",
            "slime",
            "piglin",
            "beast"
    );

    private static final Map<PairKey, Scuffle> ACTIVE_SCUFFLES = new HashMap<>();

    private AwakenedMobPeerScuffleService() {
    }

    public static void maybeStart(Mob first, Mob second, AwakenedMobProfile firstProfile,
                                  AwakenedMobProfile secondProfile, AwakenedMobPeerScene scene, long now) {
        float chance = AwakenedMobPeerRelationRules.scuffleChance(firstProfile, secondProfile, 0.18F);
        if (scene != AwakenedMobPeerScene.CONFLICT || first.getRandom().nextFloat() >= chance) {
            return;
        }
        if (!canStart(first) || !canStart(second)) {
            return;
        }

        PairKey pairKey = PairKey.of(first.getUUID(), second.getUUID());
        if (ACTIVE_SCUFFLES.containsKey(pairKey)) {
            return;
        }

        long endsAt = now + MIN_DURATION_TICKS + first.getRandom().nextInt(MAX_DURATION_TICKS - MIN_DURATION_TICKS + 1);
        ACTIVE_SCUFFLES.put(pairKey, new Scuffle(endsAt));
        first.setTarget(second);
        second.setTarget(first);
    }

    public static boolean handleMobAttack(Mob attacker, LivingEntity target, float amount, long now) {
        if (!(target instanceof Mob targetMob)) {
            return false;
        }
        PairKey pairKey = PairKey.of(attacker.getUUID(), targetMob.getUUID());
        Scuffle scuffle = ACTIVE_SCUFFLES.get(pairKey);
        if (scuffle == null) {
            return false;
        }
        if (isExpiredOrInvalid(attacker, targetMob, scuffle, now)) {
            endScuffle(attacker, targetMob, pairKey);
            return false;
        }
        if (targetMob.getHealth() - amount <= MIN_SURVIVING_HEALTH) {
            endScuffle(attacker, targetMob, pairKey);
            return true;
        }
        return false;
    }

    public static void tick(Mob mob) {
        if (!(mob.getTarget() instanceof Mob targetMob)) {
            return;
        }
        PairKey pairKey = PairKey.of(mob.getUUID(), targetMob.getUUID());
        Scuffle scuffle = ACTIVE_SCUFFLES.get(pairKey);
        if (scuffle == null) {
            return;
        }
        long now = mob.level().getGameTime();
        if (isExpiredOrInvalid(mob, targetMob, scuffle, now)) {
            endScuffle(mob, targetMob, pairKey);
        }
    }

    private static boolean canStart(Mob mob) {
        if (!(mob instanceof AwakenedMobAccessor accessor) || !accessor.herobrineCompanion$isAwakenedMob()) {
            return false;
        }
        if (mob.getTarget() instanceof Player) {
            return false;
        }
        AwakenedMobProfile profile = AwakenedMobProfiles.get(mob);
        if (profile == null || !SAFE_ROOTS.contains(profile.root())) {
            return false;
        }
        EntityType<?> type = mob.getType();
        return type != EntityType.CREEPER
                && type != EntityType.WITHER
                && type != EntityType.ENDER_DRAGON
                && type != EntityType.WARDEN;
    }

    private static boolean isExpiredOrInvalid(Mob first, Mob second, Scuffle scuffle, long now) {
        return now >= scuffle.endsAt()
                || !first.isAlive()
                || !second.isAlive()
                || first.isRemoved()
                || second.isRemoved()
                || first.distanceToSqr(second) > MAX_ACTIVE_DISTANCE_SQR;
    }

    private static void endScuffle(Mob first, Mob second, PairKey pairKey) {
        ACTIVE_SCUFFLES.remove(pairKey);
        if (first.getTarget() == second) {
            first.setTarget(null);
        }
        if (second.getTarget() == first) {
            second.setTarget(null);
        }
    }

    private record Scuffle(long endsAt) {
    }

    private record PairKey(UUID first, UUID second) {
        private static PairKey of(UUID first, UUID second) {
            return first.compareTo(second) <= 0 ? new PairKey(first, second) : new PairKey(second, first);
        }
    }
}
