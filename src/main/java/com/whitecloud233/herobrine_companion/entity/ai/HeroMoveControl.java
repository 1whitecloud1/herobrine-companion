package com.whitecloud233.herobrine_companion.entity.ai;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
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
        // === 1. 走路逻辑 (落地状态) ===
        // 如果不是飞行状态，直接交给原版 MoveControl 处理
        // 这能保证走路动画、步声、自动跳跃等行为正常
        if (!hero.isFloating()) {
            super.tick();
            return;
        }

        // === 2. 飞行/下落逻辑 (平滑处理) ===
        if (this.operation == Operation.MOVE_TO) {
            Vec3 targetVec = new Vec3(this.wantedX - this.hero.getX(), this.wantedY - this.hero.getY(), this.wantedZ - this.hero.getZ());
            double distSq = targetVec.lengthSqr();
            
            if (distSq < 0.1D) { 
                this.hero.setDeltaMovement(Vec3.ZERO);
                return;
            } 

            Vec3 desiredVelocity = targetVec.normalize().scale(this.speedModifier);
            Vec3 currentVelocity = this.hero.getDeltaMovement();
            
            // XZ轴平滑 (0.1D)
            double lerpXZ = 0.1D;
            double newX = Mth.lerp(lerpXZ, currentVelocity.x, desiredVelocity.x);
            double newZ = Mth.lerp(lerpXZ, currentVelocity.z, desiredVelocity.z);
            
            // Y轴极度平滑 (0.03D)
            // 这种慢速插值会让 Hero 落地前有一段明显的减速缓冲，像气垫一样，直到 AI Goal 判定落地
            double lerpY = 0.03D; 
            
            // 如果垂直距离很大，加速反应以免跟丢
            if (Math.abs(targetVec.y) > 3.0D) {
                lerpY = 0.1D; 
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
                // 注意：LookControl 没有 isHasWanted() 方法，我们通过检查 lookAt 状态来判断
                // 或者直接无条件缓慢复位，如果有 LookControl 覆盖它会自动生效
                
                // 简单方案：直接每 tick 尝试复位，如果 LookControl 激活，它会在稍后的 tick 中覆盖这个值
                // 但为了保险，我们只在 XRot 偏差较大时才复位
                if (Math.abs(this.hero.getXRot()) > 1.0F) {
                     this.hero.setXRot(rotlerp(this.hero.getXRot(), 0.0F, 5.0F));
                }
            }
            
        } else {
            this.hero.setDeltaMovement(this.hero.getDeltaMovement().scale(0.8D));
        }
    }
}
