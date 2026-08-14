package com.whitecloud233.herobrine_companion.destructiongod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;

final class BladeLineRendTask extends TerrainTask {
    private final int halfWidth;
    private final int depth;
    private final float slashRollDegrees;
    private final List<BlockPos> pendingBlocks = new ArrayList<>();
    private int pendingBlockIndex;
    private double pendingSegmentEndLength;
    @Nullable
    private Vec3 pendingEffectCenter;

    BladeLineRendTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int halfWidth, int depth, int delayTicks, float slashRollDegrees) {
        super(level, caster, origin, direction, maxLength, delayTicks);
        this.halfWidth = Math.max(3, halfWidth);
        this.depth = Math.max(18, depth);
        this.slashRollDegrees = slashRollDegrees;
        this.pendingSegmentEndLength = 0.0D;
    }

    @Override
    protected boolean doTick(TickBudget budget) {
        int stepsPerTick = 2;
        DestructionMode mode = DestructionMode.DIVINE;
        LivingEntity caster = this.getCaster();

        for (int step = 0; step < stepsPerTick; step++) {
            if (this.pendingBlocks.isEmpty() && this.currentLength >= this.maxLength) {
                Vec3 end = this.centerPoint();
                this.level.playSound(null, end.x, end.y, end.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.AMBIENT, 5.5F, 0.5F);
                DestructionTerrainManager.damageAndPush(this.level, caster,
                        new AABB(end.x - 8.0D, end.y - 6.0D, end.z - 8.0D, end.x + 8.0D, end.y + 10.0D, end.z + 8.0D),
                        end, 26.0F, 1.8D, 0.9D);
                return true;
            }

            if (this.pendingBlocks.isEmpty()) {
                if (!this.prepareNextSegment()) {
                    return true;
                }
            }

            this.processPendingBlocks(budget, mode);
            if (!this.pendingBlocks.isEmpty()) {
                return false;
            }

            Vec3 effectCenter = this.pendingEffectCenter == null ? this.centerPoint() : this.pendingEffectCenter;
            double segmentSpan = Math.max(4.0D, this.pendingSegmentEndLength - this.currentLength);
            double bandRadius = this.halfWidth + segmentSpan * 0.8D;
            AABB slashBand = new AABB(effectCenter.x - bandRadius, effectCenter.y - 8.0D, effectCenter.z - bandRadius,
                    effectCenter.x + bandRadius, effectCenter.y + 16.0D, effectCenter.z + bandRadius);
            DestructionTerrainManager.damageAndPush(this.level, caster, slashBand, effectCenter, 20.0F, 1.45D, 0.72D);
            DestructionTerrainManager.clearLooseEntities(this.level, caster, slashBand);
            DestructionTerrainManager.spawnSpatialRendParticles(this.level, effectCenter, this.forward, this.perpendicular, this.halfWidth + 1, segmentSpan * 1.45D, this.slashRollDegrees);
            this.level.sendParticles(ParticleTypes.EXPLOSION, effectCenter.x, effectCenter.y + 0.7D, effectCenter.z, 2, 0.3D, 0.12D, 0.3D, 0.01D);

            if (this.currentLength <= 0.0D) {
                this.level.playSound(null, effectCenter.x, effectCenter.y, effectCenter.z, SoundEvents.WITHER_SHOOT, SoundSource.AMBIENT, 2.5F, 0.32F);
            }

            this.currentLength = this.pendingSegmentEndLength;
            this.pendingBlocks.clear();
            this.pendingBlockIndex = 0;
            this.pendingEffectCenter = null;
        }

        return false;
    }

    private boolean prepareNextSegment() {
        double stepSize = 4.5D;
        Vec3 center = this.centerPoint();
        BlockPos centerPos = new BlockPos(Mth.floor(center.x), Mth.floor(center.y), Mth.floor(center.z));
        if (!this.level.isLoaded(centerPos)) {
            return false;
        }

        double nextLength = Math.min(this.maxLength, this.currentLength + stepSize);
        Vec3 segmentEnd = this.origin.add(this.forward.scale(nextLength));
        Set<Long> queuedBlocks = new HashSet<>();
        this.pendingBlocks.clear();
        this.pendingBlockIndex = 0;
        this.pendingSegmentEndLength = nextLength;
        this.pendingEffectCenter = center.lerp(segmentEnd, 0.5D);

        this.collectSweptBladePlane(center, segmentEnd, queuedBlocks);
        this.pendingBlocks.sort(Comparator
                .comparingDouble((BlockPos pos) -> Vec3.atCenterOf(pos).subtract(center).dot(this.forward))
                .thenComparingDouble(pos -> Math.abs(Vec3.atCenterOf(pos).subtract(this.pendingEffectCenter).dot(this.slashAxis())))
                .thenComparingInt(BlockPos::getY));
        return true;
    }

    private void collectSweptBladePlane(Vec3 segmentStart, Vec3 segmentEnd, Set<Long> queuedBlocks) {
        double segmentLength = Math.max(0.001D, segmentStart.distanceTo(segmentEnd));
        Vec3 segmentMid = segmentStart.lerp(segmentEnd, 0.5D);
        Vec3 slashAxis = this.slashAxis();
        Vec3 planeNormal = cross(this.forward, slashAxis);
        if (planeNormal.lengthSqr() < 1.0E-4D) {
            planeNormal = this.perpendicular;
        } else {
            planeNormal = planeNormal.normalize();
        }

        double slashReach = Math.max(28.0D, this.depth + 22.0D);
        double halfThickness = Math.max(3.25D, this.halfWidth + 0.55D);
        double halfSegment = segmentLength * 0.5D + 0.85D;
        double radiusX = Math.abs(this.forward.x) * halfSegment + Math.abs(slashAxis.x) * slashReach + Math.abs(planeNormal.x) * halfThickness + 1.5D;
        double radiusY = Math.abs(this.forward.y) * halfSegment + Math.abs(slashAxis.y) * slashReach + Math.abs(planeNormal.y) * halfThickness + 1.5D;
        double radiusZ = Math.abs(this.forward.z) * halfSegment + Math.abs(slashAxis.z) * slashReach + Math.abs(planeNormal.z) * halfThickness + 1.5D;

        int minX = Mth.floor(segmentMid.x - radiusX);
        int maxX = Mth.floor(segmentMid.x + radiusX);
        int minY = Math.max(this.level.getMinBuildHeight() + 1, Mth.floor(segmentMid.y - radiusY));
        int maxY = Math.min(this.level.getMaxBuildHeight() - 1, Mth.floor(segmentMid.y + radiusY));
        int minZ = Mth.floor(segmentMid.z - radiusZ);
        int maxZ = Mth.floor(segmentMid.z + radiusZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Vec3 blockCenter = new Vec3(x + 0.5D, y + 0.5D, z + 0.5D);
                    Vec3 fromStart = blockCenter.subtract(segmentStart);
                    double along = fromStart.dot(this.forward);
                    if (along < -0.85D || along > segmentLength + 0.85D) {
                        continue;
                    }
                    Vec3 fromMid = blockCenter.subtract(segmentMid);
                    if (Math.abs(fromMid.dot(slashAxis)) > slashReach) {
                        continue;
                    }
                    if (Math.abs(fromMid.dot(planeNormal)) > halfThickness) {
                        continue;
                    }

                    BlockPos pos = new BlockPos(x, y, z);
                    long key = pos.asLong();
                    if (queuedBlocks.add(key)) {
                        this.pendingBlocks.add(pos);
                    }
                }
            }
        }
    }

    private void processPendingBlocks(TickBudget budget, DestructionMode mode) {
        if (this.pendingBlocks.isEmpty()) {
            return;
        }

        while (budget.hasBudget() && this.pendingBlockIndex < this.pendingBlocks.size()) {
            BlockPos pos = this.pendingBlocks.get(this.pendingBlockIndex++);
            DestructionTerrainManager.destroyBlock(this.level, pos, budget, mode, false, true);
        }

        if (this.pendingBlockIndex >= this.pendingBlocks.size()) {
            this.pendingBlocks.clear();
            this.pendingBlockIndex = 0;
        }
    }

    private Vec3 slashAxis() {
        double rollRadians = Math.toRadians(this.slashRollDegrees);
        Vec3 axis = this.perpendicular.scale(Math.cos(rollRadians)).add(0.0D, Math.sin(rollRadians), 0.0D);
        if (axis.lengthSqr() < 1.0E-4D) {
            return new Vec3(0.0D, 1.0D, 0.0D);
        }
        return axis.normalize();
    }

    private static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    }
}

