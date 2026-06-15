package com.whitecloud233.herobrine_companion.entity.awakened.containment;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobProfiles;
import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyMembers;
import net.minecraft.world.entity.Mob;

public final class AwakenedVesselEligibility {
    private AwakenedVesselEligibility() {
    }

    public static AwakenedMobCaptureService.Failure getCaptureFailure(Mob mob) {
        if (mob == null || mob instanceof HeroEntity) {
            return AwakenedMobCaptureService.Failure.UNSUPPORTED;
        }
        if (!mob.isAlive() || mob.isRemoved()) {
            return AwakenedMobCaptureService.Failure.REMOVED;
        }
        if (AwakenedMobProfiles.get(mob) == null) {
            return AwakenedMobCaptureService.Failure.UNSUPPORTED;
        }
        if (HerobrineFamilyMembers.ensureAwakenedIdentity(mob)) {
            return null;
        }
        if (!(mob instanceof AwakenedMobAccessor accessor) || !accessor.herobrineCompanion$isAwakenedMob()) {
            return AwakenedMobCaptureService.Failure.NOT_AWAKENED;
        }
        return null;
    }
}
