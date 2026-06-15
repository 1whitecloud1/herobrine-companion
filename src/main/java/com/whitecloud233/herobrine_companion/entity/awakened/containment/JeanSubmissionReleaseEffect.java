package com.whitecloud233.herobrine_companion.entity.awakened.containment;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyMemberType;
import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyMembers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class JeanSubmissionReleaseEffect {
    private static final double HERO_SEARCH_RANGE = 32.0D;

    private JeanSubmissionReleaseEffect() {
    }

    public static boolean applyIfJean(ServerLevel level, Mob mob, CapturedAwakenedMob captured, Player player, Vec3 releasePos) {
        if (!(mob instanceof EnderDragon dragon)) {
            return false;
        }

        boolean knownJean = captured.jean() || HerobrineFamilyMembers.isFamilyMember(dragon, HerobrineFamilyMemberType.JEAN);
        if (!knownJean) {
            return false;
        }

        if (!HerobrineFamilyMembers.isFamilyMember(dragon, HerobrineFamilyMemberType.JEAN)) {
            HerobrineFamilyMembers.assignSummonedIdentity(dragon, HerobrineFamilyMemberType.JEAN);
        }

        AwakenedMobTransportSanitizer.sanitizePersistentData(dragon.getPersistentData());
        dragon.ejectPassengers();
        dragon.stopRiding();
        dragon.addTag("herobrine_companion_jean");
        dragon.setNoAi(false);
        dragon.setDragonFight(null);
        dragon.setFightOrigin(BlockPos.containing(releasePos));
        dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
        dragon.getPersistentData().putBoolean("HeroSubmission", true);
        dragon.setSilent(true);
        dragon.setTarget(null);
        dragon.setDeltaMovement(Vec3.ZERO);
        dragon.fallDistance = 0.0F;

        Entity lookTarget = findLookTarget(level, dragon, player);
        float yaw = lookTarget == null ? dragon.getYRot() : yawToward(dragon, lookTarget);
        dragon.getPersistentData().putFloat("HeroSubmissionYaw", yaw);
        dragon.setYRot(yaw);
        dragon.setYBodyRot(yaw);
        dragon.setYHeadRot(yaw);
        dragon.setXRot(25.0F);
        JeanSubmissionAnchor.anchor(dragon, releasePos, yaw);
        dragon.hasImpulse = true;
        return true;
    }

    private static Entity findLookTarget(ServerLevel level, EnderDragon dragon, Player player) {
        HeroEntity hero = level.getEntitiesOfClass(HeroEntity.class, dragon.getBoundingBox().inflate(HERO_SEARCH_RANGE),
                        candidate -> candidate.isAlive()
                                && !candidate.isRemoved()
                                && (player == null || candidate.getOwnerUUID() == null || candidate.getOwnerUUID().equals(player.getUUID())))
                .stream()
                .min((left, right) -> Double.compare(left.distanceToSqr(dragon), right.distanceToSqr(dragon)))
                .orElse(null);
        return hero != null ? hero : player;
    }

    private static float yawToward(Entity self, Entity target) {
        double dx = target.getX() - self.getX();
        double dz = target.getZ() - self.getZ();
        return (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 180.0F;
    }
}
