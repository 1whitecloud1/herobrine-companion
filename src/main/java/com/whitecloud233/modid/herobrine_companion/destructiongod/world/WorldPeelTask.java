package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

final class WorldPeelTask extends TerrainTask {
    private final int radius;

    WorldPeelTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int radius, int delayTicks) {
        super(level, caster, origin, direction, maxLength, delayTicks);
        this.radius = Math.max(3, radius);
    }

    @Override
    protected boolean doTick(TickBudget budget) {
        int stepsPerTick = 2;
        double stepSize = 2.5D;
        DestructionMode mode = DestructionMode.current();
        LivingEntity caster = this.getCaster();

        for (int step = 0; step < stepsPerTick; step++) {
            if (this.currentLength >= this.maxLength) {
                return true;
            }

            Vec3 center = this.centerPoint();
            BlockPos centerPos = new BlockPos(Mth.floor(center.x), Mth.floor(center.y), Mth.floor(center.z));
            if (!this.level.isLoaded(centerPos)) {
                return true;
            }

            List<BlockPos> toClear = new ArrayList<>();
            for (int dx = -this.radius; dx <= this.radius; dx++) {
                for (int dz = -this.radius; dz <= this.radius; dz++) {
                    int blockX = centerPos.getX() + dx;
                    int blockZ = centerPos.getZ() + dz;
                    int surfaceY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                    int minY = Math.max(this.level.getMinBuildHeight() + 1, surfaceY - 1);
                    int maxY = Math.min(this.level.getMaxBuildHeight() - 1, surfaceY + 12);

                    for (int y = maxY; y >= minY; y--) {
                        BlockPos scanPos = new BlockPos(blockX, y, blockZ);
                        BlockState scanState = this.level.getBlockState(scanPos);
                        if (scanState.isAir()) {
                            continue;
                        }
                        if (!DestructionTerrainManager.isExposedScaffold(this.level, scanPos)) {
                            continue;
                        }
                        toClear.add(scanPos);
                    }
                }
            }
            DestructionTerrainManager.clearBlocksInstant(this.level, toClear, mode, false);

            AABB peelBox = new AABB(center.x - this.radius, center.y - 2.0D, center.z - this.radius,
                    center.x + this.radius, center.y + 12.0D, center.z + this.radius);
            DestructionTerrainManager.damageAndPush(this.level, caster, peelBox, center, 10.0F, 0.9D, 0.35D);
            DestructionTerrainManager.clearLooseEntities(this.level, caster, peelBox);
            this.level.sendParticles(ParticleTypes.PORTAL, center.x, center.y + 1.5D, center.z, 14, 1.2D, 1.8D, 1.2D, 0.08D);
            if (step == 0) {
                this.level.playSound(null, center.x, center.y, center.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.AMBIENT, 2.5F, 0.4F);
            }
            this.currentLength += stepSize;
        }
        return false;
    }
}

