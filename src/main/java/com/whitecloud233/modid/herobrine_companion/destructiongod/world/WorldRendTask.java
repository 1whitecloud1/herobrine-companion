package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

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

final class WorldRendTask extends TerrainTask {
    private final int halfWidth;
    private final int depth;

    WorldRendTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int halfWidth, int depth, int delayTicks) {
        super(level, caster, origin, direction, maxLength, delayTicks);
        this.halfWidth = Math.max(1, halfWidth);
        this.depth = Math.max(4, depth);
    }

    @Override
    protected boolean doTick(TickBudget budget) {
        int stepsPerTick = 4;
        double stepSize = 1.25D;
        DestructionMode mode = DestructionMode.current();
        LivingEntity caster = this.getCaster();

        for (int step = 0; step < stepsPerTick; step++) {
            if (this.currentLength >= this.maxLength) {
                Vec3 end = this.centerPoint();
                this.level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 8.0F, 0.65F);
                this.level.playSound(null, end.x, end.y, end.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 6.0F, 0.75F);
                DestructionTerrainManager.damageAndPush(this.level, caster, new AABB(end.x - 10.0D, end.y - 6.0D, end.z - 10.0D, end.x + 10.0D, end.y + 10.0D, end.z + 10.0D), end, 28.0F, 1.9D, 1.1D);
                return true;
            }

            Vec3 center = this.centerPoint();
            BlockPos centerPos = new BlockPos(Mth.floor(center.x), Mth.floor(center.y), Mth.floor(center.z));
            if (!this.level.isLoaded(centerPos)) {
                return true;
            }

            List<BlockPos> toClear = new ArrayList<>();
            for (int lateral = -this.halfWidth; lateral <= this.halfWidth; lateral++) {
                Vec3 lateralOffset = this.perpendicular.scale(lateral);
                int blockX = Mth.floor(center.x + lateralOffset.x);
                int blockZ = Mth.floor(center.z + lateralOffset.z);
                int surfaceY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                int topY = Math.min(this.level.getMaxBuildHeight() - 1, surfaceY + 1);
                int bottomY = Math.max(this.level.getMinBuildHeight() + 1, surfaceY - this.depth);

                for (int y = topY; y >= bottomY; y--) {
                    toClear.add(new BlockPos(blockX, y, blockZ));
                }
            }
            DestructionTerrainManager.clearBlocksInstant(this.level, toClear, mode, false);

            AABB slice = new AABB(center.x - this.halfWidth - 1.0D, center.y - 4.0D, center.z - this.halfWidth - 1.0D,
                    center.x + this.halfWidth + 1.0D, center.y + 8.0D, center.z + this.halfWidth + 1.0D);
            DestructionTerrainManager.damageAndPush(this.level, caster, slice, center, 18.0F, 1.3D, 0.7D);
            DestructionTerrainManager.clearLooseEntities(this.level, caster, slice);
            this.level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5D, center.z, 3, 0.5D, 0.3D, 0.5D, 0.01D);
            if (step == 0) {
                this.level.playSound(null, center.x, center.y, center.z, SoundEvents.WITHER_SHOOT, SoundSource.AMBIENT, 2.0F, 0.45F);
            }

            this.currentLength += stepSize;
        }

        return false;
    }
}

