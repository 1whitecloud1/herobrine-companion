package com.whitecloud233.herobrine_companion.entity.awakened.containment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

public final class AwakenedMobTransportSanitizer {
    private static final String FORGE_DATA_TAG = "NeoForgeData";
    private static final String LEGACY_FORGE_DATA_TAG = "ForgeData";
    private static final String HERO_SUBMISSION_TAG = "HeroSubmission";
    private static final String HERO_SUBMISSION_YAW_TAG = "HeroSubmissionYaw";
    private static final String HERO_SUBMISSION_STATIONARY_TAG = "HeroSubmissionStationary";
    private static final String HERO_SUBMISSION_ANCHOR_X_TAG = "HeroSubmissionAnchorX";
    private static final String HERO_SUBMISSION_ANCHOR_Y_TAG = "HeroSubmissionAnchorY";
    private static final String HERO_SUBMISSION_ANCHOR_Z_TAG = "HeroSubmissionAnchorZ";
    private static final String JEAN_FRONT_HERO_TAG = "HerobrineCompanionJeanFrontHero";
    private static final String JEAN_FRONT_HERO_TIME_TAG = "HerobrineCompanionJeanFrontHeroTime";
    private static final String JEAN_PLAYER_RIDDEN_TAG = "HerobrineCompanionJeanPlayerRidden";
    private static final String JEAN_MOUNT_NO_AI_TAG = "HerobrineCompanionJeanMountNoAi";

    private AwakenedMobTransportSanitizer() {
    }

    public static void sanitizeCapturedData(CompoundTag entityData) {
        entityData.remove("UUID");
        entityData.remove("UUIDMost");
        entityData.remove("UUIDLeast");
        entityData.remove("Pos");
        entityData.remove("Motion");
        entityData.remove("Rotation");
        entityData.remove("Passengers");
        entityData.remove("Leash");
        entityData.remove("PortalCooldown");
        entityData.remove("FallDistance");

        if (entityData.contains(FORGE_DATA_TAG, Tag.TAG_COMPOUND)) {
            sanitizePersistentData(entityData.getCompound(FORGE_DATA_TAG));
        }
        if (entityData.contains(LEGACY_FORGE_DATA_TAG, Tag.TAG_COMPOUND)) {
            sanitizePersistentData(entityData.getCompound(LEGACY_FORGE_DATA_TAG));
        }
    }

    public static void sanitizeReleasedMob(Mob mob) {
        mob.stopRiding();
        for (Entity passenger : mob.getPassengers()) {
            passenger.stopRiding();
        }
        mob.ejectPassengers();

        mob.setTarget(null);
        PathNavigation navigation = mob.getNavigation();
        if (navigation != null) {
            navigation.stop();
        }
        mob.setDeltaMovement(Vec3.ZERO);
        mob.fallDistance = 0.0F;

        sanitizePersistentData(mob.getPersistentData());
        if (mob instanceof NeutralMob neutralMob) {
            neutralMob.stopBeingAngry();
        }
        if (mob.isSilent()) {
            mob.setSilent(false);
        }
    }

    public static void sanitizePersistentData(CompoundTag persistentData) {
        persistentData.remove(HERO_SUBMISSION_TAG);
        persistentData.remove(HERO_SUBMISSION_YAW_TAG);
        persistentData.remove(HERO_SUBMISSION_STATIONARY_TAG);
        persistentData.remove(HERO_SUBMISSION_ANCHOR_X_TAG);
        persistentData.remove(HERO_SUBMISSION_ANCHOR_Y_TAG);
        persistentData.remove(HERO_SUBMISSION_ANCHOR_Z_TAG);
        persistentData.remove(JEAN_FRONT_HERO_TAG);
        persistentData.remove(JEAN_FRONT_HERO_TIME_TAG);
        persistentData.remove(JEAN_PLAYER_RIDDEN_TAG);
        persistentData.remove(JEAN_MOUNT_NO_AI_TAG);
    }
}
