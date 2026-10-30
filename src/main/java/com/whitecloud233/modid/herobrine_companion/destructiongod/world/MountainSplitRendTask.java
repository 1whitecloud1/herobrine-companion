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

import static com.whitecloud233.modid.herobrine_companion.destructiongod.world.DestructionTerrainManager.damageAndPush;
import static com.whitecloud233.modid.herobrine_companion.destructiongod.world.DestructionTerrainManager.clearLooseEntities;

final class MountainSplitRendTask extends TerrainTask {
    private final int halfWidth;
    private final int trenchDepth;
    private final int faultDepth;

    MountainSplitRendTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int halfWidth, int trenchDepth, int faultDepth, int delayTicks) {
        super(level, caster, origin, direction, maxLength, delayTicks);
        this.halfWidth = Math.max(3, halfWidth);
        this.trenchDepth = Math.max(8, trenchDepth);
        this.faultDepth = Math.max(4, faultDepth);
    }

    @Override
    protected boolean doTick(TickBudget budget) {
        int stepsPerTick = 5;
        double stepSize = 1.75D;
        DestructionMode mode = DestructionMode.current();
        LivingEntity caster = this.getCaster();

        return this.sweep(stepsPerTick, stepSize, step -> {
            Vec3 center = this.centerPoint();
            BlockPos centerPos = new BlockPos(Mth.floor(center.x), Mth.floor(center.y), Mth.floor(center.z));
            if (!this.level.isLoaded(centerPos)) {
                return false;
            }

            List<BlockPos> toClear = new ArrayList<>();
            for (int lateral = -this.halfWidth; lateral <= this.halfWidth; lateral++) {
                Vec3 lateralOffset = this.perpendicular.scale(lateral);
                int blockX = Mth.floor(center.x + lateralOffset.x);
                int blockZ = Mth.floor(center.z + lateralOffset.z);
                int surfaceY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                double normalized = 1.0D - (Math.abs(lateral) / (double) this.halfWidth);
                double trenchFactor = Mth.clamp(normalized, 0.0D, 1.0D);
                int localDepth = this.trenchDepth + Mth.floor(trenchFactor * this.faultDepth);
                int edgeCut = Mth.floor((1.0D - trenchFactor) * 4.0D);
                int topY = Math.min(this.level.getMaxBuildHeight() - 1, surfaceY + 1);
                int bottomY = Math.max(this.level.getMinBuildHeight() + 1, surfaceY - localDepth - edgeCut);

                for (int y = topY; y >= bottomY; y--) {
                    toClear.add(new BlockPos(blockX, y, blockZ));
                }

                if (trenchFactor > 0.68D) {
                    int extraBottom = Math.max(this.level.getMinBuildHeight() + 1, bottomY - 2 - Mth.floor(trenchFactor * 3.0D));
                    for (int y = bottomY - 1; y >= extraBottom; y--) {
                        toClear.add(new BlockPos(blockX, y, blockZ));
                    }
                }
            }
            this.queueClearBlocks(toClear, mode, false);

            AABB slice = new AABB(center.x - this.halfWidth - 1.5D, center.y - 8.0D, center.z - this.halfWidth - 1.5D,
                    center.x + this.halfWidth + 1.5D, center.y + 18.0D, center.z + this.halfWidth + 1.5D);
            damageAndPush(this.level, caster, slice, center, 22.0F, 1.6D, 0.85D);
            clearLooseEntities(this.level, caster, slice);

            this.level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.8D, center.z, 5, 0.7D, 0.4D, 0.7D, 0.01D);
            this.level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, center.y + 1.2D, center.z, 8, 0.8D, 0.4D, 0.8D, 0.01D);
            if (step == 0) {
                this.level.playSound(null, center.x, center.y, center.z, SoundEvents.WITHER_SHOOT, SoundSource.AMBIENT, 2.8F, 0.35F);
                this.level.playSound(null, center.x, center.y, center.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 4.0F, 0.7F);
            }
            return true;
        }, () -> {
            Vec3 end = this.centerPoint();
            int impactRadius = Math.max(5, this.halfWidth / 2 + 2);
            List<BlockPos> impactBlocks = new ArrayList<>();
            carveImpactBasin(end, impactRadius, impactBlocks);
            this.queueClearBlocks(impactBlocks, mode, false);
            this.level.playSound(null, end.x, end.y, end.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.AMBIENT, 6.0F, 0.45F);
            this.level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 9.0F, 0.55F);
            damageAndPush(this.level, caster, new AABB(end.x - 16.0D, end.y - 8.0D, end.z - 16.0D, end.x + 16.0D, end.y + 12.0D, end.z + 16.0D), end, 34.0F, 2.2D, 1.25D);
            this.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, end.x, end.y + 0.5D, end.z, 4, 1.0D, 0.5D, 1.0D, 0.0D);
        });
    }

    private void carveImpactBasin(Vec3 center, int radius, List<BlockPos> toClear) {
        int blockCenterX = Mth.floor(center.x);
        int blockCenterZ = Mth.floor(center.z);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq > radius * radius) {
                    continue;
                }
                int blockX = blockCenterX + dx;
                int blockZ = blockCenterZ + dz;
                int surfaceY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                int basinDepth = this.trenchDepth / 2 + Math.max(1, radius - Mth.floor(Math.sqrt(distSq)));
                int topY = Math.min(this.level.getMaxBuildHeight() - 1, surfaceY + 1);
                int bottomY = Math.max(this.level.getMinBuildHeight() + 1, surfaceY - basinDepth);
                for (int y = topY; y >= bottomY; y--) {
                    toClear.add(new BlockPos(blockX, y, blockZ));
                }
            }
        }
    }
}
