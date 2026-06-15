package com.whitecloud233.herobrine_companion.entity.awakened.containment;

import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyMemberType;
import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyMembers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;

public final class AwakenedMobCaptureService {
    private AwakenedMobCaptureService() {
    }

    public static CaptureResult capture(ServerPlayer player, Mob mob, ItemStack vessel) {
        if (AwakenedVesselStorage.hasCapturedMob(vessel)) {
            return CaptureResult.failed(Failure.ALREADY_FULL);
        }

        Failure failure = AwakenedVesselEligibility.getCaptureFailure(mob);
        if (failure != null) {
            return CaptureResult.failed(failure);
        }

        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (entityTypeId == null || !(mob.level() instanceof ServerLevel serverLevel)) {
            return CaptureResult.failed(Failure.UNSUPPORTED);
        }

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

        CompoundTag entityData = new CompoundTag();
        mob.saveWithoutId(entityData);
        AwakenedMobTransportSanitizer.sanitizeCapturedData(entityData);

        HerobrineFamilyMemberType familyType = HerobrineFamilyMembers.getType(mob);
        String capturedName = mob.getDisplayName().getString();
        CapturedAwakenedMob captured = new CapturedAwakenedMob(
                entityTypeId,
                entityData,
                capturedName,
                player.getUUID(),
                serverLevel.getGameTime(),
                serverLevel.dimension().location(),
                HerobrineFamilyMembers.isFamilyMember(mob, HerobrineFamilyMemberType.JEAN),
                familyType == null ? "" : familyType.id()
        );

        AwakenedVesselStorage.write(vessel, captured);
        mob.discard();
        return CaptureResult.success(capturedName);
    }

    public enum Failure {
        ALREADY_FULL,
        NOT_AWAKENED,
        UNSUPPORTED,
        REMOVED,
        INVALID_DATA,
        NO_SPACE,
        EMPTY,
        SPAWN_FAILED
    }

    public record CaptureResult(boolean success, Failure failure, String capturedName) {
        public static CaptureResult success(String capturedName) {
            return new CaptureResult(true, null, capturedName);
        }

        public static CaptureResult failed(Failure failure) {
            return new CaptureResult(false, failure, "");
        }
    }
}
