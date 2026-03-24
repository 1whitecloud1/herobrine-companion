package com.whitecloud233.modid.herobrine_companion.client.model;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class HeroModel extends PlayerModel<HeroEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation(HerobrineCompanion.MODID, "hero"), "main");

    // --- 新增：用于弯折的下半截骨骼 ---
    public final ModelPart rightArmLower;
    public final ModelPart leftArmLower;
    public final ModelPart rightLegLower;
    public final ModelPart leftLegLower;

    // --- 新增：对应的双层皮肤外套/裤腿层 ---
    public final ModelPart rightSleeveLower;
    public final ModelPart leftSleeveLower;
    public final ModelPart rightPantsLower;
    public final ModelPart leftPantsLower;

    public HeroModel(ModelPart root, boolean slim) {
        super(root, slim);

        // 从上臂/大腿中获取作为子节点的下臂/小腿
        this.rightArmLower = this.rightArm.getChild("right_arm_lower");
        this.leftArmLower = this.leftArm.getChild("left_arm_lower");
        this.rightLegLower = this.rightLeg.getChild("right_leg_lower");
        this.leftLegLower = this.leftLeg.getChild("left_leg_lower");

        this.rightSleeveLower = this.rightSleeve.getChild("right_sleeve_lower");
        this.leftSleeveLower = this.leftSleeve.getChild("left_sleeve_lower");
        this.rightPantsLower = this.rightPants.getChild("right_pants_lower");
        this.leftPantsLower = this.leftPants.getChild("left_pants_lower");
    }

    // [核心重构]：用代码硬编码生成带有父子层级关节的玩家模型
    public static LayerDefinition createBodyLayer(boolean slim) {
        MeshDefinition meshdefinition = PlayerModel.createMesh(CubeDeformation.NONE, slim);
        PartDefinition partdefinition = meshdefinition.getRoot();

        // 1. 右臂 (切分为上下两截，每截高度 6 像素)
        PartDefinition rightArm = partdefinition.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offset(-5.0F, 2.0F, 0.0F));
        // 子节点：右小臂，旋转轴心设在手肘处 (Y=4.0F)
        rightArm.addOrReplaceChild("right_arm_lower", CubeListBuilder.create().texOffs(40, 22).addBox(-3.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition rightSleeve = partdefinition.addOrReplaceChild("right_sleeve", CubeListBuilder.create().texOffs(40, 32).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(-5.0F, 2.0F, 0.0F));
        rightSleeve.addOrReplaceChild("right_sleeve_lower", CubeListBuilder.create().texOffs(40, 38).addBox(-3.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 4.0F, 0.0F));

        // 2. 左臂
        PartDefinition leftArm = partdefinition.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offset(5.0F, 2.0F, 0.0F));
        leftArm.addOrReplaceChild("left_arm_lower", CubeListBuilder.create().texOffs(32, 54).addBox(-1.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition leftSleeve = partdefinition.addOrReplaceChild("left_sleeve", CubeListBuilder.create().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(5.0F, 2.0F, 0.0F));
        leftSleeve.addOrReplaceChild("left_sleeve_lower", CubeListBuilder.create().texOffs(48, 54).addBox(-1.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 4.0F, 0.0F));

        // 3. 右腿 (膝盖弯曲)
        PartDefinition rightLeg = partdefinition.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offset(-1.9F, 12.0F, 0.0F));
        // 子节点：右小腿，旋转轴心设在膝盖处 (Y=6.0F)
        rightLeg.addOrReplaceChild("right_leg_lower", CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition rightPants = partdefinition.addOrReplaceChild("right_pants", CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(-1.9F, 12.0F, 0.0F));
        rightPants.addOrReplaceChild("right_pants_lower", CubeListBuilder.create().texOffs(0, 38).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 6.0F, 0.0F));

        // 4. 左腿
        PartDefinition leftLeg = partdefinition.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offset(1.9F, 12.0F, 0.0F));
        leftLeg.addOrReplaceChild("left_leg_lower", CubeListBuilder.create().texOffs(16, 54).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition leftPants = partdefinition.addOrReplaceChild("left_pants", CubeListBuilder.create().texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(1.9F, 12.0F, 0.0F));
        leftPants.addOrReplaceChild("left_pants_lower", CubeListBuilder.create().texOffs(0, 54).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 6.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(HeroEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // [关键] 每次渲染前，必须把小臂/小腿的弯折角度清零，否则会被原版呼吸动画不断累加变异
        this.rightArmLower.xRot = 0; this.rightArmLower.yRot = 0; this.rightArmLower.zRot = 0;
        this.leftArmLower.xRot = 0; this.leftArmLower.yRot = 0; this.leftArmLower.zRot = 0;
        this.rightLegLower.xRot = 0; this.rightLegLower.yRot = 0; this.rightLegLower.zRot = 0;
        this.leftLegLower.xRot = 0; this.leftLegLower.yRot = 0; this.leftLegLower.zRot = 0;

        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // [新增] 姿势编辑器强行接管渲染 (10 个部位完全映射)
        // [修改] 姿势编辑器接管渲染，但必须让步于挑战状态
        if (entity.isPoseEditing && !entity.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
            // ... 保持中间的赋值和 return 不变
            this.head.xRot = entity.customPoseAngles[0][0];
            this.head.yRot = entity.customPoseAngles[0][1];
            this.head.zRot = entity.customPoseAngles[0][2];

            this.body.xRot = entity.customPoseAngles[1][0];
            this.body.yRot = entity.customPoseAngles[1][1];
            this.body.zRot = entity.customPoseAngles[1][2];

            this.rightArm.xRot = entity.customPoseAngles[2][0];
            this.rightArm.yRot = entity.customPoseAngles[2][1];
            this.rightArm.zRot = entity.customPoseAngles[2][2];

            this.rightArmLower.xRot = entity.customPoseAngles[3][0];
            this.rightArmLower.yRot = entity.customPoseAngles[3][1];
            this.rightArmLower.zRot = entity.customPoseAngles[3][2];

            this.leftArm.xRot = entity.customPoseAngles[4][0];
            this.leftArm.yRot = entity.customPoseAngles[4][1];
            this.leftArm.zRot = entity.customPoseAngles[4][2];

            this.leftArmLower.xRot = entity.customPoseAngles[5][0];
            this.leftArmLower.yRot = entity.customPoseAngles[5][1];
            this.leftArmLower.zRot = entity.customPoseAngles[5][2];

            this.rightLeg.xRot = entity.customPoseAngles[6][0];
            this.rightLeg.yRot = entity.customPoseAngles[6][1];
            this.rightLeg.zRot = entity.customPoseAngles[6][2];

            this.rightLegLower.xRot = entity.customPoseAngles[7][0];
            this.rightLegLower.yRot = entity.customPoseAngles[7][1];
            this.rightLegLower.zRot = entity.customPoseAngles[7][2];

            this.leftLeg.xRot = entity.customPoseAngles[8][0];
            this.leftLeg.yRot = entity.customPoseAngles[8][1];
            this.leftLeg.zRot = entity.customPoseAngles[8][2];

            this.leftLegLower.xRot = entity.customPoseAngles[9][0];
            this.leftLegLower.yRot = entity.customPoseAngles[9][1];
            this.leftLegLower.zRot = entity.customPoseAngles[9][2];

            copyAllModelProperties();
            return; // 阻断后续所有原版动画
        }

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

        // --- 保持原有原版浮空动画逻辑 ---
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

        if (this.attackTime <= 0 && this.rightArmPose == ArmPose.EMPTY) {
            this.rightArm.xRot = Mth.lerp(floatAmount, this.rightArm.xRot, floatArmX);
            this.rightArm.zRot = Mth.lerp(floatAmount, 0.0F, floatRightArmZ);
        }
        if (this.attackTime <= 0 && this.leftArmPose == ArmPose.EMPTY) {
            this.leftArm.xRot = Mth.lerp(floatAmount, this.leftArm.xRot, floatArmX);
            this.leftArm.zRot = Mth.lerp(floatAmount, 0.0F, floatLeftArmZ);
        }

        com.whitecloud233.modid.herobrine_companion.client.fight.animation.HeroChallengeAnimations.setupChallengeAnims(this, entity, ageInTicks);

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
        float progress = entity.getThunderProgress(Minecraft.getInstance().getPartialTick());
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

        // [关键] 必须同时同步新增加的小臂/小腿皮肤外套
        this.rightSleeveLower.copyFrom(this.rightArmLower);
        this.leftSleeveLower.copyFrom(this.leftArmLower);
        this.rightPantsLower.copyFrom(this.rightLegLower);
        this.leftPantsLower.copyFrom(this.leftLegLower);
    }
}