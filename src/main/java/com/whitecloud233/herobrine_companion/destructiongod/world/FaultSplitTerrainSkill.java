package com.whitecloud233.herobrine_companion.destructiongod.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class FaultSplitTerrainSkill {
    private FaultSplitTerrainSkill() {
    }

    public static void start(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int terrainHalfWidth, int lineHalfWidth, int shiftDistance, int delayTicks) {
        DestructionTerrainManager.startScytheFaultSplit(level, caster, origin, direction, maxLength, terrainHalfWidth, lineHalfWidth, shiftDistance, delayTicks);
    }
}

