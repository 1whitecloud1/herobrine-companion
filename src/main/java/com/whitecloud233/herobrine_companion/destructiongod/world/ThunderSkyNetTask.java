package com.whitecloud233.herobrine_companion.destructiongod.world;

import com.whitecloud233.herobrine_companion.destructiongod.network.DestructionGodLightningArcPacket;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ThunderSkyNetTask extends TerrainTask {
    private final double cloudY;
    private final double radius;
    private final double actualStrikeRadius;
    private final int durationTicks;
    private final int strikesPerPulse;
    private final int pulseIntervalTicks;
    private int age;

    ThunderSkyNetTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 center, double cloudY, double radius, int durationTicks, int strikesPerPulse, int pulseIntervalTicks, int delayTicks) {
        super(level, caster, center, new Vec3(0.0D, 0.0D, 1.0D), 1.0D, delayTicks);
        this.cloudY = cloudY;
        this.radius = Math.max(48.0D, radius);
        this.actualStrikeRadius = Math.max(44.0D, this.radius * 0.82D);
        this.durationTicks = Math.max(40, durationTicks);
        this.strikesPerPulse = Math.max(3, strikesPerPulse);
        this.pulseIntervalTicks = Math.max(1, pulseIntervalTicks);
    }

    @Override
    protected boolean doTick(TickBudget budget) {
        LivingEntity caster = this.getCaster();
        if (this.age == 0) {
            this.level.playSound(null, this.origin.x, this.origin.y + 4.0D, this.origin.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 8.0F, 0.55F);
            this.level.playSound(null, this.origin.x, this.origin.y + 4.0D, this.origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 4.5F, 0.42F);
        }
        if (this.age >= this.durationTicks) {
            return true;
        }

        if (this.age % this.pulseIntervalTicks == 0) {
            List<BlockPos> pulseBlocks = new ArrayList<>();
            Set<Long> queuedKeys = new HashSet<>();
            DestructionMode mode = DestructionMode.current();
            for (int i = 0; i < this.strikesPerPulse; i++) {
                Vec3 impact = this.sampleImpactPoint();
                Vec3 cloudAnchor = this.sampleCloudAnchor(impact);
                int strikeRadius = 2 + this.level.random.nextInt(2);
                int strikeDepth = 8 + this.level.random.nextInt(5);
                if (caster != null) {
                    float strikeWidth = 0.12F + this.level.random.nextFloat() * 0.04F;
                    PacketHandler.sendToTracking(new DestructionGodLightningArcPacket(cloudAnchor, impact, strikeWidth, 18), caster);
                }
                DestructionTerrainManager.collectLightningStrikeBlocks(this.level, impact, strikeRadius, strikeDepth, pulseBlocks, queuedKeys);
                AABB damageBox = new AABB(impact.x - strikeRadius - 2.0D, impact.y - 8.0D, impact.z - strikeRadius - 2.0D,
                        impact.x + strikeRadius + 2.0D, impact.y + 14.0D, impact.z + strikeRadius + 2.0D);
                DestructionTerrainManager.damageAndPush(this.level, caster, damageBox, impact, 24.0F, 1.6D, 0.95D);
                DestructionTerrainManager.clearLooseEntities(this.level, caster, damageBox);
                this.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, impact.x, impact.y + 0.4D, impact.z, 72, strikeRadius * 0.55D, 1.4D, strikeRadius * 0.55D, 0.18D);
                if ((this.age + i) % 5 == 0) {
                    this.level.sendParticles(ParticleTypes.FLASH, impact.x, impact.y + 0.8D, impact.z, 1, 0.35D, 0.55D, 0.35D, 0.0D);
                }
            }

            if (!pulseBlocks.isEmpty()) {
                DestructionTerrainManager.clearBlocksInstant(this.level, pulseBlocks, mode, true);
            }

            if ((this.age / this.pulseIntervalTicks) % 2 == 0) {
                this.level.playSound(null, this.origin.x, this.cloudY, this.origin.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 4.2F, 0.5F + this.level.random.nextFloat() * 0.1F);
            }
        }

        this.age++;
        return false;
    }

    private Vec3 sampleImpactPoint() {
        double angle = this.level.random.nextDouble() * Math.PI * 2.0D;
        double distance = Math.sqrt(this.level.random.nextDouble()) * this.actualStrikeRadius;
        double x = this.origin.x + Math.cos(angle) * distance;
        double z = this.origin.z + Math.sin(angle) * distance;
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int surfaceY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
        return new Vec3(blockX + 0.5D, surfaceY + 0.5D, blockZ + 0.5D);
    }

    private Vec3 sampleCloudAnchor(Vec3 impact) {
        Vec3 toImpact = impact.subtract(this.origin);
        Vec3 horizontal = new Vec3(toImpact.x, 0.0D, toImpact.z);
        if (horizontal.lengthSqr() < 1.0E-4D) {
            horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            horizontal = horizontal.normalize();
        }
        double canopyDistance = Math.min(this.radius, impact.distanceTo(this.origin) * (0.72D + this.level.random.nextDouble() * 0.42D));
        Vec3 lateral = new Vec3(-horizontal.z, 0.0D, horizontal.x).scale((this.level.random.nextDouble() - 0.5D) * this.radius * 0.18D);
        return this.origin.add(horizontal.scale(canopyDistance)).add(lateral).add(0.0D, this.cloudY - this.origin.y + (this.level.random.nextDouble() - 0.5D) * 8.0D, 0.0D);
    }
}

