package com.whitecloud233.herobrine_companion.entity.ai;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.goal.HeroGodlyCompanionGoal;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

public class HeroMoveControl extends MoveControl {
    private final HeroEntity hero;

    public HeroMoveControl(HeroEntity hero) {
        super(hero);
        this.hero = hero;
    }


    @Override
    public void tick() {
        if (HeroGodlyCompanionGoal.isOwnerWithinStayStillRadius(this.hero)) {
            this.operation = Operation.WAIT;
            this.hero.getNavigation().stop();
            this.hero.setDeltaMovement(Vec3.ZERO);
            return;
        }

        if (!this.hero.isFloating()) {
            super.tick();
            return;
        }

        if (this.operation == Operation.MOVE_TO) {
            Vec3 targetVec = new Vec3(this.wantedX - this.hero.getX(), this.wantedY - this.hero.getY(), this.wantedZ - this.hero.getZ());
            double distSq = targetVec.lengthSqr();

            // 1. 极近点平滑刹车，消除抖动
            if (distSq < 0.5D) {
                this.operation = Operation.WAIT;
                this.hero.setDeltaMovement(this.hero.getDeltaMovement().scale(0.35D));
                return;
            }

            // 2. 动态最大速度
            double maxSpeed = this.speedModifier * 0.9D;
            if (distSq > 16.0D) maxSpeed *= 1.5D; // 远距离加速

            // 3. 【核心防鬼畜：到达转向】
            // 如果距离目标不到一步之遥，强行限制速度为刚好到达的量，防止飞过头
            double actualSpeed = maxSpeed;
            if (distSq < maxSpeed * maxSpeed) {
                actualSpeed = Math.sqrt(distSq);
            }

            Vec3 desiredVelocity = targetVec.normalize().scale(actualSpeed);

            // 4. 【核心动力注入】
            // 抛弃迟缓的 Lerp！直接赋予期望速度，并乘以 1.1 的系数，强行抵消原版引擎的空气阻力扣减
            double comp = this.hero.isCompanionMode() ? 1.35D : 1.8D;
            double newX = desiredVelocity.x * comp;
            double newY = desiredVelocity.y * comp;
            double newZ = desiredVelocity.z * comp;

            // 5. 气垫船越障机制
            if (this.hero.horizontalCollision) {
                newY += 0.5D; // 撞墙时提供升力
            }
            if (this.hero.onGround()) {
                newY += 0.1D; // 脚底擦地时微微抬升
                if (!this.hero.isCompanionMode()) {
                    newX *= 1.5D; // 补偿地面的巨大摩擦力惩罚
                    newZ *= 1.5D;
                }
            }

            // 绝对速度覆盖。陪伴穿墙只允许横向穿墙，向下不能穿过脚下方块。
            Vec3 velocity = protectCompanionFloor(new Vec3(newX, newY, newZ));
            this.hero.setDeltaMovement(velocity.x, velocity.y, velocity.z);

            // === 转向平滑化 ===
            // 主动注视目标时让 LookControl 接管头部/身体朝向，避免移动控制把视线每 tick 拉回。
            if (distSq > 0.25D && shouldFaceMovement()) {
                double d0 = this.wantedX - this.hero.getX();
                double d1 = this.wantedZ - this.hero.getZ();
                float targetYRot = -((float) Mth.atan2(d0, d1)) * (180F / (float) Math.PI);
                this.hero.setYRot(rotlerp(this.hero.getYRot(), targetYRot, 10.0F));
                this.hero.yBodyRot = this.hero.getYRot();

                if (Math.abs(this.hero.getXRot()) > 1.0F) {
                    this.hero.setXRot(rotlerp(this.hero.getXRot(), 0.0F, 5.0F));
                }
            }

        } else {
            // 自由滑行时保留惯性
            this.hero.setDeltaMovement(this.hero.getDeltaMovement().scale(0.95D));
        }
    }

    private boolean shouldFaceMovement() {
        return !this.hero.getLookControl().isLookingAtTarget()
                && this.hero.getTarget() == null
                && !this.hero.isBattleModeActive()
                && this.hero.getInvitedPos() == null;
    }

    public void stopMoving() {
        this.operation = Operation.WAIT;
    }

    public Vec3 protectCompanionFloor(Vec3 velocity) {
        if (!this.hero.isCompanionMode() || !this.hero.noPhysics || velocity.y >= 0.0D) {
            return velocity;
        }

        double clampedY = velocity.y;
        double currentFeetY = this.hero.getY();
        double nextFeetY = currentFeetY + velocity.y;
        net.minecraft.world.phys.AABB box = this.hero.getBoundingBox().deflate(0.05D);

        clampedY = clampDownwardVelocityAtPoint(this.hero.getX(), this.hero.getZ(), currentFeetY, nextFeetY, clampedY);
        clampedY = clampDownwardVelocityAtPoint(box.minX, box.minZ, currentFeetY, nextFeetY, clampedY);
        clampedY = clampDownwardVelocityAtPoint(box.minX, box.maxZ, currentFeetY, nextFeetY, clampedY);
        clampedY = clampDownwardVelocityAtPoint(box.maxX, box.minZ, currentFeetY, nextFeetY, clampedY);
        clampedY = clampDownwardVelocityAtPoint(box.maxX, box.maxZ, currentFeetY, nextFeetY, clampedY);

        return new Vec3(velocity.x, clampedY, velocity.z);
    }

    private double clampDownwardVelocityAtPoint(double x, double z, double currentFeetY, double nextFeetY, double currentVelocityY) {
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, nextFeetY - 0.02D, z);
        net.minecraft.world.level.block.state.BlockState state = this.hero.level().getBlockState(pos);
        net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(this.hero.level(), pos);
        if (shape.isEmpty()) {
            return currentVelocityY;
        }

        double floorTop = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
        if (floorTop >= nextFeetY - 0.02D && floorTop <= currentFeetY + 0.05D) {
            return Math.max(currentVelocityY, floorTop - currentFeetY);
        }
        return currentVelocityY;
    }
}
