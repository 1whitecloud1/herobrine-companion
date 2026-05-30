package com.whitecloud233.modid.herobrine_companion.client.fight.goal;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.PaleLightningArcPacket;
import com.whitecloud233.modid.herobrine_companion.network.PaleLightningPacket;
import com.whitecloud233.modid.herobrine_companion.util.EndRingContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

public class HeroPhase1Goal extends Goal {
    private static final double TELEPORT_PRESSURE_DISTANCE_SQR = 8.0D * 8.0D;
    private static final double TELEPORT_MIN_DISTANCE = 6.0D;
    private static final double TELEPORT_MAX_DISTANCE = 10.5D;
    private static final double TELEPORT_ARENA_RADIUS = 42.0D;
    private static final int TELEPORT_BASE_COOLDOWN = 18;
    private static final int TELEPORT_PRESSURE_COOLDOWN = 10;

    private final HeroEntity hero;
    private LivingEntity target;
    private int phaseTicks;
    private int teleportCooldown;
    private double targetHoverY = 112.0D;

    private final List<PendingStrike> pendingStrikes = new ArrayList<>();

    private static class PendingStrike {
        final Vec3 pos;
        int ticksLeft;

        PendingStrike(Vec3 pos, int ticksLeft) {
            this.pos = pos;
            this.ticksLeft = ticksLeft;
        }
    }

    public HeroPhase1Goal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return this.hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)
                || this.hero.getPersistentData().getBoolean("IsChallengeActive");
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.phaseTicks = this.hero.getPersistentData().getInt("ChallengePhaseTicks");
        this.teleportCooldown = 0;
    }

    @Override
    public void tick() {
        if (this.hero.level() instanceof ServerLevel serverLevel) {
            Iterator<PendingStrike> iterator = this.pendingStrikes.iterator();
            while (iterator.hasNext()) {
                PendingStrike strike = iterator.next();
                strike.ticksLeft--;
                if (strike.ticksLeft <= 0) {
                    dealLightningDamage(serverLevel, strike.pos);
                    iterator.remove();
                }
            }
        }

        if (this.target == null || !this.target.isAlive() || this.target.isRemoved()) {
            Player nearestPlayer = this.hero.level().getNearestPlayer(this.hero, 100.0D);
            if (nearestPlayer != null && nearestPlayer.getPersistentData().getBoolean("IsChallengeActive")) {
                this.target = nearestPlayer;
            } else {
                hoverIdle();
                return;
            }
        }

        this.phaseTicks++;
        this.hero.getPersistentData().putInt("ChallengePhaseTicks", this.phaseTicks);
        this.hero.getEntityData().set(HeroEntity.CHALLENGE_TICKS, this.phaseTicks);
        this.hero.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (this.hero.level() instanceof ServerLevel serverLevel) {
            maybeTeleportDuringStorm(serverLevel);
        }

        double currentY = this.hero.getY();
        double dy;
        if (currentY < this.targetHoverY) {
            dy = 0.08D;
        } else if (currentY > this.targetHoverY + 0.2D) {
            dy = -0.04D;
        } else {
            dy = Math.sin(this.phaseTicks * 0.05D) * 0.02D;
        }
        this.hero.setDeltaMovement(0.0D, dy, 0.0D);

        if (this.hero.level() instanceof ServerLevel serverLevel) {
            if (this.phaseTicks > 30 && this.phaseTicks % 6 == 0) {
                int count = 2 + serverLevel.getRandom().nextInt(2);
                for (int i = 0; i < count; i++) {
                    castPaleLightningPillar();
                }
            }
            if (this.phaseTicks > 30 && this.phaseTicks % 3 == 0) {
                castIndependentLightningWeb();
            }
        }
    }

    private void hoverIdle() {
        double currentY = this.hero.getY();
        double dy = 0.0D;
        if (currentY < this.targetHoverY) {
            dy = 0.08D;
        } else if (currentY > this.targetHoverY + 0.2D) {
            dy = -0.04D;
        }
        this.hero.setDeltaMovement(0.0D, dy, 0.0D);
    }

    private void maybeTeleportDuringStorm(ServerLevel level) {
        if (this.teleportCooldown > 0) {
            this.teleportCooldown--;
        }
        if (this.target == null || this.phaseTicks <= 30 || this.teleportCooldown > 0) {
            return;
        }

        boolean pressuredByMelee = horizontalDistanceSqr(this.hero.position(), this.target.position()) <= TELEPORT_PRESSURE_DISTANCE_SQR;
        boolean stormReposition = this.phaseTicks >= 42
                && (this.phaseTicks % 18 == 0 || (this.pendingStrikes.size() >= 3 && this.phaseTicks % 12 == 0));
        if (!pressuredByMelee && !stormReposition) {
            return;
        }

        if (tryCombatTeleport(level, pressuredByMelee)) {
            this.teleportCooldown = pressuredByMelee ? TELEPORT_PRESSURE_COOLDOWN : TELEPORT_BASE_COOLDOWN;
        }
    }

    private boolean tryCombatTeleport(ServerLevel level, boolean pressuredByMelee) {
        float baseYaw = this.target.getYRot();
        float[] angleCandidates = pressuredByMelee
                ? new float[]{160.0F, -160.0F, 125.0F, -125.0F, 180.0F, 95.0F, -95.0F}
                : new float[]{120.0F, -120.0F, 155.0F, -155.0F, 90.0F, -90.0F, 180.0F};

        for (float angleOffset : angleCandidates) {
            double distance = pressuredByMelee
                    ? TELEPORT_MAX_DISTANCE - level.random.nextDouble() * 1.5D
                    : TELEPORT_MIN_DISTANCE + level.random.nextDouble() * (TELEPORT_MAX_DISTANCE - TELEPORT_MIN_DISTANCE);
            float jitter = (level.random.nextFloat() - 0.5F) * 18.0F;
            Vec3 offset = Vec3.directionFromRotation(0.0F, baseYaw + angleOffset + jitter).scale(distance);
            Vec3 targetPos = clampToArena(this.target.getX() + offset.x, this.target.getZ() + offset.z);
            double y = desiredTeleportY(level, targetPos.x, targetPos.z);
            if (canTeleportTo(level, targetPos.x, y, targetPos.z)) {
                doCombatTeleport(level, targetPos.x, y, targetPos.z);
                return true;
            }
        }

        for (int i = 0; i < 6; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = Mth.lerp(level.random.nextDouble(), TELEPORT_MIN_DISTANCE, TELEPORT_MAX_DISTANCE);
            Vec3 targetPos = clampToArena(
                    this.target.getX() + Math.cos(angle) * distance,
                    this.target.getZ() + Math.sin(angle) * distance
            );
            double y = desiredTeleportY(level, targetPos.x, targetPos.z);
            if (canTeleportTo(level, targetPos.x, y, targetPos.z)) {
                doCombatTeleport(level, targetPos.x, y, targetPos.z);
                return true;
            }
        }
        return false;
    }

    private double desiredTeleportY(ServerLevel level, double x, double z) {
        int arenaFloor = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        double hoverBase = Math.max(this.targetHoverY - 1.0D, arenaFloor + 4.0D);
        double relativeBase = this.target != null ? Math.max(hoverBase, this.target.getY() + 3.0D) : hoverBase;
        return Mth.clamp(relativeBase + (level.random.nextDouble() - 0.5D) * 1.5D, hoverBase, this.targetHoverY + 2.5D);
    }

    private Vec3 clampToArena(double x, double z) {
        double dx = x - EndRingContext.CENTER_X;
        double dz = z - EndRingContext.CENTER_Z;
        double distanceSq = dx * dx + dz * dz;
        if (distanceSq <= TELEPORT_ARENA_RADIUS * TELEPORT_ARENA_RADIUS) {
            return new Vec3(x, this.targetHoverY, z);
        }

        double scale = TELEPORT_ARENA_RADIUS / Math.sqrt(distanceSq);
        return new Vec3(
                EndRingContext.CENTER_X + dx * scale,
                this.targetHoverY,
                EndRingContext.CENTER_Z + dz * scale
        );
    }

    private boolean canTeleportTo(ServerLevel level, double x, double y, double z) {
        BlockPos feetPos = BlockPos.containing(x, y, z);
        if (!level.getWorldBorder().isWithinBounds(feetPos)) {
            return false;
        }
        if (!level.isEmptyBlock(feetPos) || !level.isEmptyBlock(feetPos.above()) || !level.isEmptyBlock(feetPos.above(2))) {
            return false;
        }

        AABB movedBox = this.hero.getBoundingBox().move(x - this.hero.getX(), y - this.hero.getY(), z - this.hero.getZ());
        return level.noCollision(this.hero, movedBox);
    }

    private void doCombatTeleport(ServerLevel level, double x, double y, double z) {
        Vec3 oldPos = this.hero.position();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, oldPos.x, oldPos.y + 1.2D, oldPos.z, 18, 0.45D, 0.8D, 0.45D, 0.04D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, oldPos.x, oldPos.y + 1.3D, oldPos.z, 12, 0.35D, 0.6D, 0.35D, 0.08D);

        this.hero.teleportTo(x, y, z);
        this.hero.getNavigation().stop();
        this.hero.setDeltaMovement(0.0D, 0.02D, 0.0D);
        this.hero.lookAt(this.target, 180.0F, 180.0F);

        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1.2D, z, 24, 0.55D, 0.9D, 0.55D, 0.05D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 1.3D, z, 18, 0.45D, 0.8D, 0.45D, 0.10D);
        level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.6F, 0.65F + level.random.nextFloat() * 0.15F);
    }

    private double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private void castPaleLightningPillar() {
        if (!(this.hero.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 center = this.target != null ? this.target.position() : this.hero.position();
        double spreadRadius = 15.0D;
        double angle = serverLevel.random.nextDouble() * Math.PI * 2.0D;
        double distance = Math.sqrt(serverLevel.random.nextDouble()) * spreadRadius;

        double targetX = center.x + Math.cos(angle) * distance;
        double targetZ = center.z + Math.sin(angle) * distance;

        double groundY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) targetX, (int) targetZ);
        if (groundY < serverLevel.getMinBuildHeight()) {
            groundY = center.y;
        }

        PaleLightningPacket packet = new PaleLightningPacket(targetX, groundY, targetZ, 6.0F);
        PacketHandler.sendToTracking(packet, this.hero);

        Vec3 groundPos = new Vec3(targetX, groundY, targetZ);
        this.pendingStrikes.add(new PendingStrike(groundPos, 17));
    }

    private void dealLightningDamage(ServerLevel level, Vec3 strikePos) {
        double radius = 3.0D;
        AABB searchBox = new AABB(
                strikePos.x - radius - 1.0D, strikePos.y - 10.0D, strikePos.z - radius - 1.0D,
                strikePos.x + radius + 1.0D, strikePos.y + 20.0D, strikePos.z + radius + 1.0D
        );

        level.sendParticles(ParticleTypes.FLAME, strikePos.x, strikePos.y + 0.1D, strikePos.z, 15, 0.0D, 0.0D, 0.0D, 0.01D);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox);
        float damageMultiplier = this.hero.getPersistentData().contains("ChallengeDamageMultiplier")
                ? this.hero.getPersistentData().getFloat("ChallengeDamageMultiplier")
                : 1.0F;
        float finalDamage = 10.0F * damageMultiplier;

        for (LivingEntity entity : targets) {
            if (entity == this.hero) {
                continue;
            }

            double dx = entity.getX() - strikePos.x;
            double dz = entity.getZ() - strikePos.z;
            double distanceSq = dx * dx + dz * dz;
            if (distanceSq > radius * radius) {
                continue;
            }

            DamageSource source = level.damageSources().magic();
            entity.hurt(source, finalDamage);

            Vec3 knockbackDir = entity.position().subtract(strikePos);
            if (knockbackDir.lengthSqr() < 1.0E-4D) {
                knockbackDir = new Vec3(0.0D, 0.0D, 1.0D);
            } else {
                knockbackDir = knockbackDir.normalize();
            }
            entity.push(knockbackDir.x * 0.5D, 0.4D, knockbackDir.z * 0.5D);
        }
    }

    private void castIndependentLightningWeb() {
        if (!(this.hero.level() instanceof ServerLevel)) {
            return;
        }

        double handX = this.hero.getX() - Math.sin(Math.toRadians(this.hero.yBodyRot + 45.0D)) * 0.6D;
        double handY = this.hero.getY() + 1.6D;
        double handZ = this.hero.getZ() + Math.cos(Math.toRadians(this.hero.yBodyRot + 45.0D)) * 0.6D;
        Vec3 handPos = new Vec3(handX, handY, handZ);

        int webNodes = 5;
        for (int i = 0; i < webNodes; i++) {
            double timeAngle = (this.phaseTicks + i * (360.0D / webNodes)) * Math.PI / 180.0D;
            double radius = 1.5D + Math.sin(this.phaseTicks * 0.1D) * 0.5D;

            double endX = handPos.x + Math.cos(timeAngle) * radius;
            double endY = handPos.y + Math.sin(timeAngle * 2.0D) * radius;
            double endZ = handPos.z + Math.sin(timeAngle) * radius;
            Vec3 endPos = new Vec3(endX, endY, endZ);

            PaleLightningArcPacket packet = new PaleLightningArcPacket(handPos, endPos);
            PacketHandler.sendToTracking(packet, this.hero);
        }
    }

    @Override
    public void stop() {
        this.target = null;
        this.teleportCooldown = 0;
    }
}
