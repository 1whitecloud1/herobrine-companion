package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class OrbTerrainSkill {
    private OrbTerrainSkill() {
    }

    public static void start(ServerLevel level, @Nullable LivingEntity caster, Vec3 start, Vec3 impact, int fallTicks, double startRadius, double maxOrbRadius, double craterRadius, int craterDepth, int delayTicks) {
        DestructionTerrainManager.startDestructionGodOrb(level, caster, start, impact, fallTicks, startRadius, maxOrbRadius, craterRadius, craterDepth, delayTicks);
    }
}

