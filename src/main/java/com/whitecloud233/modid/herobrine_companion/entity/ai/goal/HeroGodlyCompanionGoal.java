package com.whitecloud233.modid.herobrine_companion.entity.ai.goal;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HeroGodlyCompanionGoal extends Goal {
    private final HeroEntity hero;
    private final double speedModifier;
    private Player owner;

    // 参数配置
    private static final double HOVER_HEIGHT_AIR = 2.0D;
    private static final double FOLLOW_DISTANCE = 3.5D;
    private static final double LANDING_THRESHOLD = 0.8D;

    // [新增] 战斗超时时间
    private static final int COMBAT_TIMEOUT = 100;

    private int teleportCooldown;

    // 柔性跟随参数
    private float randomOffset;
    private float currentOrbitAngle;
    private float targetOrbitAngle;

    private int changePositionTimer;

    public HeroGodlyCompanionGoal(HeroEntity hero, double speed) {
        this.hero = hero;
        this.speedModifier = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * [新增] 统一的战斗状态判定
     * 与 ObserveAndRescueGoal 和 TeleportGoal 保持绝对一致，防止 AI 撕扯
     */
    private boolean isInCombat(Player player) {
        int currentTick = player.tickCount;
        int lastHurtByMobTime = player.getLastHurtByMobTimestamp();
        int lastHurtMobTime = player.getLastHurtMobTimestamp();

        boolean recentlyHurt = lastHurtByMobTime > 0 && (currentTick - lastHurtByMobTime) < COMBAT_TIMEOUT;
        boolean recentlyAttacked = lastHurtMobTime > 0 && (currentTick - lastHurtMobTime) < COMBAT_TIMEOUT;

        if (!recentlyHurt && !recentlyAttacked) return false;

        LivingEntity attacker = player.getLastHurtByMob();
        LivingEntity target = player.getLastHurtMob();

        boolean hasValidAttacker = attacker != null && attacker.isAlive() && attacker.distanceToSqr(player) < 900.0D;
        boolean hasValidTarget = target != null && target.isAlive() && target.distanceToSqr(player) < 900.0D;

        return hasValidAttacker || hasValidTarget;
    }

    @Override
    public boolean canUse() {
        if (!this.hero.isCompanionMode()) return false;
        // 如果正在交易，禁止跟随移动
        if (this.hero.getTradingPlayer() != null) return false;

        Player player = this.hero.level().getNearestPlayer(this.hero, 64.0D);
        if (player == null) return false;
        this.owner = player;

        // 【关键修复】如果玩家进入战斗，立刻禁用贴身跟随
        // 将身体的控制权完美移交给 HeroObserveAndRescueGoal
        if (isInCombat(this.owner)) return false;

        return this.hero.distanceToSqr(player) > (FOLLOW_DISTANCE * FOLLOW_DISTANCE + 4.0D);
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.hero.isCompanionMode()) return false;
        // 如果正在交易，立即停止跟随
        if (this.hero.getTradingPlayer() != null) return false;
        if (this.owner == null || !this.owner.isAlive()) return false;

        // 【关键修复】如果在日常跟随中突然爆发战斗，立刻打断当前步伐
        if (isInCombat(this.owner)) return false;

        return this.hero.distanceToSqr(this.owner) > (FOLLOW_DISTANCE * FOLLOW_DISTANCE);
    }

    @Override
    public void start() {
        this.hero.setOwnerUUID(this.owner.getUUID());
        // 初始默认飞行，由 tick 修正
        this.hero.setNoGravity(true);
        this.hero.setFloating(true);

        pickNewRandomPosition();
        this.targetOrbitAngle = this.owner.yBodyRot;
        this.currentOrbitAngle = this.targetOrbitAngle;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.hero.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        // === 1. 头部与身体转向修复 ===
        double d0 = this.owner.getX() - this.hero.getX();
        double d1 = this.owner.getZ() - this.hero.getZ();
        double distSqr = d0 * d0 + d1 * d1;

        if (distSqr > 0.1D) {
            float targetYRot = -((float)Mth.atan2(d0, d1)) * (180F / (float)Math.PI);
            this.hero.yBodyRot = rotlerp(this.hero.yBodyRot, targetYRot, 10.0F);
            this.hero.setYRot(this.hero.yBodyRot);
        }

        this.hero.getLookControl().setLookAt(this.owner, 30.0F, 40.0F);

        // 兜底逻辑：如果你跑得太快(超过 20 格)，但又没处于战斗中，偶尔会触发传送
        double distToOwnerSqr = this.hero.distanceToSqr(this.owner);
        if (distToOwnerSqr > 400.0D) {
            if (this.teleportCooldown-- <= 0) {
                teleportNearOwner();
                this.teleportCooldown = 20;
            }
            return;
        }

        boolean isOwnerMoving = this.owner.getDeltaMovement().horizontalDistanceSqr() > 0.001;
        if (!isOwnerMoving && --this.changePositionTimer <= 0) {
            pickNewRandomPosition();
        }

        // 1. 计算角度
        updateTargetAngle(isOwnerMoving);
        this.currentOrbitAngle = rotlerp(this.currentOrbitAngle, this.targetOrbitAngle, 1.5F);

        // 2. 计算目标位置
        Vec3 targetPos = calculateTargetPos(this.currentOrbitAngle);

        // === 3. 核心修复：状态切换判定 ===
        double heightDiff = this.hero.getY() - targetPos.y;
        boolean ownerIsFlying = !this.owner.onGround() && this.owner.getAbilities().flying;

        boolean shouldFly = ownerIsFlying || heightDiff > LANDING_THRESHOLD;

        if (shouldFly != this.hero.isFloating()) {
            this.hero.setFloating(shouldFly);
            this.hero.setNoGravity(shouldFly);
            this.hero.getNavigation().stop();
        }

        // === 4. 移动逻辑修复 ===
        double dx = this.hero.getX() - targetPos.x;
        double dz = this.hero.getZ() - targetPos.z;
        double distHorizontalSqr = dx * dx + dz * dz;

        double speed = this.speedModifier;
        if (distHorizontalSqr > 25.0D) speed *= 1.5D;

        if (this.hero.isFloating()) {
            boolean closeEnoughHorizontally = distHorizontalSqr < 1.0D;
            boolean closeEnoughVertically = Math.abs(heightDiff) < 0.2D;

            if (closeEnoughHorizontally && closeEnoughVertically) {
                this.hero.setDeltaMovement(this.hero.getDeltaMovement().scale(0.6));
            } else {
                this.hero.getMoveControl().setWantedPosition(targetPos.x, targetPos.y, targetPos.z, speed);
            }
        } else {
            if (distHorizontalSqr < 1.5D) {
                this.hero.getNavigation().stop();
            } else {
                this.hero.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, speed);
            }
        }
    }

    private void updateTargetAngle(boolean isMoving) {
        if (isMoving) {
            Vec3 velocity = this.owner.getDeltaMovement();
            float moveYaw = (float)(Mth.atan2(velocity.z, velocity.x) * (180.0D / Math.PI)) - 90.0F;
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
            targetY = this.owner.getY();
            BlockPos blockPos = new BlockPos((int)tx, (int)targetY, (int)tz);
            if (this.hero.level().getBlockState(blockPos).isSolid() &&
                    this.hero.level().getBlockState(blockPos.above()).isSolid()) {
                targetY = this.owner.getY() + 3.0;
            }
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

        this.hero.teleportTo(target.x, target.y, target.z);
        this.hero.setDeltaMovement(Vec3.ZERO);
    }

    protected float rotlerp(float pStart, float pEnd, float pMaxIncrease) {
        float f = Mth.wrapDegrees(pEnd - pStart);
        if (f > pMaxIncrease) f = pMaxIncrease;
        if (f < -pMaxIncrease) f = -pMaxIncrease;
        return pStart + f;
    }
}