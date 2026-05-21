package com.whitecloud233.modid.herobrine_companion.entity.ai.goal;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HeroGodlyCompanionGoal extends Goal {
    private static final double HOVER_HEIGHT_AIR = 2.0D;
    private static final double FOLLOW_DISTANCE = 3.5D;
    private static final int COMBAT_TIMEOUT = 100;

    private final HeroEntity hero;
    private final double speedModifier;
    private Player owner;

    private int teleportCooldown;
    private float randomOffset;
    private float currentOrbitAngle;
    private float targetOrbitAngle;
    private int changePositionTimer;

    public HeroGodlyCompanionGoal(HeroEntity hero, double speed) {
        this.hero = hero;
        this.speedModifier = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean isInCombat(Player player) {
        int currentTick = player.tickCount;
        int lastHurtByMobTime = player.getLastHurtByMobTimestamp();
        int lastHurtMobTime = player.getLastHurtMobTimestamp();

        boolean recentlyHurt = lastHurtByMobTime > 0 && (currentTick - lastHurtByMobTime) < COMBAT_TIMEOUT;
        boolean recentlyAttacked = lastHurtMobTime > 0 && (currentTick - lastHurtMobTime) < COMBAT_TIMEOUT;
        if (!recentlyHurt && !recentlyAttacked) {
            return false;
        }

        LivingEntity attacker = player.getLastHurtByMob();
        LivingEntity target = player.getLastHurtMob();
        boolean hasValidAttacker = attacker != null && attacker.isAlive() && attacker.distanceToSqr(player) < 900.0D;
        boolean hasValidTarget = target != null && target.isAlive() && target.distanceToSqr(player) < 900.0D;
        return hasValidAttacker || hasValidTarget;
    }

    @Override
    public boolean canUse() {
        if (this.hero.isBattleModeActive()) return false;
        if (!this.hero.isCompanionMode()) return false;
        if (this.hero.getTradingPlayer() != null) return false;

        Player player = this.hero.getOwnerUUID() != null ? this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID()) : null;
        if (player == null || !player.isAlive()) return false;

        this.owner = player;
        if (isInCombat(this.owner)) return false;

        return this.hero.distanceToSqr(player) > (2.5D * 2.5D);
    }

    @Override
    public boolean canContinueToUse() {
        if (this.hero.isBattleModeActive()) return false;
        if (!this.hero.isCompanionMode()) return false;
        if (this.hero.getTradingPlayer() != null) return false;
        if (this.owner == null || !this.owner.isAlive()) return false;
        if (isInCombat(this.owner)) return false;

        return this.hero.distanceToSqr(this.owner) > (1.5D * 1.5D);
    }

    @Override
    public void start() {
        this.hero.setFloating(true);
        this.hero.setNoGravity(true);
        this.hero.getNavigation().stop();

        pickNewRandomPosition();
        this.targetOrbitAngle = this.owner.yBodyRot;
        this.currentOrbitAngle = this.targetOrbitAngle;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.hero.setDeltaMovement(Vec3.ZERO);
        syncHeadToBody();
    }

    @Override
    public void tick() {
        double distToOwnerSqr = this.hero.distanceToSqr(this.owner);
        if (distToOwnerSqr > 200.0D) {
            if (this.teleportCooldown-- <= 0) {
                teleportNearOwner();
                this.teleportCooldown = 20;
            }
            return;
        }

        boolean isOwnerMoving = this.owner.getDeltaMovement().horizontalDistanceSqr() > 0.001D;
        if (--this.changePositionTimer <= 0) {
            pickNewRandomPosition();
        }

        updateTargetAngle(isOwnerMoving);
        this.currentOrbitAngle = rotlerp(this.currentOrbitAngle, this.targetOrbitAngle, 1.5F);
        Vec3 targetPos = calculateTargetPos(this.currentOrbitAngle);

        if (!this.hero.isFloating()) this.hero.setFloating(true);
        if (!this.hero.isNoGravity()) this.hero.setNoGravity(true);

        double dx = this.hero.getX() - targetPos.x;
        double dz = this.hero.getZ() - targetPos.z;
        double distHorizontalSqr = dx * dx + dz * dz;
        double heightDiff = this.hero.getY() - targetPos.y;

        double speed = this.speedModifier;
        if (distHorizontalSqr > 25.0D) {
            speed *= 1.5D;
        }

        boolean closeEnoughHorizontally = distHorizontalSqr < 0.0625D;
        boolean closeEnoughVertically = Math.abs(heightDiff) < 0.25D;
        boolean hoveringInPlace = closeEnoughHorizontally && closeEnoughVertically;

        if (hoveringInPlace) {
            this.hero.setDeltaMovement(this.hero.getDeltaMovement().scale(0.5D));
        } else {
            this.hero.getMoveControl().setWantedPosition(targetPos.x, targetPos.y, targetPos.z, speed);
        }

        if (hoveringInPlace && distToOwnerSqr <= 16.0D) {
            this.hero.getLookControl().setLookAt(this.owner, 18.0F, 25.0F);
        } else {
            syncHeadToBody();
        }

        if (this.hero.horizontalCollision || distHorizontalSqr > 0.1D) {
            Vec3 moveDir = this.hero.getDeltaMovement().normalize();
            if (this.hero.getDeltaMovement().lengthSqr() < 0.001D) {
                moveDir = this.hero.getLookAngle();
            }

            net.minecraft.core.BlockPos frontPos = net.minecraft.core.BlockPos.containing(
                    this.hero.getX() + moveDir.x * 0.8D,
                    this.hero.getY() + 0.1D,
                    this.hero.getZ() + moveDir.z * 0.8D
            );

            for (int i = 0; i < 2; i++) {
                net.minecraft.core.BlockPos checkPos = i == 0 ? frontPos : frontPos.above();
                net.minecraft.world.level.block.state.BlockState state = this.hero.level().getBlockState(checkPos);
                if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door
                        && !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)) {
                    door.setOpen(this.hero, this.hero.level(), state, checkPos, true);
                }
            }
        }
    }

    private void updateTargetAngle(boolean isMoving) {
        if (isMoving) {
            Vec3 velocity = this.owner.getDeltaMovement();
            float moveYaw = (float) (Mth.atan2(velocity.z, velocity.x) * (180.0D / Math.PI)) - 90.0F;
            this.targetOrbitAngle = moveYaw + this.randomOffset;
        }
    }

    private Vec3 calculateTargetPos(float angleDegrees) {
        double targetAngleRad = Math.toRadians(angleDegrees);
        double tx = this.owner.getX() + Math.sin(-targetAngleRad) * FOLLOW_DISTANCE;
        double tz = this.owner.getZ() + Math.cos(-targetAngleRad) * FOLLOW_DISTANCE;

        double targetY;
        if (this.owner.getAbilities().flying || !this.owner.onGround()) {
            targetY = this.owner.getY() + HOVER_HEIGHT_AIR;
        } else {
            targetY = this.owner.getY() + 0.5D;
        }
        return new Vec3(tx, targetY, tz);
    }

    private void pickNewRandomPosition() {
        this.randomOffset = this.hero.getRandom().nextFloat() * 360.0F;
        this.changePositionTimer = 400 + this.hero.getRandom().nextInt(200);
    }

    private void teleportNearOwner() {
        this.currentOrbitAngle = this.owner.yBodyRot + this.randomOffset;
        this.targetOrbitAngle = this.currentOrbitAngle;
        Vec3 target = calculateTargetPos(this.currentOrbitAngle);
        this.hero.moveTo(target.x, target.y, target.z, this.hero.getYRot(), this.hero.getXRot());
        this.hero.setDeltaMovement(Vec3.ZERO);
        syncHeadToBody();
    }

    private void syncHeadToBody() {
        float bodyYaw = this.hero.getYRot();
        this.hero.setYHeadRot(bodyYaw);
        this.hero.yHeadRotO = bodyYaw;
    }

    protected float rotlerp(float pStart, float pEnd, float pMaxIncrease) {
        float f = Mth.wrapDegrees(pEnd - pStart);
        if (f > pMaxIncrease) f = pMaxIncrease;
        if (f < -pMaxIncrease) f = -pMaxIncrease;
        return pStart + f;
    }
}
