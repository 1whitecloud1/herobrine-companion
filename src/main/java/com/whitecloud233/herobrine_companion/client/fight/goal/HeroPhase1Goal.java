package com.whitecloud233.herobrine_companion.client.fight.goal;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.fight.HeroAfterimage;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.PaleLightningArcPacket;
import com.whitecloud233.herobrine_companion.network.PaleLightningPacket;
import com.whitecloud233.herobrine_companion.util.EndRingContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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

/**
 * 挑战模式第一阶段战斗目标,与 1.20.1 行为对齐:
 * Hero 悬停施法,风暴期用"冲刺移动"切入侧翼/后方(velocity 冲刺 + 残影拖尾),
 * 不再使用 1.21.1 重写版的"瞬移"。
 */
public class HeroPhase1Goal extends Goal {
    private static final double MOVE_PRESSURE_DISTANCE_SQR = 8.0D * 8.0D;
    private static final double MOVE_MIN_DISTANCE = 6.0D;
    private static final double MOVE_MAX_DISTANCE = 10.5D;
    private static final double MOVE_ARENA_RADIUS = 42.0D;
    private static final int AFTERIMAGE_MAX_TICKS = 10;
    private static final int AFTERIMAGE_INTERVAL = 1;

    private static final int[] MOVE_DURATIONS = {26, 20, 14};
    private static final int[] MOVE_COOLDOWNS = {120, 80, 52};
    private static final int[] MOVE_PRESSURE_COOLDOWNS = {80, 52, 36};

    private final HeroEntity hero;
    private LivingEntity target;
    private int phaseTicks;
    private int movementCooldown;
    private Vec3 moveTarget;
    private int moveTicks;
    private int moveDuration;
    private int afterimageIntervalTicks;
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
        this.movementCooldown = 0;
        this.moveTarget = null;
        this.moveTicks = 0;
        this.moveDuration = 0;
        this.afterimageIntervalTicks = 0;
        this.hero.clearChallengeAfterimages();
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
            if (this.moveTarget != null) {
                tickCombatMove(serverLevel);
            } else {
                maybeStartMovementDuringStorm(serverLevel);
                if (this.moveTarget != null) {
                    tickCombatMove(serverLevel);
                }
            }
        }

        if (this.moveTarget == null) {
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
        }

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

    private void maybeStartMovementDuringStorm(ServerLevel level) {
        if (this.movementCooldown > 0) {
            this.movementCooldown--;
        }
        if (this.target == null || this.phaseTicks <= 30 || this.movementCooldown > 0) {
            return;
        }

        boolean pressuredByMelee = horizontalDistanceSqr(this.hero.position(), this.target.position()) <= MOVE_PRESSURE_DISTANCE_SQR;
        boolean stormReposition = this.phaseTicks >= 42
                && (this.phaseTicks % 18 == 0 || (this.pendingStrikes.size() >= 3 && this.phaseTicks % 12 == 0));
        if (!pressuredByMelee && !stormReposition) {
            return;
        }

        if (tryStartCombatMove(level, pressuredByMelee)) {
            this.movementCooldown = difficultyMoveCooldown(pressuredByMelee);
        }
    }

    private boolean tryStartCombatMove(ServerLevel level, boolean pressuredByMelee) {
        float baseYaw = this.target.getYRot();
        float[] angleCandidates = pressuredByMelee
                ? new float[]{160.0F, -160.0F, 125.0F, -125.0F, 180.0F, 95.0F, -95.0F}
                : new float[]{120.0F, -120.0F, 155.0F, -155.0F, 90.0F, -90.0F, 180.0F};

        for (float angleOffset : angleCandidates) {
            double distance = pressuredByMelee
                    ? MOVE_MAX_DISTANCE - level.random.nextDouble() * 1.5D
                    : MOVE_MIN_DISTANCE + level.random.nextDouble() * (MOVE_MAX_DISTANCE - MOVE_MIN_DISTANCE);
            float jitter = (level.random.nextFloat() - 0.5F) * 18.0F;
            Vec3 offset = Vec3.directionFromRotation(0.0F, baseYaw + angleOffset + jitter).scale(distance);
            Vec3 targetPos = clampToArena(this.target.getX() + offset.x, this.target.getZ() + offset.z);
            double y = desiredMoveY(level, targetPos.x, targetPos.z);
            if (canMoveTo(level, targetPos.x, y, targetPos.z)) {
                startCombatMove(level, targetPos.x, y, targetPos.z);
                return true;
            }
        }

        for (int i = 0; i < 6; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = Mth.lerp(level.random.nextDouble(), MOVE_MIN_DISTANCE, MOVE_MAX_DISTANCE);
            Vec3 targetPos = clampToArena(
                    this.target.getX() + Math.cos(angle) * distance,
                    this.target.getZ() + Math.sin(angle) * distance
            );
            double y = desiredMoveY(level, targetPos.x, targetPos.z);
            if (canMoveTo(level, targetPos.x, y, targetPos.z)) {
                startCombatMove(level, targetPos.x, y, targetPos.z);
                return true;
            }
        }
        return false;
    }

    private double desiredMoveY(ServerLevel level, double x, double z) {
        int arenaFloor = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        double hoverBase = Math.max(this.targetHoverY - 1.0D, arenaFloor + 4.0D);
        double relativeBase = this.target != null ? Math.max(hoverBase, this.target.getY() + 3.0D) : hoverBase;
        return Mth.clamp(relativeBase + (level.random.nextDouble() - 0.5D) * 1.5D, hoverBase, this.targetHoverY + 2.5D);
    }

    private Vec3 clampToArena(double x, double z) {
        double dx = x - EndRingContext.CENTER_X;
        double dz = z - EndRingContext.CENTER_Z;
        double distanceSq = dx * dx + dz * dz;
        if (distanceSq <= MOVE_ARENA_RADIUS * MOVE_ARENA_RADIUS) {
            return new Vec3(x, this.targetHoverY, z);
        }

        double scale = MOVE_ARENA_RADIUS / Math.sqrt(distanceSq);
        return new Vec3(
                EndRingContext.CENTER_X + dx * scale,
                this.targetHoverY,
                EndRingContext.CENTER_Z + dz * scale
        );
    }

    private boolean canMoveTo(ServerLevel level, double x, double y, double z) {
        if (!canStandAt(level, x, y, z)) {
            return false;
        }

        Vec3 start = this.hero.position();
        Vec3 end = new Vec3(x, y, z);
        double distance = start.distanceTo(end);
        int samples = Math.max(2, Mth.ceil(distance / 2.0D));
        for (int i = 1; i < samples; i++) {
            Vec3 sample = start.lerp(end, i / (double) samples);
            if (!canStandAt(level, sample.x, sample.y, sample.z)) {
                return false;
            }
        }
        return true;
    }

    private boolean canStandAt(ServerLevel level, double x, double y, double z) {
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

    private void startCombatMove(ServerLevel level, double x, double y, double z) {
        Vec3 oldPos = this.hero.position();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, oldPos.x, oldPos.y + 1.2D, oldPos.z, 14, 0.45D, 0.8D, 0.45D, 0.04D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, oldPos.x, oldPos.y + 1.3D, oldPos.z, 10, 0.35D, 0.6D, 0.35D, 0.08D);

        this.moveTarget = new Vec3(x, y, z);
        this.moveTicks = 0;
        this.moveDuration = difficultyMoveDuration();
        this.afterimageIntervalTicks = AFTERIMAGE_INTERVAL - 1;
        this.hero.getNavigation().stop();
        this.hero.setDeltaMovement(Vec3.ZERO);
        this.hero.lookAt(this.target, 180.0F, 180.0F);
    }

    private void tickCombatMove(ServerLevel level) {
        if (this.moveTarget == null) {
            return;
        }

        this.moveTicks++;
        Vec3 remaining = this.moveTarget.subtract(this.hero.position());
        if (this.moveTicks >= this.moveDuration || remaining.lengthSqr() <= 0.01D) {
            finishCombatMove(level);
            return;
        }

        int ticksLeft = Math.max(1, this.moveDuration - this.moveTicks + 1);
        Vec3 step = remaining.scale(1.0D / ticksLeft);
        this.hero.setDeltaMovement(step);
        this.hero.getNavigation().stop();
        if (this.target != null) {
            this.hero.lookAt(this.target, 45.0F, 45.0F);
        }

        this.afterimageIntervalTicks++;
        if (this.afterimageIntervalTicks >= AFTERIMAGE_INTERVAL) {
            this.afterimageIntervalTicks = 0;
            this.hero.addChallengeAfterimage(HeroAfterimage.of(this.hero, AFTERIMAGE_MAX_TICKS));
        }
    }

    private void finishCombatMove(ServerLevel level) {
        Vec3 endPos = this.hero.position();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, endPos.x, endPos.y + 1.3D, endPos.z, 14, 0.45D, 0.8D, 0.45D, 0.10D);

        this.hero.addChallengeAfterimage(HeroAfterimage.of(this.hero, AFTERIMAGE_MAX_TICKS));
        this.hero.getNavigation().stop();
        this.hero.setDeltaMovement(0.0D, 0.02D, 0.0D);
        if (this.target != null) {
            this.hero.lookAt(this.target, 180.0F, 180.0F);
        }

        this.moveTarget = null;
        this.moveTicks = 0;
        this.moveDuration = 0;
        this.afterimageIntervalTicks = 0;
    }

    private int challengeMode() {
        if (this.hero.getPersistentData().contains("ChallengeMode")) {
            return Math.max(0, Math.min(2, this.hero.getPersistentData().getInt("ChallengeMode")));
        }

        float multiplier = this.hero.getPersistentData().contains("ChallengeDamageMultiplier")
                ? this.hero.getPersistentData().getFloat("ChallengeDamageMultiplier")
                : 1.0F;
        return multiplier >= 1.8F ? 2 : multiplier <= 0.6F ? 0 : 1;
    }

    private int difficultyMoveDuration() {
        return MOVE_DURATIONS[challengeMode()];
    }

    private int difficultyMoveCooldown(boolean pressuredByMelee) {
        int mode = challengeMode();
        return pressuredByMelee ? MOVE_PRESSURE_COOLDOWNS[mode] : MOVE_COOLDOWNS[mode];
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
            double distanceTo = dx * dx + dz * dz;
            if (distanceTo > radius * radius) {
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
        this.movementCooldown = 0;
        this.moveTarget = null;
        this.moveTicks = 0;
        this.moveDuration = 0;
        this.afterimageIntervalTicks = 0;
        this.hero.clearChallengeAfterimages();
    }
}