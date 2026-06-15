package com.whitecloud233.modid.herobrine_companion.entity.awakened.containment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.Vec3;

public final class JeanSubmissionAnchor {
    private static final String HERO_SUBMISSION_TAG = "HeroSubmission";
    private static final String STATIONARY_TAG = "HeroSubmissionStationary";
    private static final String ANCHOR_X_TAG = "HeroSubmissionAnchorX";
    private static final String ANCHOR_Y_TAG = "HeroSubmissionAnchorY";
    private static final String ANCHOR_Z_TAG = "HeroSubmissionAnchorZ";
    private static final String ANCHOR_YAW_TAG = "HeroSubmissionYaw";

    private JeanSubmissionAnchor() {
    }

    public static void anchor(EnderDragon dragon, Vec3 position, float yaw) {
        CompoundTag data = dragon.getPersistentData();
        data.putBoolean(STATIONARY_TAG, true);
        data.putDouble(ANCHOR_X_TAG, position.x);
        data.putDouble(ANCHOR_Y_TAG, position.y);
        data.putDouble(ANCHOR_Z_TAG, position.z);
        data.putFloat(ANCHOR_YAW_TAG, yaw);
        enforce(dragon);
    }

    public static boolean enforce(EnderDragon dragon) {
        CompoundTag data = dragon.getPersistentData();
        if (!data.getBoolean(STATIONARY_TAG)) {
            return false;
        }

        if (!data.getBoolean(HERO_SUBMISSION_TAG)) {
            clear(data);
            return false;
        }

        Vec3 anchor = new Vec3(data.getDouble(ANCHOR_X_TAG), data.getDouble(ANCHOR_Y_TAG), data.getDouble(ANCHOR_Z_TAG));
        float yaw = data.getFloat(ANCHOR_YAW_TAG);

        if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.SITTING_SCANNING) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
        }

        dragon.setTarget(null);
        dragon.setNoAi(false);
        dragon.setSilent(true);
        dragon.getNavigation().stop();
        dragon.setDeltaMovement(Vec3.ZERO);
        dragon.setPos(anchor.x, anchor.y, anchor.z);
        dragon.xo = anchor.x;
        dragon.yo = anchor.y;
        dragon.zo = anchor.z;
        dragon.setYRot(yaw);
        dragon.setYBodyRot(yaw);
        dragon.setYHeadRot(yaw);
        dragon.yRotO = yaw;
        dragon.yBodyRotO = yaw;
        dragon.yHeadRotO = yaw;
        dragon.setXRot(25.0F);
        dragon.xRotO = 25.0F;
        dragon.fallDistance = 0.0F;
        dragon.hasImpulse = true;
        return true;
    }

    public static void clear(CompoundTag data) {
        data.remove(STATIONARY_TAG);
        data.remove(ANCHOR_X_TAG);
        data.remove(ANCHOR_Y_TAG);
        data.remove(ANCHOR_Z_TAG);
        data.remove(ANCHOR_YAW_TAG);
    }

    public static void releaseForCombat(EnderDragon dragon) {
        CompoundTag data = dragon.getPersistentData();
        clear(data);
        data.putBoolean(HERO_SUBMISSION_TAG, false);
        dragon.setSilent(false);

        if (dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.SITTING_SCANNING) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        }

        Vec3 velocity = dragon.getDeltaMovement();
        if (velocity.y < 0.18D) {
            dragon.setDeltaMovement(velocity.x, 0.18D, velocity.z);
        }
        dragon.hasImpulse = true;
    }
}
