package com.whitecloud233.herobrine_companion.destructiongod.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class LightningTerrainSkill {
    private LightningTerrainSkill() {
    }

    public static void startStrike(ServerLevel level, @Nullable LivingEntity caster, Vec3 center, int radius, int depth, int delayTicks) {
        DestructionTerrainManager.startDestructionLightningStrike(level, caster, center, radius, depth, delayTicks);
    }

    public static void startArc(ServerLevel level, @Nullable LivingEntity caster, Vec3 start, Vec3 end, double radius, int delayTicks) {
        DestructionTerrainManager.startDestructionLightningArc(level, caster, start, end, radius, delayTicks);
    }

    public static void startBeam(ServerLevel level, @Nullable LivingEntity caster, Vec3 start, Vec3 end, double radius, int delayTicks) {
        DestructionTerrainManager.startDestructionLightningBeam(level, caster, start, end, radius, delayTicks);
    }

    public static void startThunderSkyNet(ServerLevel level, @Nullable LivingEntity caster, Vec3 center, double cloudY, double radius, int durationTicks, int strikesPerPulse, int pulseIntervalTicks, int delayTicks) {
        DestructionTerrainManager.startThunderSkyNet(level, caster, center, cloudY, radius, durationTicks, strikesPerPulse, pulseIntervalTicks, delayTicks);
    }
}

