package com.whitecloud233.modid.herobrine_companion.client.model;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class HeroModel extends PlayerModel<HeroEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation(HerobrineCompanion.MODID, "hero"), "main");

    public HeroModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(HeroEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // [新增] 如果正在播放抚摸镰刀动画，则执行专用逻辑并返回
        if (entity.isInspectingScythe()) {
            setupScytheInspectAnim(entity, ageInTicks);
            return;
        }

        // [新增] 如果正在播放调试动画，则执行专用逻辑并返回
        if (entity.isDebugAnim()) {
            setupDebugAnim(entity, ageInTicks);
            return;
        }

        // [新增] 如果正在播放雷电召唤动画，则执行专用逻辑并返回
        if (entity.isCastingThunder()) {
            setupThunderAnim(entity, ageInTicks);
            return;
        }


        float partialTick = Minecraft.getInstance().getPartialTick();
        float floatAmount = entity.getFloatingAmount(partialTick);

        float headTilt = Mth.sin(ageInTicks * 0.05f) * 0.05f;
        headTilt += (netHeadYaw * 0.01f) * 0.2f;
        this.head.zRot = headTilt;
        this.hat.zRot = this.head.zRot;

        float walkBodyY = 0.0F;
        float walkBodyXRot = this.body.xRot;

        float floatBodyY = -2.0F + Mth.sin(ageInTicks * 0.1f) * 3.0F;

        float floatBodyXRot = 0.05F;

        float legLag = Mth.cos(ageInTicks * 0.1f) * 0.1f;
        float floatRightLegX = 0.3f + legLag;
        float floatLeftLegX = 0.2f + legLag * 0.8f;
        float floatLegZ = 0.05f;

        float armBreath = Mth.sin(ageInTicks * 0.06f) * 0.1f;
        float floatRightArmZ = 0.2f + armBreath;
        float floatLeftArmZ = -0.2f - armBreath;
        float floatArmX = -0.2f + armBreath * 0.5f;

        if (!entity.isCrouching()) {
            this.body.y = Mth.lerp(floatAmount, walkBodyY, floatBodyY);

            this.body.xRot = Mth.lerp(floatAmount, walkBodyXRot, floatBodyXRot);
            this.body.yRot = Mth.lerp(floatAmount, this.body.yRot, 0.0F);

            this.head.y = this.body.y;
            this.rightArm.y = 2.0F + this.body.y;
            this.leftArm.y = 2.0F + this.body.y;
            this.rightLeg.y = 12.0F + this.body.y;
            this.leftLeg.y = 12.0F + this.body.y;

            this.jacket.y = this.body.y;
            this.hat.y = this.head.y;
            this.rightSleeve.y = this.rightArm.y;
            this.leftSleeve.y = this.leftArm.y;
            this.rightPants.y = this.rightLeg.y;
            this.leftPants.y = this.leftLeg.y;
        }

        this.rightLeg.xRot = Mth.lerp(floatAmount, this.rightLeg.xRot, floatRightLegX);
        this.leftLeg.xRot = Mth.lerp(floatAmount, this.leftLeg.xRot, floatLeftLegX);

        this.rightLeg.yRot = Mth.lerp(floatAmount, this.rightLeg.yRot, 0.0F);
        this.leftLeg.yRot = Mth.lerp(floatAmount, this.leftLeg.yRot, 0.0F);

        this.rightLeg.zRot = Mth.lerp(floatAmount, 0.0F, floatLegZ);
        this.leftLeg.zRot = Mth.lerp(floatAmount, 0.0F, -floatLegZ);

        float walkRightArmZ = 0.0F;
        float walkLeftArmZ = 0.0F;

        if (this.attackTime <= 0 && this.rightArmPose == ArmPose.EMPTY) {
            this.rightArm.xRot = Mth.lerp(floatAmount, this.rightArm.xRot, floatArmX);
            this.rightArm.zRot = Mth.lerp(floatAmount, walkRightArmZ, floatRightArmZ);
        }
        if (this.attackTime <= 0 && this.leftArmPose == ArmPose.EMPTY) {
            this.leftArm.xRot = Mth.lerp(floatAmount, this.leftArm.xRot, floatArmX);
            this.leftArm.zRot = Mth.lerp(floatAmount, walkLeftArmZ, floatLeftArmZ);
        }

        // [新增] 挂载挑战模式专属的独立动画系统 (注意确保你的包路径与这里一致)
        com.whitecloud233.modid.herobrine_companion.client.fight.animation.HeroChallengeAnimations.setupChallengeAnims(this, entity, ageInTicks);

        copyAllModelProperties();
    }

    private void setupScytheInspectAnim(HeroEntity entity, float ageInTicks) {
        // --- 1. 新的淡入淡出逻辑 ---
        float animTimer = entity.scytheAnimTick;
        float totalDuration = 160.0F;
        float fadeDuration = 20.0F; // 淡入淡出各 20 tick (1秒)，更平滑
        float blend = 0.0F;

        if (animTimer > (totalDuration - fadeDuration)) {
            blend = (totalDuration - animTimer) / fadeDuration;
        } else if (animTimer < fadeDuration) {
            blend = animTimer / fadeDuration;
        } else {
            blend = 1.0F;
        }

        // --- 2. 动作调整 ---

        // 右手 (持镰手) - 使用新的“黄金数值”
        this.rightArm.xRot = Mth.lerp(blend, this.rightArm.xRot, -1.4F);
        this.rightArm.yRot = Mth.lerp(blend, this.rightArm.yRot, -0.4F);
        this.rightArm.zRot = Mth.lerp(blend, this.rightArm.zRot, -0.3F);

        // 左手 (抚摸手) - 减慢速度，增加幅度
        float strokeSpeed = 0.08F;
        float strokeRange = 0.4F;
        float strokeMotion = Mth.cos(ageInTicks * strokeSpeed) * strokeRange;

        float baseLeftArmX = -1.35F;
        float baseLeftArmY = 0.5F;
        float baseLeftArmZ = 0.2F;

        this.leftArm.xRot = Mth.lerp(blend, this.leftArm.xRot, baseLeftArmX);
        this.leftArm.yRot = Mth.lerp(blend, this.leftArm.yRot, baseLeftArmY + strokeMotion);
        this.leftArm.zRot = Mth.lerp(blend, this.leftArm.zRot, baseLeftArmZ + (strokeMotion * 0.1F));

        // 头部 - 增加眼神跟随细节
        float headTracking = strokeMotion * 0.3F;
        this.head.xRot = Mth.lerp(blend, this.head.xRot, 0.5F); // 低头
        this.head.yRot = Mth.lerp(blend, this.head.yRot, headTracking); // 左右看

        // 最后同步所有属性
        copyAllModelProperties();
    }

    private void setupDebugAnim(HeroEntity entity, float ageInTicks) {
        // 计算混合因子 (Blend)，同之前的逻辑，为了平滑过渡
        float animTimer = entity.debugAnimTick;
        float blend = 0.0F;
        if (animTimer > 90) blend = (100 - animTimer) / 10.0F;
        else if (animTimer > 10) blend = 1.0F;
        else blend = animTimer / 10.0F;

        if (blend <= 0.01F) return;

        // --- 动作设计 ---

        // 1. 头部：盯着自己的右手
        this.head.xRot = Mth.lerp(blend, this.head.xRot, 0.3F);
        this.head.yRot = Mth.lerp(blend, this.head.yRot, -0.4F);

        // 2. 右臂：抬起姿态 (Holding the screen)
        float baseArmX = -1.5F;
        float baseArmY = -0.5F;

        // 3. 模拟“打字”微动 (Typing Jitter)
        float typeAction = Mth.sin(ageInTicks * 0.8F) * 0.05F;
        if (entity.getRandom().nextFloat() < 0.1F) {
            typeAction += 0.15F;
        }

        // 应用到右臂
        this.rightArm.xRot = Mth.lerp(blend, this.rightArm.xRot, baseArmX + typeAction);
        this.rightArm.yRot = Mth.lerp(blend, this.rightArm.yRot, baseArmY);
        this.rightArm.zRot = Mth.lerp(blend, this.rightArm.zRot, typeAction * 0.5F);

        // 4. 左臂：自然下垂或背在身后
        this.leftArm.xRot = Mth.lerp(blend, this.leftArm.xRot, 0.2F);
        this.leftArm.zRot = Mth.lerp(blend, this.leftArm.zRot, 0.1F);

        // 最后同步所有属性
        copyAllModelProperties();
    }

    private void setupThunderAnim(HeroEntity entity, float ageInTicks) {
        float progress = entity.getThunderProgress(Minecraft.getInstance().getPartialTick());

        // 如果不在动作中，直接返回
        if (progress <= 0.01F) return;

        // --- 1. 计算平滑曲线 (SmoothStep) ---
        float smooth = progress * progress * (3.0F - 2.0F * progress);

        // --- 2. 肢体动作 ---

        // A. 右臂：高举擎天
        float targetRightArmX = -3.2F;
        float targetRightArmZ = 0.2F;

        this.rightArm.xRot = Mth.lerp(smooth, this.rightArm.xRot, targetRightArmX);
        this.rightArm.zRot = Mth.lerp(smooth, this.rightArm.zRot, targetRightArmZ);
        this.rightArm.yRot = Mth.lerp(smooth, this.rightArm.yRot, 0.0F);

        // B. 左臂：握拳下压
        this.leftArm.xRot = Mth.lerp(smooth, this.leftArm.xRot, 0.8F);
        this.leftArm.zRot = Mth.lerp(smooth, this.leftArm.zRot, -0.4F);

        // C. 头部：仰望天空
        this.head.xRot = Mth.lerp(smooth, this.head.xRot, -1.2F);
        this.head.yRot = Mth.lerp(smooth, this.head.yRot, 0.0F);

        // D. 脊柱/身体：后仰挺胸
        this.body.xRot = Mth.lerp(smooth, this.body.xRot, -0.3F);
        this.body.y = Mth.lerp(smooth, this.body.y, -2.0F);

        // --- 3. 能量过载震颤 (Overload Shake) ---
        if (progress > 0.6F) {
            float shakeIntensity = (progress - 0.6F) * 0.05F;

            float shakeX = Mth.sin(ageInTicks * 2.5F) * shakeIntensity;
            float shakeZ = Mth.cos(ageInTicks * 2.5F) * shakeIntensity;

            this.rightArm.xRot += shakeX * 2.0F;
            this.rightArm.zRot += shakeZ;

            this.head.yRot += shakeX;

            this.body.xRot += shakeX * 0.5F;
        }

        // 最后同步所有属性
        copyAllModelProperties();
    }

    private void copyAllModelProperties() {
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
    }
}