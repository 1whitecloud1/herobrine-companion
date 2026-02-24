package com.whitecloud233.modid.herobrine_companion.entity.ai.goal;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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
    
    // [修复] 落地阈值稍微调高一点点，更容易触发
    private static final double LANDING_THRESHOLD = 0.8D; 

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

    @Override
    public boolean canUse() {
        if (!this.hero.isCompanionMode()) return false;
        // [新增] 如果正在交易，禁止跟随移动
        if (this.hero.getTradingPlayer() != null) return false;

        Player player = this.hero.level().getNearestPlayer(this.hero, 64.0D);
        if (player == null) return false;
        this.owner = player;
        return this.hero.distanceToSqr(player) > (FOLLOW_DISTANCE * FOLLOW_DISTANCE + 4.0D);
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.hero.isCompanionMode()) return false;
        // [新增] 如果正在交易，立即停止跟随
        if (this.hero.getTradingPlayer() != null) return false;

        if (this.owner == null || !this.owner.isAlive()) return false;
        // [修复] 当距离足够近时，停止该 Goal，允许 IdleGoal 执行
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
        // 始终让身体面向 Owner，避免身体背对 Owner 时头部受限导致的抽搐
        double d0 = this.owner.getX() - this.hero.getX();
        double d1 = this.owner.getZ() - this.hero.getZ();
        double distSqr = d0 * d0 + d1 * d1;

        // 只有当水平距离足够时才调整身体朝向，防止在正上方/正下方时 atan2 导致的角度跳变
        if (distSqr > 0.1D) {
            float targetYRot = -((float)Mth.atan2(d0, d1)) * (180F / (float)Math.PI);
            // 平滑转动身体，速度适中 (10.0F)
            this.hero.yBodyRot = rotlerp(this.hero.yBodyRot, targetYRot, 10.0F);
            this.hero.setYRot(this.hero.yBodyRot);
        }

        // 头部看向 Owner
        // 既然身体已经转过去了，头部只需要很小的调整
        // 增加垂直转动速度 (40.0F) 以应对高度变化，水平速度 (30.0F) 保持平滑
        this.hero.getLookControl().setLookAt(this.owner, 30.0F, 40.0F);
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
        double heightDiff = this.hero.getY() - targetPos.y; // 正数表示 Hero 在目标上方
        boolean ownerIsFlying = !this.owner.onGround() && this.owner.getAbilities().flying;
        
        // 如果高度差 > 阈值，或者玩家在飞，则保持飞行
        boolean shouldFly = ownerIsFlying || heightDiff > LANDING_THRESHOLD;

        if (shouldFly != this.hero.isFloating()) {
            this.hero.setFloating(shouldFly);
            this.hero.setNoGravity(shouldFly);
            // [重要] 切换状态时立刻重置导航路径，防止旧路径干扰
            this.hero.getNavigation().stop();
        }

        // === 4. 移动逻辑修复 ===
        // 分别计算水平距离和垂直距离，不要混在一起
        double dx = this.hero.getX() - targetPos.x;
        double dz = this.hero.getZ() - targetPos.z;
        double distHorizontalSqr = dx * dx + dz * dz;
        
        double speed = this.speedModifier;
        if (distHorizontalSqr > 25.0D) speed *= 1.5D;

        if (this.hero.isFloating()) {
            // [飞行状态]
            // 只要没对准（无论是水平没对准，还是垂直没对准），就继续移动
            // 修复：之前只判断总距离 < 2.0 就停，导致高度差 1.0 时也停了

            boolean closeEnoughHorizontally = distHorizontalSqr < 1.0D;
            boolean closeEnoughVertically = Math.abs(heightDiff) < 0.2D;

            if (closeEnoughHorizontally && closeEnoughVertically) {
                // 完全到位了才刹车
                this.hero.setDeltaMovement(this.hero.getDeltaMovement().scale(0.6));
            } else {
                // 没到位就继续飞
                this.hero.getMoveControl().setWantedPosition(targetPos.x, targetPos.y, targetPos.z, speed);
            }
        } else {
            // [走路状态]
            // 如果水平距离太近，就停下来（避免原地踏步）
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
            // 目标就是脚底板地面
            targetY = this.owner.getY(); 
            
            BlockPos blockPos = new BlockPos((int)tx, (int)targetY, (int)tz);
            // 只有当目标点真的是实体方块内部时，才抬高
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