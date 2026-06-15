package com.whitecloud233.herobrine_companion.entity.family;

import com.whitecloud233.herobrine_companion.entity.awakened.containment.JeanSubmissionAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JeanCombatResponseService {
    private static final String PLAYER_DAMAGE_TARGET_TAG = "HerobrineCompanionJeanPlayerDamageTarget";
    private static final String PLAYER_DAMAGE_UNTIL_TAG = "HerobrineCompanionJeanPlayerDamageUntil";
    private static final String PLAYER_DAMAGE_LEVEL_TAG = "HerobrineCompanionJeanPlayerDamageLevel";
    private static final String PLAYER_DAMAGE_DEATHS_TAG = "HerobrineCompanionJeanPlayerDamageDeaths";
    private static final String NEXT_BOOSTED_ATTACK_TAG = "HerobrineCompanionJeanNextBoostedAttack";
    private static final String BREATH_UNTIL_TAG = "HerobrineCompanionJeanBreathUntil";
    private static final String NEXT_BREATH_DAMAGE_TAG = "HerobrineCompanionJeanNextBreathDamage";
    private static final String NEXT_PRESSURE_REFRESH_TAG = "HerobrineCompanionJeanNextPressureRefresh";
    private static final int REQUIRED_TARGET_DEATHS = 3;
    private static final int MAX_DAMAGE_LEVEL = 5;
    private static final int BASE_ATTACK_INTERVAL_TICKS = 70;
    private static final int ATTACK_INTERVAL_REDUCTION_TICKS = 9;
    private static final int MIN_ATTACK_INTERVAL_TICKS = 24;
    private static final int BREATH_DURATION_TICKS = 84;
    private static final int BREATH_DAMAGE_INTERVAL_TICKS = 12;
    private static final float BREATH_DAMAGE = 5.0F;
    private static final double BREATH_DAMAGE_RANGE_SQR = 56.0D * 56.0D;
    private static final double CHARGE_REACQUIRE_DISTANCE_SQR = 48.0D * 48.0D;
    private static final double DEATH_EVENT_FALLBACK_RANGE = 256.0D;
    private static final double PURSUIT_MIN_RADIUS = 13.0D;
    private static final double PURSUIT_ORBIT_RADIUS = 22.0D;
    private static final double PURSUIT_MAX_RADIUS = 34.0D;
    private static final double PURSUIT_HEIGHT = 9.0D;
    private static final double PURSUIT_HIGH_LIMIT = 16.0D;
    private static final Map<UUID, Set<UUID>> ACTIVE_JEANS_BY_TARGET = new HashMap<>();

    private JeanCombatResponseService() {
    }

    public static boolean markPlayerDamage(Mob mob, Player player) {
        if (!(mob instanceof EnderDragon dragon)
                || player == null
                || player.isCreative()
                || player.isSpectator()
                || !isJean(dragon)) {
            return false;
        }

        long now = dragon.level().getGameTime();
        CompoundTag data = dragon.getPersistentData();
        UUID previousTarget = data.hasUUID(PLAYER_DAMAGE_TARGET_TAG) ? data.getUUID(PLAYER_DAMAGE_TARGET_TAG) : null;
        boolean sameTarget = player.getUUID().equals(previousTarget);
        int level = sameTarget
                ? Math.min(MAX_DAMAGE_LEVEL, data.getInt(PLAYER_DAMAGE_LEVEL_TAG) + 1)
                : 1;

        if (previousTarget != null && !sameTarget) {
            unregister(dragon.getUUID(), previousTarget);
        }
        data.putUUID(PLAYER_DAMAGE_TARGET_TAG, player.getUUID());
        data.putLong(PLAYER_DAMAGE_UNTIL_TAG, Long.MAX_VALUE);
        data.putInt(PLAYER_DAMAGE_LEVEL_TAG, level);
        if (!sameTarget) {
            data.putInt(PLAYER_DAMAGE_DEATHS_TAG, 0);
        }
        data.putLong(NEXT_BOOSTED_ATTACK_TAG, now + 6L);
        register(dragon.getUUID(), player.getUUID());

        prepareForPlayerFight(dragon, player);
        return true;
    }

    public static void tick(Mob mob) {
        if (!(mob instanceof EnderDragon dragon)
                || dragon.level().isClientSide
                || !isJean(dragon)) {
            return;
        }

        Player target = activeAttackTarget(dragon);
        if (target == null) {
            return;
        }

        prepareForPlayerFight(dragon, target);
        if (hasPlayerPassenger(dragon)) {
            return;
        }

        long now = dragon.level().getGameTime();
        CompoundTag data = dragon.getPersistentData();
        maintainPressure(dragon, target, data, now);
        if (now <= data.getLong(BREATH_UNTIL_TAG)) {
            tickBreathDamage(dragon, target, data, now);
            return;
        }

        if (now < data.getLong(NEXT_BOOSTED_ATTACK_TAG)) {
            return;
        }

        triggerBoostedAttack(dragon, target, data.getInt(PLAYER_DAMAGE_LEVEL_TAG));
        data.putLong(NEXT_BOOSTED_ATTACK_TAG, now + nextAttackInterval(dragon, data.getInt(PLAYER_DAMAGE_LEVEL_TAG)));
    }

    public static boolean shouldUseOriginalUnriddenFlightSpeed(EnderDragon dragon) {
        return hasActivePlayerDamageResponse(dragon);
    }

    public static boolean isActiveTarget(Mob mob, Player player) {
        return mob instanceof EnderDragon dragon && player != null && isTargetUuidActive(dragon, player.getUUID());
    }

    public static boolean hasActivePlayerDamageResponse(EnderDragon dragon) {
        if (!isTargetUuidActive(dragon, null)) {
            return false;
        }
        register(dragon.getUUID(), dragon.getPersistentData().getUUID(PLAYER_DAMAGE_TARGET_TAG));
        return true;
    }

    public static boolean enforcePursuitFlight(EnderDragon dragon) {
        if (dragon == null || dragon.level().isClientSide || hasPlayerPassenger(dragon)) {
            return false;
        }

        Player target = activeAttackTarget(dragon);
        if (target == null) {
            return false;
        }

        Vec3 motion = pursuitMotion(dragon, target);
        dragon.setDeltaMovement(motion);
        dragon.setPos(dragon.getX() + motion.x, dragon.getY() + motion.y, dragon.getZ() + motion.z);
        dragon.setFightOrigin(BlockPos.containing(target.position()));
        dragon.setTarget(target);
        turnAlongMotion(dragon, motion, target);
        dragon.fallDistance = 0.0F;
        dragon.hasImpulse = true;
        return true;
    }

    public static void recordPlayerDeath(Player player) {
        if (player == null || player.level().getServer() == null) {
            return;
        }

        UUID targetId = player.getUUID();
        Set<UUID> processed = new HashSet<>();
        Set<UUID> jeanIds = ACTIVE_JEANS_BY_TARGET.get(targetId);
        if (jeanIds != null) {
            for (UUID jeanId : Set.copyOf(jeanIds)) {
                EnderDragon dragon = findJean(player, jeanId);
                if (dragon != null && processed.add(dragon.getUUID())) {
                    recordDeathForDragon(dragon, targetId);
                }
            }
        }

        AABB fallbackArea = player.getBoundingBox().inflate(DEATH_EVENT_FALLBACK_RANGE);
        for (EnderDragon dragon : player.level().getEntitiesOfClass(EnderDragon.class, fallbackArea,
                candidate -> isJean(candidate) && isTargetUuidActive(candidate, targetId))) {
            if (processed.add(dragon.getUUID())) {
                recordDeathForDragon(dragon, targetId);
            }
        }
    }

    private static Player activeAttackTarget(EnderDragon dragon) {
        CompoundTag data = dragon.getPersistentData();
        if (!isTargetUuidActive(dragon, null)) {
            return null;
        }

        register(dragon.getUUID(), data.getUUID(PLAYER_DAMAGE_TARGET_TAG));
        if (dragon.level().getServer() == null) {
            return null;
        }

        ServerPlayer player = dragon.level().getServer().getPlayerList().getPlayer(data.getUUID(PLAYER_DAMAGE_TARGET_TAG));
        if (player == null
                || !player.isAlive()
                || player.level() != dragon.level()
                || player.isCreative()
                || player.isSpectator()) {
            return null;
        }
        return player;
    }

    private static void prepareForPlayerFight(EnderDragon dragon, Player player) {
        JeanSubmissionAnchor.releaseForCombat(dragon);
        dragon.setNoAi(false);
        dragon.setSilent(false);
        dragon.setDragonFight(null);
        dragon.setFightOrigin(BlockPos.containing(player.position()));
        dragon.setTarget(player);
        dragon.fallDistance = 0.0F;
        dragon.hasImpulse = true;
    }

    private static void triggerBoostedAttack(EnderDragon dragon, Player target, int damageLevel) {
        double distanceSqr = dragon.distanceToSqr(target);
        boolean charge = distanceSqr > BREATH_DAMAGE_RANGE_SQR
                || dragon.getRandom().nextFloat() < Math.max(0.18F, 0.38F - damageLevel * 0.04F);

        if (charge) {
            Vec3 chargeTarget = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
            dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
            dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(chargeTarget);
            return;
        }

        startBreathAttack(dragon, target);
    }

    private static void startBreathAttack(EnderDragon dragon, Player target) {
        long now = dragon.level().getGameTime();
        CompoundTag data = dragon.getPersistentData();
        data.putLong(BREATH_UNTIL_TAG, now + BREATH_DURATION_TICKS);
        data.putLong(NEXT_BREATH_DAMAGE_TAG, now + 4L);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.STRAFE_PLAYER);
        dragon.getPhaseManager().getPhase(EnderDragonPhase.STRAFE_PLAYER).setTarget(target);
    }

    private static void maintainPressure(EnderDragon dragon, Player target, CompoundTag data, long now) {
        if (now < data.getLong(NEXT_PRESSURE_REFRESH_TAG)) {
            return;
        }

        data.putLong(NEXT_PRESSURE_REFRESH_TAG, now + 20L);
        dragon.setFightOrigin(BlockPos.containing(target.position()));

        if (dragon.distanceToSqr(target) > CHARGE_REACQUIRE_DISTANCE_SQR) {
            Vec3 chargeTarget = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
            dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
            dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(chargeTarget);
        }
    }

    private static void tickBreathDamage(EnderDragon dragon, Player target, CompoundTag data, long now) {
        if (now < data.getLong(NEXT_BREATH_DAMAGE_TAG)) {
            return;
        }

        data.putLong(NEXT_BREATH_DAMAGE_TAG, now + BREATH_DAMAGE_INTERVAL_TICKS);
        if (dragon.distanceToSqr(target) > BREATH_DAMAGE_RANGE_SQR) {
            return;
        }

        spawnBreathLine(dragon, target);
        target.hurt(dragon.damageSources().mobAttack(dragon), BREATH_DAMAGE);
    }

    private static void spawnBreathLine(EnderDragon dragon, Player target) {
        if (!(dragon.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 from = dragon.position().add(0.0D, dragon.getBbHeight() * 0.45D, 0.0D);
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
        Vec3 step = to.subtract(from).scale(1.0D / 14.0D);
        for (int i = 1; i <= 14; i++) {
            Vec3 pos = from.add(step.scale(i));
            level.sendParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y, pos.z, 2, 0.18D, 0.18D, 0.18D, 0.02D);
        }
    }

    private static long nextAttackInterval(EnderDragon dragon, int damageLevel) {
        int interval = Math.max(
                MIN_ATTACK_INTERVAL_TICKS,
                BASE_ATTACK_INTERVAL_TICKS - Math.max(0, damageLevel - 1) * ATTACK_INTERVAL_REDUCTION_TICKS
        );
        return interval + dragon.getRandom().nextInt(9);
    }

    private static Vec3 pursuitMotion(EnderDragon dragon, Player target) {
        Vec3 dragonPos = dragon.position();
        Vec3 targetPos = target.position();
        Vec3 fromTarget = new Vec3(dragonPos.x - targetPos.x, 0.0D, dragonPos.z - targetPos.z);
        double horizontalDistance = fromTarget.length();
        Vec3 radial = horizontalDistance > 1.0E-4D
                ? fromTarget.scale(1.0D / horizontalDistance)
                : Vec3.directionFromRotation(0.0F, dragon.getYRot()).normalize();
        Vec3 towardTarget = radial.scale(-1.0D);
        Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);

        Vec3 horizontalMotion;
        if (horizontalDistance > PURSUIT_MAX_RADIUS) {
            double speed = horizontalDistance > 80.0D ? 1.55D : 1.25D;
            horizontalMotion = towardTarget.scale(speed).add(tangent.scale(0.18D));
        } else if (horizontalDistance < PURSUIT_MIN_RADIUS) {
            horizontalMotion = radial.scale(0.85D).add(tangent.scale(0.45D));
            if (horizontalMotion.lengthSqr() > 1.0E-4D) {
                horizontalMotion = horizontalMotion.normalize().scale(0.95D);
            }
        } else {
            double radiusCorrection = (horizontalDistance - PURSUIT_ORBIT_RADIUS) * 0.035D;
            horizontalMotion = tangent.scale(0.88D).add(radial.scale(radiusCorrection));
            if (horizontalMotion.length() > 1.05D) {
                horizontalMotion = horizontalMotion.normalize().scale(1.05D);
            }
        }

        double desiredY = target.getY() + PURSUIT_HEIGHT;
        double dy = clamp((desiredY - dragon.getY()) * 0.09D, -0.72D, 0.5D);
        if (dragon.getY() > target.getY() + PURSUIT_HIGH_LIMIT) {
            dy = Math.min(dy, -0.48D);
        } else if (dragon.getY() < target.getY() + 4.0D) {
            dy = Math.max(dy, 0.25D);
        }

        return new Vec3(horizontalMotion.x, dy, horizontalMotion.z);
    }

    private static void turnAlongMotion(EnderDragon dragon, Vec3 motion, Player target) {
        double dx = motion.x;
        double dz = motion.z;
        if (dx * dx + dz * dz < 1.0E-4D) {
            dx = target.getX() - dragon.getX();
            dz = target.getZ() - dragon.getZ();
        }

        float movementYaw = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float dragonYaw = movementYaw + 180.0F;
        dragon.setYRot(dragonYaw);
        dragon.setYBodyRot(dragonYaw);
        dragon.setYHeadRot(dragonYaw);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean hasPlayerPassenger(EnderDragon dragon) {
        return dragon.getPassengers().stream().anyMatch(Player.class::isInstance);
    }

    private static boolean isJean(EnderDragon dragon) {
        return HerobrineFamilyMembers.isFamilyMember(dragon, HerobrineFamilyMemberType.JEAN)
                || dragon.getTags().contains("herobrine_companion_jean")
                || dragon.hasCustomName() && "jean".equalsIgnoreCase(dragon.getCustomName().getString());
    }

    private static boolean isTargetUuidActive(EnderDragon dragon, UUID expectedTarget) {
        CompoundTag data = dragon.getPersistentData();
        if (!data.hasUUID(PLAYER_DAMAGE_TARGET_TAG)) {
            return false;
        }
        if (data.getInt(PLAYER_DAMAGE_DEATHS_TAG) >= REQUIRED_TARGET_DEATHS) {
            clear(dragon);
            return false;
        }
        return expectedTarget == null || expectedTarget.equals(data.getUUID(PLAYER_DAMAGE_TARGET_TAG));
    }

    private static void recordDeathForDragon(EnderDragon dragon, UUID targetId) {
        CompoundTag data = dragon.getPersistentData();
        if (!isTargetUuidActive(dragon, targetId)) {
            return;
        }

        int deaths = data.getInt(PLAYER_DAMAGE_DEATHS_TAG) + 1;
        data.putInt(PLAYER_DAMAGE_DEATHS_TAG, deaths);
        data.remove(BREATH_UNTIL_TAG);
        data.remove(NEXT_BREATH_DAMAGE_TAG);
        data.putLong(NEXT_BOOSTED_ATTACK_TAG, dragon.level().getGameTime() + 30L);

        if (deaths >= REQUIRED_TARGET_DEATHS) {
            clear(dragon);
            dragon.setTarget(null);
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        }
    }

    private static EnderDragon findJean(Player player, UUID jeanId) {
        if (player.level().getServer() == null) {
            return null;
        }

        for (ServerLevel level : player.level().getServer().getAllLevels()) {
            if (level.getEntity(jeanId) instanceof EnderDragon dragon && isJean(dragon)) {
                return dragon;
            }
        }
        return null;
    }

    private static void register(UUID jeanId, UUID targetId) {
        ACTIVE_JEANS_BY_TARGET.computeIfAbsent(targetId, ignored -> new HashSet<>()).add(jeanId);
    }

    private static void unregister(UUID jeanId, UUID targetId) {
        Set<UUID> jeanIds = ACTIVE_JEANS_BY_TARGET.get(targetId);
        if (jeanIds == null) {
            return;
        }
        jeanIds.remove(jeanId);
        if (jeanIds.isEmpty()) {
            ACTIVE_JEANS_BY_TARGET.remove(targetId);
        }
    }

    private static void clear(EnderDragon dragon) {
        CompoundTag data = dragon.getPersistentData();
        if (data.hasUUID(PLAYER_DAMAGE_TARGET_TAG)) {
            unregister(dragon.getUUID(), data.getUUID(PLAYER_DAMAGE_TARGET_TAG));
        }
        data.remove(PLAYER_DAMAGE_TARGET_TAG);
        data.remove(PLAYER_DAMAGE_UNTIL_TAG);
        data.remove(PLAYER_DAMAGE_LEVEL_TAG);
        data.remove(PLAYER_DAMAGE_DEATHS_TAG);
        data.remove(NEXT_BOOSTED_ATTACK_TAG);
        data.remove(BREATH_UNTIL_TAG);
        data.remove(NEXT_BREATH_DAMAGE_TAG);
        data.remove(NEXT_PRESSURE_REFRESH_TAG);
    }
}
