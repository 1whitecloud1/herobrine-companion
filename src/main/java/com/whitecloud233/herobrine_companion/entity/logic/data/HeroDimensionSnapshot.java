package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.util.EndRingContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Copy the complete entity payload, stripping only identity and End Ring presentation state. */
public final class HeroDimensionSnapshot {
    private HeroDimensionSnapshot() {}

    public static CompoundTag prepare(CompoundTag original) {
        CompoundTag copy = original.copy();
        for (String key : new String[]{"UUID", "UUIDMost", "UUIDLeast", "Passengers"}) copy.remove(key);
        ListTag tags = copy.getList("Tags", Tag.TAG_STRING);
        tags.removeIf(tag -> switch (tag.getAsString()) {
            case EndRingContext.TAG_INTRO, EndRingContext.TAG_FIXED, EndRingContext.TAG_TELEPORTING,
                    EndRingContext.TAG_RESPAWNED_SAFE, EndRingContext.TAG_TRANSFER_STAGED -> true;
            default -> false;
        });
        copy.getCompound("ForgeData").remove("IntroTick");
        return copy;
    }
}
