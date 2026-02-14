package com.whitecloud233.herobrine_companion.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobSubmissionMixin extends LivingEntity {

    protected MobSubmissionMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "serverAiStep", at = @At("TAIL"))
    private void enforceSubmission(CallbackInfo ci) {
        // 检查 NBT 标记
        if (this.getPersistentData().getBoolean("HeroSubmission")) {
            Mob self = (Mob) (Object) this;

            // --- 基础控制 (保持不变) ---
            if (self.getPose() != Pose.CROUCHING) self.setPose(Pose.CROUCHING);
            self.setSprinting(false);
            self.getNavigation().stop();
            self.getMoveControl().setWantedPosition(self.getX(), self.getY(), self.getZ(), 0.0);

            // --- 核心修复：头部旋转锁死 ---

            // 1. 读取 Goal 计算好的“看向 Herobrine”的角度
            if (self.getPersistentData().contains("HeroSubmissionYaw")) {
                float targetYaw = self.getPersistentData().getFloat("HeroSubmissionYaw");

                // 2. 计算战栗抖动
                float jitter = 0.0F;
                if (self.tickCount % 2 == 0) {
                    float shiverIntensity = 0.5F; // 加大抖动力度
                    jitter = (self.getRandom().nextFloat() - 0.5F) * shiverIntensity;
                }

                // 3. 【绝对暴力覆盖】
                // 无论原版 AI (LookAtPlayerGoal) 计算了什么，直接覆盖掉
                // 这里的关键是：必须同时设置 YRot (身体) 和 YHeadRot (头)

                float finalYaw = targetYaw + jitter;

                self.setYRot(finalYaw);      // 身体整体朝向
                self.yBodyRot = finalYaw;    // 身体渲染朝向
                self.yHeadRot = finalYaw;    // 头部渲染朝向 (这就解决了看玩家的问题！)

                // 4. 强制低头 (XRot)
                // 60度是深低头，再加上一点点抖动
                self.setXRot(20.0F + jitter);
                self.xRotO = self.getXRot(); // 修正平滑插值，防止客户端看起来鬼畜
            }

            // --- 锁死跳跃 ---
            if (self.getDeltaMovement().y > 0) {
                self.setDeltaMovement(self.getDeltaMovement().multiply(1, 0, 1));
            }
        }
    }
}