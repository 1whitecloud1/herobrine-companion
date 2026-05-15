package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

import com.whitecloud233.modid.herobrine_companion.config.Config;
import com.whitecloud233.modid.herobrine_companion.destructiongod.entity.DestructionGodHerobrineEntity;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
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

final class WorldCollapseTask extends TerrainTask {
    private final double endRadius;
    private final int bandWidth;
    private double currentRadius;

    WorldCollapseTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 center, double startRadius, double endRadius, int bandWidth, int delayTicks) {
        super(level, caster, center, new Vec3(0.0D, 0.0D, 1.0D), startRadius, delayTicks);
        this.currentRadius = Math.max(endRadius + 2.0D, startRadius);
        this.endRadius = Math.max(3.0D, endRadius);
        this.bandWidth = Math.max(2, bandWidth);
    }

    @Override
    protected boolean doTick(TickBudget budget) {
        LivingEntity caster = this.getCaster();
        DestructionMode mode = DestructionMode.current();
        if (!Config.destructionGodFinalPhaseWorldCollapse && !(caster instanceof DestructionGodHerobrineEntity)) {
            return true;
        }
        if (this.currentRadius <= this.endRadius) {
            this.level.playSound(null, this.origin.x, this.origin.y, this.origin.z, SoundEvents.WITHER_DEATH, SoundSource.AMBIENT, 6.0F, 1.35F);
            return true;
        }

        int ceilRadius = Mth.ceil(this.currentRadius + this.bandWidth);
        double inner = Math.max(this.endRadius, this.currentRadius - this.bandWidth);
        double innerSq = inner * inner;
        double outerSq = (this.currentRadius + this.bandWidth) * (this.currentRadius + this.bandWidth);

        List<BlockPos> toClear = new ArrayList<>();
        for (int dx = -ceilRadius; dx <= ceilRadius; dx++) {
            for (int dz = -ceilRadius; dz <= ceilRadius; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq < innerSq || distSq > outerSq) {
                    continue;
                }

                int blockX = Mth.floor(this.origin.x) + dx;
                int blockZ = Mth.floor(this.origin.z) + dz;
                int surfaceY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                int topY = Math.min(this.level.getMaxBuildHeight() - 1, surfaceY + 1);
                int bottomY = Math.max(this.level.getMinBuildHeight() + 1, surfaceY - 10);

                for (int y = topY; y >= bottomY; y--) {
                    toClear.add(new BlockPos(blockX, y, blockZ));
                }
            }
        }
        DestructionTerrainManager.clearBlocksInstant(this.level, toClear, mode, false);

        AABB collapseBand = new AABB(this.origin.x - this.currentRadius - this.bandWidth, this.origin.y - 8.0D, this.origin.z - this.currentRadius - this.bandWidth,
                this.origin.x + this.currentRadius + this.bandWidth, this.origin.y + 16.0D, this.origin.z + this.currentRadius + this.bandWidth);
        List<LivingEntity> targets = this.level.getEntitiesOfClass(LivingEntity.class, collapseBand, entity -> entity.isAlive() && entity != caster && !(entity instanceof HeroEntity));
        for (LivingEntity target : targets) {
            double dx = target.getX() - this.origin.x;
            double dz = target.getZ() - this.origin.z;
            double distSq = dx * dx + dz * dz;
            if (distSq < innerSq || distSq > outerSq) {
                continue;
            }
            Vec3 pull = new Vec3(this.origin.x - target.getX(), 0.0D, this.origin.z - target.getZ());
            if (pull.lengthSqr() > 1.0E-4D) {
                pull = pull.normalize();
                target.push(pull.x * 1.15D, 0.55D, pull.z * 1.15D);
            }
            target.hurt(this.level.damageSources().magic(), 20.0F);
        }

        this.level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.origin.x, this.origin.y + 1.0D, this.origin.z, 26, this.currentRadius * 0.3D, 1.6D, this.currentRadius * 0.3D, 0.02D);
        this.level.playSound(null, this.origin.x, this.origin.y, this.origin.z, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 3.5F, 0.55F + this.level.random.nextFloat() * 0.15F);
        this.currentRadius -= 1.75D;
        return false;
    }
}

