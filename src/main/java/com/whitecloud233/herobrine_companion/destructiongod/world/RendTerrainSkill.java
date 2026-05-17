package com.whitecloud233.herobrine_companion.destructiongod.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class RendTerrainSkill {
    private RendTerrainSkill() {
    }

    public static void startCombo(ServerLevel level, LivingEntity caster) {
        DestructionTerrainManager.startDestructionCombo(level, caster);
    }

    public static void startWorldRend(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int halfWidth, int depth, int delayTicks) {
        DestructionTerrainManager.startWorldRend(level, caster, origin, direction, maxLength, halfWidth, depth, delayTicks);
    }

    public static void startMountainSplitRend(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int halfWidth, int trenchDepth, int faultDepth, int delayTicks) {
        DestructionTerrainManager.startMountainSplitRend(level, caster, origin, direction, maxLength, halfWidth, trenchDepth, faultDepth, delayTicks);
    }

    public static void startBladeLineRend(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int halfWidth, int depth, int delayTicks, float slashRollDegrees) {
        DestructionTerrainManager.startBladeLineRend(level, caster, origin, direction, maxLength, halfWidth, depth, delayTicks, slashRollDegrees);
    }

    public static void startApocalypseCrack(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, double sideOffset, int crackHalfWidth, int depth, int delayTicks) {
        DestructionTerrainManager.startApocalypseCrack(level, caster, origin, direction, maxLength, sideOffset, crackHalfWidth, depth, delayTicks);
    }

    public static void startWorldPeel(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int radius, int delayTicks) {
        DestructionTerrainManager.startWorldPeel(level, caster, origin, direction, maxLength, radius, delayTicks);
    }

    public static void startWorldCollapse(ServerLevel level, @Nullable LivingEntity caster, Vec3 center, double startRadius, double endRadius, int bandWidth, int delayTicks) {
        DestructionTerrainManager.startWorldCollapse(level, caster, center, startRadius, endRadius, bandWidth, delayTicks);
    }
}

