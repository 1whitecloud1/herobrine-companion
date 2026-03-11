package com.whitecloud233.modid.herobrine_companion.entity.ai;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
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
        // [新增] 1. 地面/行走模式：交给原版逻辑处理
        // 这样能保证受重力影响、能自动跳跃台阶、贴地行走
        if (!this.hero.isFloating()) {
            super.tick();
            return;
        }

        // 2. 飞行/悬浮模式：自定义平滑逻辑
        if (this.operation == Operation.MOVE_TO) {
            Vec3 targetVec = new Vec3(this.wantedX - this.hero.getX(), this.wantedY - this.hero.getY(), this.wantedZ - this.hero.getZ());
            double distSq = targetVec.lengthSqr();
            
            // 极近距离处理 (防止鬼畜)
            if (distSq < 0.1D) { 
                this.hero.setDeltaMovement(Vec3.ZERO);
                return;
            } 

            // 期望速度
            Vec3 desiredVelocity = targetVec.normalize().scale(this.speedModifier);
            Vec3 currentVelocity = this.hero.getDeltaMovement();
            
            // === 分离轴向平滑 ===
            
            // [优化] 动态水平响应系数 (XZ)
            // 基础响应 0.1，距离越远响应越快，防止卡顿（低TPS）导致移动迟缓
            double lerpXZ = 0.1D;
            if (distSq > 4.0D) lerpXZ = 0.3D;   // 距离 > 2格，提升响应
            if (distSq > 16.0D) lerpXZ = 0.8D;  // 距离 > 4格，几乎瞬间响应，抵抗卡顿

            double newX = Mth.lerp(lerpXZ, currentVelocity.x, desiredVelocity.x);
            double newZ = Mth.lerp(lerpXZ, currentVelocity.z, desiredVelocity.z);
            
            // 垂直轴 (Y): 0.03D (响应极慢，营造漂浮感)
            double lerpY = 0.03D; 
            
            // 如果距离很远(>5格)，Y轴加速响应
            if (Math.abs(targetVec.y) > 5.0D) {
                lerpY = 0.2D; 
            }
            
            double newY = Mth.lerp(lerpY, currentVelocity.y, desiredVelocity.y);

            this.hero.setDeltaMovement(newX, newY, newZ);
            // 转向逻辑
            // [修改] 无论是否在陪伴模式，只要在移动，就允许 MoveControl 调整朝向
            // 但为了避免头部抽搐，我们限制最大旋转速度，并确保身体和头部同步
            if (distSq > 2.25D) {
                double d0 = this.wantedX - this.hero.getX();
                double d1 = this.wantedZ - this.hero.getZ();
                float targetYRot = -((float)Mth.atan2(d0, d1)) * (180F / (float)Math.PI);

                // 平滑旋转身体
                this.hero.setYRot(rotlerp(this.hero.getYRot(), targetYRot, 10.0F));
                this.hero.yBodyRot = this.hero.getYRot();

                // [新增] 强制重置头部垂直角度 (XRot)
                // 如果没有 LookControl 正在工作（例如没有 LookAtPlayerGoal），
                // 那么头部可能会保持之前的角度（例如之前在看地上的东西）。
                // 我们在这里缓慢地将头部抬起，使其平视前方。
                if (Math.abs(this.hero.getXRot()) > 1.0F) {
                    this.hero.setXRot(rotlerp(this.hero.getXRot(), 0.0F, 5.0F));
                }
            }

        } else {
            this.hero.setDeltaMovement(this.hero.getDeltaMovement().scale(0.8D));
        }
    }
}
