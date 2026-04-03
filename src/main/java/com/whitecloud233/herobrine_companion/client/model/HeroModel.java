package com.whitecloud233.herobrine_companion.client.model;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class HeroModel extends PlayerModel<HeroEntity> {

    // 【核心修复 1】使用 fromNamespaceAndPath 替代 new ResourceLocation()
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "hero"), "main");

    public final ModelPart rightArmLower;
    public final ModelPart leftArmLower;
    public final ModelPart rightLegLower;
    public final ModelPart leftLegLower;

    public final ModelPart rightSleeveLower;
    public final ModelPart leftSleeveLower;
    public final ModelPart rightPantsLower;
    public final ModelPart leftPantsLower;

    public HeroModel(ModelPart root, boolean slim) {
        super(root, slim);

        this.rightArmLower = this.rightArm.getChild("right_arm_lower");
        this.leftArmLower = this.leftArm.getChild("left_arm_lower");
        this.rightLegLower = this.rightLeg.getChild("right_leg_lower");
        this.leftLegLower = this.leftLeg.getChild("left_leg_lower");

        this.rightSleeveLower = this.rightSleeve.getChild("right_sleeve_lower");
        this.leftSleeveLower = this.leftSleeve.getChild("left_sleeve_lower");
        this.rightPantsLower = this.rightPants.getChild("right_pants_lower");
        this.leftPantsLower = this.leftPants.getChild("left_pants_lower");
    }

    public static LayerDefinition createBodyLayer(boolean slim) {
        MeshDefinition meshdefinition = PlayerModel.createMesh(CubeDeformation.NONE, slim);
        PartDefinition partdefinition = meshdefinition.getRoot();

        // 1. 右臂
        PartDefinition rightArm = partdefinition.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 6.25F, 4.0F), PartPose.offset(-5.0F, 2.0F, 0.0F));
        rightArm.addOrReplaceChild("right_arm_lower", CubeListBuilder.create().texOffs(40, 22).addBox(-3.0F, -0.25F, -2.0F, 4.0F, 6.25F, 4.0F), PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition rightSleeve = partdefinition.addOrReplaceChild("right_sleeve", CubeListBuilder.create().texOffs(40, 32).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 6.25F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(-5.0F, 2.0F, 0.0F));
        rightSleeve.addOrReplaceChild("right_sleeve_lower", CubeListBuilder.create().texOffs(40, 38).addBox(-3.0F, -0.25F, -2.0F, 4.0F, 6.25F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 4.0F, 0.0F));

        // 2. 左臂
        PartDefinition leftArm = partdefinition.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 6.25F, 4.0F), PartPose.offset(5.0F, 2.0F, 0.0F));
        leftArm.addOrReplaceChild("left_arm_lower", CubeListBuilder.create().texOffs(32, 54).addBox(-1.0F, -0.25F, -2.0F, 4.0F, 6.25F, 4.0F), PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition leftSleeve = partdefinition.addOrReplaceChild("left_sleeve", CubeListBuilder.create().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 6.25F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(5.0F, 2.0F, 0.0F));
        leftSleeve.addOrReplaceChild("left_sleeve_lower", CubeListBuilder.create().texOffs(48, 54).addBox(-1.0F, -0.25F, -2.0F, 4.0F, 6.25F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 4.0F, 0.0F));

        // 3. 右腿
        PartDefinition rightLeg = partdefinition.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.25F, 4.0F), PartPose.offset(-1.9F, 12.0F, 0.0F));
        rightLeg.addOrReplaceChild("right_leg_lower", CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, -0.25F, -2.0F, 4.0F, 6.25F, 4.0F), PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition rightPants = partdefinition.addOrReplaceChild("right_pants", CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.25F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(-1.9F, 12.0F, 0.0F));
        rightPants.addOrReplaceChild("right_pants_lower", CubeListBuilder.create().texOffs(0, 38).addBox(-2.0F, -0.25F, -2.0F, 4.0F, 6.25F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 6.0F, 0.0F));

        // 4. 左腿
        PartDefinition leftLeg = partdefinition.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.25F, 4.0F), PartPose.offset(1.9F, 12.0F, 0.0F));
        leftLeg.addOrReplaceChild("left_leg_lower", CubeListBuilder.create().texOffs(16, 54).addBox(-2.0F, -0.25F, -2.0F, 4.0F, 6.25F, 4.0F), PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition leftPants = partdefinition.addOrReplaceChild("left_pants", CubeListBuilder.create().texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.25F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(1.9F, 12.0F, 0.0F));
        leftPants.addOrReplaceChild("left_pants_lower", CubeListBuilder.create().texOffs(0, 54).addBox(-2.0F, -0.25F, -2.0F, 4.0F, 6.25F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 6.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(HeroEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // [关键修复] 每次渲染前，彻底重置所有基础坐标和【旋转角度】！
        // 防止退出姿势编辑器后，躯干等部位的旋转角度发生“脏数据残留”导致身体翻折。
        this.head.setPos(0.0F, 0.0F, 0.0F);
        this.body.setPos(0.0F, 0.0F, 0.0F);
        this.rightArm.setPos(-5.0F, 2.0F, 0.0F);
        this.leftArm.setPos(5.0F, 2.0F, 0.0F);
        this.rightLeg.setPos(-1.9F, 12.0F, 0.0F);
        this.leftLeg.setPos(1.9F, 12.0F, 0.0F);

        // 强行清零上半身的绝对旋转
        this.head.xRot = 0; this.head.yRot = 0; this.head.zRot = 0;
        this.body.xRot = 0; this.body.yRot = 0; this.body.zRot = 0;
        this.rightArm.xRot = 0; this.rightArm.yRot = 0; this.rightArm.zRot = 0;
        this.leftArm.xRot = 0; this.leftArm.yRot = 0; this.leftArm.zRot = 0;
        this.rightLeg.xRot = 0; this.rightLeg.yRot = 0; this.rightLeg.zRot = 0;
        this.leftLeg.xRot = 0; this.leftLeg.yRot = 0; this.leftLeg.zRot = 0;

        // 下半截弯折角度清零
        this.rightArmLower.xRot = 0; this.rightArmLower.yRot = 0; this.rightArmLower.zRot = 0;
        this.leftArmLower.xRot = 0; this.leftArmLower.yRot = 0; this.leftArmLower.zRot = 0;
        this.rightLegLower.xRot = 0; this.rightLegLower.yRot = 0; this.rightLegLower.zRot = 0;
        this.leftLegLower.xRot = 0; this.leftLegLower.yRot = 0; this.leftLegLower.zRot = 0;

        // 交给原版系统接管基础动画
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // --- 姿势编辑器接管渲染 ---
        if (entity.isPoseEditing && !entity.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
            float[] cHead = entity.customPoseAngles[0];
            float[] cBody = entity.customPoseAngles[1];
            float[] cRArm = entity.customPoseAngles[2];
            float[] cRArmL= entity.customPoseAngles[3];
            float[] cLArm = entity.customPoseAngles[4];
            float[] cLArmL= entity.customPoseAngles[5];
            float[] cRLeg = entity.customPoseAngles[6];
            float[] cRLegL= entity.customPoseAngles[7];
            float[] cLLeg = entity.customPoseAngles[8];
            float[] cLLegL= entity.customPoseAngles[9];

            // 1. 躯干 (作为整个上身的基准)
            this.body.xRot = cBody[0];
            this.body.yRot = cBody[1];
            this.body.zRot = cBody[2];

            // 2. 头部跟随躯干 (原点同样在0,0,0，所以坐标不需要移动，只需叠加旋转)
            this.head.xRot = cBody[0] + cHead[0];
            this.head.yRot = cBody[1] + cHead[1];
            this.head.zRot = cBody[2] + cHead[2];

            // 3. 右臂跟随躯干联动 (计算肩膀在 3D 空间被旋转后的新位置)
            org.joml.Vector3f rArmPos = new org.joml.Vector3f(-5.0F, 2.0F, 0.0F);
            rArmPos.rotateX(cBody[0]).rotateY(cBody[1]).rotateZ(cBody[2]);
            this.rightArm.setPos(rArmPos.x, rArmPos.y, rArmPos.z);
            this.rightArm.xRot = cBody[0] + cRArm[0];
            this.rightArm.yRot = cBody[1] + cRArm[1];
            this.rightArm.zRot = cBody[2] + cRArm[2];

            // 4. 左臂跟随躯干联动
            org.joml.Vector3f lArmPos = new org.joml.Vector3f(5.0F, 2.0F, 0.0F);
            lArmPos.rotateX(cBody[0]).rotateY(cBody[1]).rotateZ(cBody[2]);
            this.leftArm.setPos(lArmPos.x, lArmPos.y, lArmPos.z);
            this.leftArm.xRot = cBody[0] + cLArm[0];
            this.leftArm.yRot = cBody[1] + cLArm[1];
            this.leftArm.zRot = cBody[2] + cLArm[2];

            // 5. 右腿跟随躯干联动 (计算大腿根部的新位置)
            org.joml.Vector3f rLegPos = new org.joml.Vector3f(-1.9F, 12.0F, 0.0F);
            rLegPos.rotateX(cBody[0]).rotateY(cBody[1]).rotateZ(cBody[2]);
            this.rightLeg.setPos(rLegPos.x, rLegPos.y, rLegPos.z);
            this.rightLeg.xRot = cBody[0] + cRLeg[0];
            this.rightLeg.yRot = cBody[1] + cRLeg[1];
            this.rightLeg.zRot = cBody[2] + cRLeg[2];

            // 6. 左腿跟随躯干联动
            org.joml.Vector3f lLegPos = new org.joml.Vector3f(1.9F, 12.0F, 0.0F);
            lLegPos.rotateX(cBody[0]).rotateY(cBody[1]).rotateZ(cBody[2]);
            this.leftLeg.setPos(lLegPos.x, lLegPos.y, lLegPos.z);
            this.leftLeg.xRot = cBody[0] + cLLeg[0];
            this.leftLeg.yRot = cBody[1] + cLLeg[1];
            this.leftLeg.zRot = cBody[2] + cLLeg[2];

            // 7. 下半截（小臂/小腿）因为原本就是子节点，天然跟随上半截，只需赋值局部角度
            this.rightArmLower.xRot = cRArmL[0]; this.rightArmLower.yRot = cRArmL[1]; this.rightArmLower.zRot = cRArmL[2];
            this.leftArmLower.xRot  = cLArmL[0]; this.leftArmLower.yRot  = cLArmL[1]; this.leftArmLower.zRot  = cLArmL[2];
            this.rightLegLower.xRot = cRLegL[0]; this.rightLegLower.yRot = cRLegL[1]; this.rightLegLower.zRot = cRLegL[2];
            this.leftLegLower.xRot  = cLLegL[0]; this.leftLegLower.yRot  = cLLegL[1]; this.leftLegLower.zRot  = cLLegL[2];

            copyAllModelProperties();
            return;
        }

        // --- 以下是原有的战斗/浮空等原生动画逻辑 ---
        if (entity.isInspectingScythe()) {
            setupScytheInspectAnim(entity, ageInTicks);
            return;
        }

        if (entity.isDebugAnim()) {
            setupDebugAnim(entity, ageInTicks);
            return;
        }

        if (entity.isCastingThunder()) {
            setupThunderAnim(entity, ageInTicks);
            return;
        }

        float partialTick = ageInTicks - entity.tickCount;
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

        if (this.attackTime <= 0 && this.rightArmPose == ArmPose.EMPTY) {
            this.rightArm.xRot = Mth.lerp(floatAmount, this.rightArm.xRot, floatArmX);
            this.rightArm.zRot = Mth.lerp(floatAmount, 0.0F, floatRightArmZ);
        }
        if (this.attackTime <= 0 && this.leftArmPose == ArmPose.EMPTY) {
            this.leftArm.xRot = Mth.lerp(floatAmount, this.leftArm.xRot, floatArmX);
            this.leftArm.zRot = Mth.lerp(floatAmount, 0.0F, floatLeftArmZ);
        }

        com.whitecloud233.herobrine_companion.client.fight.animation.HeroChallengeAnimations.setupChallengeAnims(this, entity, ageInTicks);

        copyAllModelProperties();
    }

    private void setupScytheInspectAnim(HeroEntity entity, float ageInTicks) {
        float animTimer = entity.scytheAnimTick;
        float totalDuration = 160.0F;
        float fadeDuration = 20.0F;
        float blend = 0.0F;

        if (animTimer > (totalDuration - fadeDuration)) {
            blend = (totalDuration - animTimer) / fadeDuration;
        } else if (animTimer < fadeDuration) {
            blend = animTimer / fadeDuration;
        } else {
            blend = 1.0F;
        }

        this.rightArm.xRot = Mth.lerp(blend, this.rightArm.xRot, -1.4F);
        this.rightArm.yRot = Mth.lerp(blend, this.rightArm.yRot, -0.4F);
        this.rightArm.zRot = Mth.lerp(blend, this.rightArm.zRot, -0.3F);

        float strokeSpeed = 0.08F;
        float strokeRange = 0.4F;
        float strokeMotion = Mth.cos(ageInTicks * strokeSpeed) * strokeRange;

        this.leftArm.xRot = Mth.lerp(blend, this.leftArm.xRot, -1.35F);
        this.leftArm.yRot = Mth.lerp(blend, this.leftArm.yRot, 0.5F + strokeMotion);
        this.leftArm.zRot = Mth.lerp(blend, this.leftArm.zRot, 0.2F + (strokeMotion * 0.1F));

        float headTracking = strokeMotion * 0.3F;
        this.head.xRot = Mth.lerp(blend, this.head.xRot, 0.5F);
        this.head.yRot = Mth.lerp(blend, this.head.yRot, headTracking);

        copyAllModelProperties();
    }

    private void setupDebugAnim(HeroEntity entity, float ageInTicks) {
        float animTimer = entity.debugAnimTick;
        float blend = 0.0F;
        if (animTimer > 90) blend = (100 - animTimer) / 10.0F;
        else if (animTimer > 10) blend = 1.0F;
        else blend = animTimer / 10.0F;

        if (blend <= 0.01F) return;

        this.head.xRot = Mth.lerp(blend, this.head.xRot, 0.3F);
        this.head.yRot = Mth.lerp(blend, this.head.yRot, -0.4F);

        float typeAction = Mth.sin(ageInTicks * 0.8F) * 0.05F;
        if (entity.getRandom().nextFloat() < 0.1F) {
            typeAction += 0.15F;
        }

        this.rightArm.xRot = Mth.lerp(blend, this.rightArm.xRot, -1.5F + typeAction);
        this.rightArm.yRot = Mth.lerp(blend, this.rightArm.yRot, -0.5F);
        this.rightArm.zRot = Mth.lerp(blend, this.rightArm.zRot, typeAction * 0.5F);

        this.leftArm.xRot = Mth.lerp(blend, this.leftArm.xRot, 0.2F);
        this.leftArm.zRot = Mth.lerp(blend, this.leftArm.zRot, 0.1F);

        copyAllModelProperties();
    }

    private void setupThunderAnim(HeroEntity entity, float ageInTicks) {
        // 【核心修复 2】替换获取渲染插值的 API
        float progress = entity.getThunderProgress(Minecraft.getInstance().getTimer().getGameTimeDeltaTicks());
        if (progress <= 0.01F) return;

        float smooth = progress * progress * (3.0F - 2.0F * progress);

        this.rightArm.xRot = Mth.lerp(smooth, this.rightArm.xRot, -3.2F);
        this.rightArm.zRot = Mth.lerp(smooth, this.rightArm.zRot, 0.2F);
        this.rightArm.yRot = Mth.lerp(smooth, this.rightArm.yRot, 0.0F);

        this.leftArm.xRot = Mth.lerp(smooth, this.leftArm.xRot, 0.8F);
        this.leftArm.zRot = Mth.lerp(smooth, this.leftArm.zRot, -0.4F);

        this.head.xRot = Mth.lerp(smooth, this.head.xRot, -1.2F);
        this.head.yRot = Mth.lerp(smooth, this.head.yRot, 0.0F);

        this.body.xRot = Mth.lerp(smooth, this.body.xRot, -0.3F);
        this.body.y = Mth.lerp(smooth, this.body.y, -2.0F);

        if (progress > 0.6F) {
            float shakeIntensity = (progress - 0.6F) * 0.05F;
            float shakeX = Mth.sin(ageInTicks * 2.5F) * shakeIntensity;
            float shakeZ = Mth.cos(ageInTicks * 2.5F) * shakeIntensity;

            this.rightArm.xRot += shakeX * 2.0F;
            this.rightArm.zRot += shakeZ;
            this.head.yRot += shakeX;
            this.body.xRot += shakeX * 0.5F;
        }

        copyAllModelProperties();
    }

    private void copyAllModelProperties() {
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);

        this.rightSleeveLower.copyFrom(this.rightArmLower);
        this.leftSleeveLower.copyFrom(this.leftArmLower);
        this.rightPantsLower.copyFrom(this.rightLegLower);
        this.leftPantsLower.copyFrom(this.leftLegLower);
    }
}