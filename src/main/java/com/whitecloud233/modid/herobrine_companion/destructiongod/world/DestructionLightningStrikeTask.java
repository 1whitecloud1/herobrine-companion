package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

final class DestructionLightningStrikeTask extends TerrainTask {
    private final int radius;
    private final int depth;
    private final List<BlockPos> pendingBlocks = new ArrayList<>();
    private boolean prepared;

    DestructionLightningStrikeTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 center, int radius, int depth, int delayTicks) {
        super(level, caster, center, new Vec3(0.0D, 0.0D, 1.0D), 1.0D, delayTicks);
        this.radius = Math.max(2, radius);
        this.depth = Math.max(8, depth);
    }

    @Override
    protected boolean doTick(TickBudget budget) {
        LivingEntity caster = this.getCaster();
        if (!this.prepared) {
            this.prepareBlocks();
            this.prepared = true;
            this.level.playSound(null, this.origin.x, this.origin.y, this.origin.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 4.8F, 0.72F);
            this.level.playSound(null, this.origin.x, this.origin.y, this.origin.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.55F);
            this.level.sendParticles(ParticleTypes.FLASH, this.origin.x, this.origin.y + 0.8D, this.origin.z, 3, 0.8D, 0.8D, 0.8D, 0.0D);
            this.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.origin.x, this.origin.y + 0.4D, this.origin.z, 80, this.radius * 0.45D, 1.2D, this.radius * 0.45D, 0.16D);
            AABB damageBox = new AABB(this.origin.x - this.radius - 2.0D, this.origin.y - 8.0D, this.origin.z - this.radius - 2.0D,
                    this.origin.x + this.radius + 2.0D, this.origin.y + 14.0D, this.origin.z + this.radius + 2.0D);
            DestructionTerrainManager.damageAndPush(this.level, caster, damageBox, this.origin, 24.0F, 1.6D, 0.95D);
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
        DestructionTerrainManager.collectLightningStrikeBlocks(this.level, this.origin, this.radius, this.depth, this.pendingBlocks, new HashSet<>());
    }
}


