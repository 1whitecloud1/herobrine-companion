package com.whitecloud233.modid.herobrine_companion.entity.family;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class HerobrineFamilyWorldData extends SavedData {
    private static final String DATA_NAME = "herobrine_companion_family_world";
    private static final String LAST_SUMMON_TIMES_TAG = "LastSummonTimes";
    private static final String PASSED_TRIALS_TAG = "PassedTrials";

    private final EnumMap<HerobrineFamilyMemberType, Long> lastSummonTimes = new EnumMap<>(HerobrineFamilyMemberType.class);
    private final EnumMap<HerobrineFamilyMemberType, Set<UUID>> passedTrials = new EnumMap<>(HerobrineFamilyMemberType.class);

    public static HerobrineFamilyWorldData load(CompoundTag tag) {
        HerobrineFamilyWorldData data = new HerobrineFamilyWorldData();
        CompoundTag lastTimesTag = tag.getCompound(LAST_SUMMON_TIMES_TAG);
        for (HerobrineFamilyMemberType type : HerobrineFamilyMemberType.values()) {
            if (lastTimesTag.contains(type.id())) {
                data.lastSummonTimes.put(type, lastTimesTag.getLong(type.id()));
            }
        }
        CompoundTag passedTrialsTag = tag.getCompound(PASSED_TRIALS_TAG);
        for (HerobrineFamilyMemberType type : HerobrineFamilyMemberType.values()) {
            CompoundTag typeTag = passedTrialsTag.getCompound(type.id());
            for (String playerId : typeTag.getAllKeys()) {
                try {
                    data.passedTrials.computeIfAbsent(type, ignored -> new HashSet<>()).add(UUID.fromString(playerId));
                } catch (IllegalArgumentException ignored) {
                    // Skip invalid legacy data instead of failing the world load.
                }
            }
        }
        return data;
    }

    public static HerobrineFamilyWorldData get(ServerLevel level) {
        ServerLevel overworld = Objects.requireNonNull(level.getServer().getLevel(Level.OVERWORLD));
        return overworld.getDataStorage().computeIfAbsent(HerobrineFamilyWorldData::load, HerobrineFamilyWorldData::new, DATA_NAME);
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        CompoundTag lastTimesTag = new CompoundTag();
        for (Map.Entry<HerobrineFamilyMemberType, Long> entry : lastSummonTimes.entrySet()) {
            lastTimesTag.putLong(entry.getKey().id(), entry.getValue());
        }
        tag.put(LAST_SUMMON_TIMES_TAG, lastTimesTag);

        CompoundTag passedTrialsTag = new CompoundTag();
        for (Map.Entry<HerobrineFamilyMemberType, Set<UUID>> entry : passedTrials.entrySet()) {
            CompoundTag typeTag = new CompoundTag();
            for (UUID playerId : entry.getValue()) {
                typeTag.putBoolean(playerId.toString(), true);
            }
            passedTrialsTag.put(entry.getKey().id(), typeTag);
        }
        tag.put(PASSED_TRIALS_TAG, passedTrialsTag);
        return tag;
    }

    public long getRemainingCooldown(HerobrineFamilyMemberType type, long now) {
        long readyAt = lastSummonTimes.getOrDefault(type, Long.MIN_VALUE / 4L) + type.cooldownTicks();
        return Math.max(0L, readyAt - now);
    }

    public boolean isCooldownReady(HerobrineFamilyMemberType type, long now) {
        return getRemainingCooldown(type, now) <= 0L;
    }

    public void markSummoned(HerobrineFamilyMemberType type, long now) {
        lastSummonTimes.put(type, now);
        setDirty();
    }

    public void markTrialPassed(HerobrineFamilyMemberType type, UUID playerId) {
        if (type == null || playerId == null) {
            return;
        }

        passedTrials.computeIfAbsent(type, ignored -> new HashSet<>()).add(playerId);
        setDirty();
    }

    public boolean hasTrialPassed(HerobrineFamilyMemberType type, UUID playerId) {
        if (type == null || playerId == null) {
            return false;
        }

        return passedTrials.getOrDefault(type, Set.of()).contains(playerId);
    }
}
