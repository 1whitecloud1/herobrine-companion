package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

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
import java.util.ArrayList;
import java.util.List;

final class DestructionLightningArcTask extends TerrainTask {
    private final Vec3 end;
    private final double radius;
    private final List<BlockPos> pendingBlocks = new ArrayList<>();
    private boolean prepared;

    DestructionLightningArcTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 start, Vec3 end, double radius, int delayTicks) {
        super(level, caster, start, end.subtract(start), start.distanceTo(end), delayTicks);
        this.end = end;
        this.radius = Math.max(1.5D, radius);
    }

    @Override
    protected boolean doTick(TickBudget budget) {
        LivingEntity caster = this.getCaster();
        if (!this.prepared) {
            this.prepareBlocks();
            this.prepared = true;
            Vec3 midpoint = this.origin.lerp(this.end, 0.5D);
            this.level.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.6F, 1.45F);
            this.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, midpoint.x, midpoint.y, midpoint.z, 50, 1.2D, 1.2D, 1.2D, 0.18D);
            AABB damageBox = new AABB(
                    Math.min(this.origin.x, this.end.x) - this.radius - 2.0D, Math.min(this.origin.y, this.end.y) - this.radius - 2.0D, Math.min(this.origin.z, this.end.z) - this.radius - 2.0D,
                    Math.max(this.origin.x, this.end.x) + this.radius + 2.0D, Math.max(this.origin.y, this.end.y) + this.radius + 2.0D, Math.max(this.origin.z, this.end.z) + this.radius + 2.0D);
            DestructionTerrainManager.damageAndPush(this.level, caster, damageBox, midpoint, 14.0F, 0.9D, 0.55D);
            DestructionTerrainManager.clearLooseEntities(this.level, caster, damageBox);
        }

        DestructionMode mode = DestructionMode.current();
        if (!this.pendingBlocks.isEmpty()) {
            DestructionTerrainManager.clearBlocksInstant(this.level, this.pendingBlocks, mode, true);
            this.pendingBlocks.clear();
        }
        return true;
    }

    private void prepareBlocks() {
        Vec3 segment = this.end.subtract(this.origin);
        double lengthSq = segment.lengthSqr();
        if (lengthSq < 1.0E-4D) {
            return;
        }

        int minX = Mth.floor(Math.min(this.origin.x, this.end.x) - this.radius - 1.0D);
        int maxX = Mth.floor(Math.max(this.origin.x, this.end.x) + this.radius + 1.0D);
        int minY = Math.max(this.level.getMinBuildHeight() + 1, Mth.floor(Math.min(this.origin.y, this.end.y) - this.radius - 1.0D));
        int maxY = Math.min(this.level.getMaxBuildHeight() - 1, Mth.floor(Math.max(this.origin.y, this.end.y) + this.radius + 1.0D));
        int minZ = Mth.floor(Math.min(this.origin.z, this.end.z) - this.radius - 1.0D);
        int maxZ = Mth.floor(Math.max(this.origin.z, this.end.z) + this.radius + 1.0D);
        double radiusSq = this.radius * this.radius;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Vec3 blockCenter = new Vec3(x + 0.5D, y + 0.5D, z + 0.5D);
                    double t = Mth.clamp(blockCenter.subtract(this.origin).dot(segment) / lengthSq, 0.0D, 1.0D);
                    Vec3 closest = this.origin.add(segment.scale(t));
                    if (blockCenter.distanceToSqr(closest) <= radiusSq) {
                        this.pendingBlocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }
}

