package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
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

final class DestructionLightningBeamTask extends TerrainTask {
    private final Vec3 end;
    private final double radius;
    private final List<BlockPos> pendingBlocks = new ArrayList<>();
    private boolean prepared;

    DestructionLightningBeamTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 start, Vec3 end, double radius, int delayTicks) {
        super(level, caster, start, end.subtract(start), start.distanceTo(end), delayTicks);
        this.end = end;
        this.radius = Math.max(2.5D, radius);
    }

    @Override
    protected boolean doTick(TickBudget budget) {
        LivingEntity caster = this.getCaster();
        if (!this.prepared) {
            this.prepareBlocks();
            this.prepared = true;
            Vec3 midpoint = this.origin.lerp(this.end, 0.5D);
            this.level.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 6.0F, 0.52F);
            this.level.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 7.5F, 0.66F);
            this.level.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.8F, 0.58F);
            this.level.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.8F, 0.40F);
            this.level.sendParticles(ParticleTypes.FLASH, this.origin.x, this.origin.y, this.origin.z, 3, 0.5D, 0.5D, 0.5D, 0.0D);
            this.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, midpoint.x, midpoint.y, midpoint.z, 90, this.radius, this.radius, this.radius, 0.20D);
            this.damageCylinder(caster, midpoint);
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

    private void damageCylinder(@Nullable LivingEntity caster, Vec3 midpoint) {
        Vec3 segment = this.end.subtract(this.origin);
        double lengthSq = segment.lengthSqr();
        if (lengthSq < 1.0E-4D) {
            return;
        }
        double searchRadius = this.origin.distanceTo(this.end) * 0.5D + this.radius + 4.0D;
        AABB search = new AABB(midpoint.x - searchRadius, midpoint.y - searchRadius, midpoint.z - searchRadius,
                midpoint.x + searchRadius, midpoint.y + searchRadius, midpoint.z + searchRadius);
        List<LivingEntity> targets = this.level.getEntitiesOfClass(LivingEntity.class, search, entity -> entity.isAlive() && entity != caster && !(entity instanceof HeroEntity));
        double damageRadiusSq = (this.radius + 1.5D) * (this.radius + 1.5D);
        for (LivingEntity target : targets) {
            Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            double t = Mth.clamp(targetCenter.subtract(this.origin).dot(segment) / lengthSq, 0.0D, 1.0D);
            Vec3 closest = this.origin.add(segment.scale(t));
            double distSq = targetCenter.distanceToSqr(closest);
            if (distSq > damageRadiusSq) {
                continue;
            }
            double scale = Mth.clamp(1.0D - Math.sqrt(distSq) / (this.radius + 1.5D), 0.35D, 1.0D);
            target.hurt(this.level.damageSources().magic(), (float) (26.0D * scale));
            Vec3 push = targetCenter.subtract(closest);
            if (push.lengthSqr() < 1.0E-4D) {
                push = segment.normalize();
            } else {
                push = push.normalize();
            }
            target.push(push.x * 1.4D * scale, 0.55D * scale, push.z * 1.4D * scale);
        }
    }
}

