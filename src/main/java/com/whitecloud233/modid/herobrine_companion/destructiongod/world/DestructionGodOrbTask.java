package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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

import static com.whitecloud233.modid.herobrine_companion.destructiongod.world.DestructionTerrainManager.damageAndPush;
import static com.whitecloud233.modid.herobrine_companion.destructiongod.world.DestructionTerrainManager.clearLooseEntities;
import static com.whitecloud233.modid.herobrine_companion.destructiongod.world.DestructionTerrainManager.canBreak;
import static com.whitecloud233.modid.herobrine_companion.destructiongod.world.DestructionTerrainManager.destroyBlock;

final class DestructionGodOrbTask extends TerrainTask {
    private final Vec3 impact;
    private final int fallTicks;
    private final double startRadius;
    private final double maxOrbRadius;
    private final double craterRadius;
    private final int craterDepth;
    private final List<BlockPos> pendingCraterBlocks = new ArrayList<>();
    private int age;
    private int craterIndex;
    private boolean impactPrepared;

    DestructionGodOrbTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 start, Vec3 impact, int fallTicks, double startRadius, double maxOrbRadius, double craterRadius, int craterDepth, int delayTicks) {
        super(level, caster, start, impact.subtract(start), start.distanceTo(impact), delayTicks);
        this.impact = impact;
        this.fallTicks = Math.max(40, fallTicks);
        this.startRadius = Math.max(1.5D, startRadius);
        this.maxOrbRadius = Math.max(this.startRadius + 4.0D, maxOrbRadius);
        this.craterRadius = Math.max(this.maxOrbRadius * 2.0D, craterRadius);
        this.craterDepth = Math.max(24, craterDepth);
    }

    @Override
    protected boolean doTick(TickBudget budget) {
        LivingEntity caster = this.getCaster();
        if (this.age < this.fallTicks) {
            Vec3 orbCenter = this.orbCenter();
            double radius = this.currentRadius();
            this.renderFallingOrb(orbCenter, radius);
            this.suckTerrain(orbCenter, radius, budget);
            this.pullEntities(caster, orbCenter, radius);
            this.age++;
            return false;
        }

        if (!this.impactPrepared) {
            this.prepareImpactCrater();
            this.impactPrepared = true;
            this.level.playSound(null, this.impact.x, this.impact.y, this.impact.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 10.0F, 0.35F);
            this.level.playSound(null, this.impact.x, this.impact.y, this.impact.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 8.0F, 0.45F);
            this.level.playSound(null, this.impact.x, this.impact.y, this.impact.z, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 6.0F, 0.65F);
            this.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.impact.x, this.impact.y + 1.0D, this.impact.z, 12, 2.0D, 1.0D, 2.0D, 0.0D);
            this.level.sendParticles(ParticleTypes.FLASH, this.impact.x, this.impact.y + 1.0D, this.impact.z, 10, 2.0D, 1.0D, 2.0D, 0.0D);
            AABB blast = new AABB(this.impact.x - this.craterRadius, this.impact.y - this.craterDepth, this.impact.z - this.craterRadius,
                    this.impact.x + this.craterRadius, this.impact.y + this.craterRadius * 0.45D, this.impact.z + this.craterRadius);
            damageAndPush(this.level, caster, blast, this.impact, 46.0F, 3.2D, 1.4D);
            clearLooseEntities(this.level, caster, blast);
        }

        this.queueClearBlocks(this.pendingCraterBlocks, DestructionMode.current(), true);
        this.pendingCraterBlocks.clear();
        this.craterIndex = 0;
        return true;
    }

    private Vec3 orbCenter() {
        double progress = Mth.clamp(this.age / (double) this.fallTicks, 0.0D, 1.0D);
        Vec3 apex = this.origin.lerp(this.impact, 0.5D).add(0.0D, 18.0D, 0.0D);
        double inv = 1.0D - progress;
        return this.origin.scale(inv * inv).add(apex.scale(2.0D * inv * progress)).add(this.impact.scale(progress * progress));
    }

    private double currentRadius() {
        double progress = Mth.clamp(this.age / (double) this.fallTicks, 0.0D, 1.0D);
        return Mth.lerp(progress * progress, this.startRadius, this.maxOrbRadius);
    }

    private void renderFallingOrb(Vec3 center, double radius) {
        this.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 12, radius * 0.18D, radius * 0.18D, radius * 0.18D, 0.05D);
        this.level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 8, radius * 0.22D, radius * 0.22D, radius * 0.22D, 0.02D);
        if (this.age % 10 == 0) {
            this.level.playSound(null, center.x, center.y, center.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 2.2F, 0.55F + this.level.random.nextFloat() * 0.15F);
        }
    }

    private void suckTerrain(Vec3 center, double radius, TickBudget budget) {
        int pulls = 32;
        double scanRadius = radius + 8.0D;
        DestructionMode mode = DestructionMode.current();
        for (int i = 0; i < pulls && budget.hasBudget(); i++) {
            double angle = this.level.random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(this.level.random.nextDouble()) * scanRadius;
            int x = Mth.floor(center.x + Math.cos(angle) * distance);
            int z = Mth.floor(center.z + Math.sin(angle) * distance);
            int surfaceY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            int y = Mth.clamp(surfaceY - this.level.random.nextInt(4), this.level.getMinBuildHeight() + 1, this.level.getMaxBuildHeight() - 1);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = this.level.getBlockState(pos);
            if (state.isAir() || !canBreak(this.level, pos, state, mode, true)) {
                continue;
            }
            destroyBlock(this.level, pos, budget, mode, true);
            Vec3 p = Vec3.atCenterOf(pos);
            this.level.sendParticles(ParticleTypes.POOF, p.x, p.y, p.z, 2, 0.08D, 0.08D, 0.08D, 0.02D);
            this.level.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 6, (center.x - p.x) * 0.07D, (center.y - p.y) * 0.07D, (center.z - p.z) * 0.07D, 0.16D);
            this.level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, (center.x - p.x) * 0.03D, (center.y - p.y) * 0.03D, (center.z - p.z) * 0.03D, 0.0D);
            this.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), p.x, p.y, p.z, 8, (center.x - p.x) * 0.06D, (center.y - p.y) * 0.06D, (center.z - p.z) * 0.06D, 0.08D);
        }
    }

    private void pullEntities(@Nullable LivingEntity caster, Vec3 center, double radius) {
        AABB area = new AABB(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
        List<LivingEntity> targets = this.level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity.isAlive() && entity != caster && !(entity instanceof HeroEntity));
        for (LivingEntity target : targets) {
            Vec3 pull = center.subtract(target.position());
            if (pull.lengthSqr() > 1.0E-4D) {
                pull = pull.normalize();
                target.push(pull.x * 0.18D, pull.y * 0.12D + 0.05D, pull.z * 0.18D);
            }
            if (this.age % 15 == 0) {
                target.hurt(this.level.damageSources().magic(), 4.0F);
            }
        }
    }

    private void prepareImpactCrater() {
        int radius = Mth.ceil(this.craterRadius);
        int centerX = Mth.floor(this.impact.x);
        int centerY = Mth.floor(this.impact.y);
        int centerZ = Mth.floor(this.impact.z);
        double radiusSq = this.craterRadius * this.craterRadius;
        double depth = this.craterDepth;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double horizontalSq = dx * dx + dz * dz;
                if (horizontalSq > radiusSq) {
                    continue;
                }
                double horizontalFactor = Math.sqrt(horizontalSq) / this.craterRadius;
                double localDepth = depth * Math.sqrt(Math.max(0.0D, 1.0D - horizontalFactor * horizontalFactor));
                int topY = Math.min(this.level.getMaxBuildHeight() - 1, centerY + 4 - Mth.floor(horizontalFactor * 4.0D));
                int bottomY = Math.max(this.level.getMinBuildHeight() + 1, centerY - Mth.ceil(localDepth));
                for (int y = topY; y >= bottomY; y--) {
                    double vertical = (centerY - y) / Math.max(1.0D, depth);
                    if (horizontalFactor * horizontalFactor + vertical * vertical <= 1.08D) {
                        this.pendingCraterBlocks.add(new BlockPos(centerX + dx, y, centerZ + dz));
                    }
                }
            }
        }
    }
}
