package com.whitecloud233.modid.herobrine_companion.client.fight.animation;

import com.whitecloud233.modid.herobrine_companion.client.model.HeroModel;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.util.Mth;

public class HeroChallengeAnimations {

    public static void setupChallengeAnims(HeroModel model, HeroEntity entity, float ageInTicks) {
        // [修改] 检查同步通道中的挑战状态
        if (!entity.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) return;

        // [修改] 直接从同步通道获取进度 tick
        int ticks = entity.getEntityData().get(HeroEntity.CHALLENGE_TICKS);

        if (ticks > 0) {
            // 60 tick (3秒) 内完成抬手
            float armProgress = Math.min(1.0f, ticks / 60.0f);
            float smooth = armProgress * armProgress * (3.0F - 2.0F * armProgress);

            // 目标角度：右臂向前平举，手腕稍微向内 (凝聚雷电的姿态)
            float targetXRot = -1.5F; // 约向上90度
            float targetYRot = -0.3F; // 稍微向内收拢
            float targetZRot = 0.1F;  // 稍微向外张开

            // 与模型原有的手臂动作进行混合 (Lerp)
            model.rightArm.xRot = Mth.lerp(smooth, model.rightArm.xRot, targetXRot);
            model.rightArm.yRot = Mth.lerp(smooth, model.rightArm.yRot, targetYRot);
            model.rightArm.zRot = Mth.lerp(smooth, model.rightArm.zRot, targetZRot);

            // 给抬起的手臂加一点点能量震颤感
            if (armProgress > 0.8F) {
                float jitter = Mth.sin(ageInTicks * 1.5F) * 0.05F;
                model.rightArm.xRot += jitter;
                model.rightArm.zRot += jitter * 0.5F;
            }
        }
    }
}