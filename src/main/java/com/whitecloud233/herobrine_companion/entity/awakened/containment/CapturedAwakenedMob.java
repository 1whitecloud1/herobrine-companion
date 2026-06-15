package com.whitecloud233.herobrine_companion.entity.awakened.containment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record CapturedAwakenedMob(
        ResourceLocation entityTypeId,
        CompoundTag entityData,
        String capturedName,
        UUID capturedBy,
        long capturedGameTime,
        ResourceLocation capturedDimension,
        boolean jean,
        String familyMemberId
) {
    public CapturedAwakenedMob {
        entityData = entityData == null ? new CompoundTag() : entityData.copy();
        capturedName = capturedName == null ? "" : capturedName;
        familyMemberId = familyMemberId == null ? "" : familyMemberId;
    }

    public CompoundTag copyEntityData() {
        return entityData.copy();
    }
}
