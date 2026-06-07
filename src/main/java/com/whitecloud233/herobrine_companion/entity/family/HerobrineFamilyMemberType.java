package com.whitecloud233.herobrine_companion.entity.family;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum HerobrineFamilyMemberType {
    SIMMONS("simmons", EntityType.WITHER, 60, 3L * 24000L),
    JEAN("jean", EntityType.ENDER_DRAGON, 85, 5L * 24000L);

    private static final Map<String, HerobrineFamilyMemberType> BY_ID = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(HerobrineFamilyMemberType::id, Function.identity()));

    private final String id;
    private final EntityType<? extends Mob> entityType;
    private final int requiredTrust;
    private final long cooldownTicks;

    HerobrineFamilyMemberType(String id, EntityType<? extends Mob> entityType, int requiredTrust, long cooldownTicks) {
        this.id = id;
        this.entityType = entityType;
        this.requiredTrust = requiredTrust;
        this.cooldownTicks = cooldownTicks;
    }

    public String id() {
        return id;
    }

    public EntityType<? extends Mob> entityType() {
        return entityType;
    }

    public int requiredTrust() {
        return requiredTrust;
    }

    public long cooldownTicks() {
        return cooldownTicks;
    }

    public static HerobrineFamilyMemberType byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }
}
