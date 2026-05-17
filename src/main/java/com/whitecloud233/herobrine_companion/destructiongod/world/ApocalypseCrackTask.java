package com.whitecloud233.herobrine_companion.destructiongod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

final class ApocalypseCrackTask extends TerrainTask {
    private final double sideOffset;
    private final int crackHalfWidth;
    private final int depth;

    ApocalypseCrackTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, double sideOffset, int crackHalfWidth, int depth, int delayTicks) {
        super(level, caster, origin, direction, maxLength, delayTicks);
        this.sideOffset = Math.max(3.0D, sideOffset);
        this.crackHalfWidth = Math.max(1, crackHalfWidth);
        this.depth = Math.max(5, depth);
    }

    @Override
    protected boolean doTick(TickBudget budget) {
        int stepsPerTick = 3;
        double stepSize = 1.5D;
        DestructionMode mode = DestructionMode.current();
        LivingEntity caster = this.getCaster();

        for (int step = 0; step < stepsPerTick; step++) {
            if (this.currentLength >= this.maxLength) {
                return true;
            }

            Vec3 base = this.centerPoint();
            for (int side = -1; side <= 1; side += 2) {
                Vec3 crackCenter = base.add(this.perpendicular.scale(this.sideOffset * side));
                BlockPos crackPos = new BlockPos(Mth.floor(crackCenter.x), Mth.floor(crackCenter.y), Mth.floor(crackCenter.z));
                if (!this.level.isLoaded(crackPos)) {
                    continue;
                }

                List<BlockPos> toClear = new ArrayList<>();
                for (int lateral = -this.crackHalfWidth; lateral <= this.crackHalfWidth; lateral++) {
                    Vec3 lateralOffset = this.perpendicular.scale(lateral);
                    int blockX = Mth.floor(crackCenter.x + lateralOffset.x);
                    int blockZ = Mth.floor(crackCenter.z + lateralOffset.z);
                    int surfaceY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                    int jaggedDepth = this.depth + Math.abs((blockX * 31 + blockZ * 17 + Mth.floor(this.currentLength * 7.0D)) % 4);
                    int topY = Math.min(this.level.getMaxBuildHeight() - 1, surfaceY + 1);
                    int bottomY = Math.max(this.level.getMinBuildHeight() + 1, surfaceY - jaggedDepth);

                    for (int y = topY; y >= bottomY; y--) {
                        toClear.add(new BlockPos(blockX, y, blockZ));
                    }
                }
                DestructionTerrainManager.clearBlocksInstant(this.level, toClear, mode, false);

                AABB crackBox = new AABB(crackCenter.x - this.crackHalfWidth - 1.0D, crackCenter.y - 4.0D, crackCenter.z - this.crackHalfWidth - 1.0D,
                        crackCenter.x + this.crackHalfWidth + 1.0D, crackCenter.y + 6.0D, crackCenter.z + this.crackHalfWidth + 1.0D);
                DestructionTerrainManager.damageAndPush(this.level, caster, crackBox, crackCenter, 14.0F, 1.1D, 0.5D);
                this.level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, crackCenter.x, crackCenter.y + 0.2D, crackCenter.z, 4, 0.4D, 0.2D, 0.4D, 0.01D);
            }

            if (step == 0) {
                this.level.playSound(null, base.x, base.y, base.z, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 2.4F, 1.45F);
            }
            this.currentLength += stepSize;
        }
        return false;
    }
}

