package com.whitecloud233.modid.herobrine_companion.client.model;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.compat.cooking.HeroCookingCompat;
import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroCombatWeaponHelper;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.modid.herobrine_companion.fight.animation.HeroChallengeAnimations;
import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraftforge.registries.ForgeRegistries;

public class HeroModel extends PlayerModel<HeroEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.tryParse(HerobrineCompanion.MODID + ":hero"), "main");

    public final ModelPart rightArmLower;
    public final ModelPart leftArmLower;
    public final ModelPart rightLegLower;
    public final ModelPart leftLegLower;

    public final ModelPart rightSleeveLower;
    public final ModelPart leftSleeveLower;
    public final ModelPart rightPantsLower;
    public final ModelPart leftPantsLower;

    private static final float[][] FLYING_POSE = new float[][] {
            {0.0F, 0.0F, 0.0F},
            {0.11219974F, 0.0F, 0.0F},
            {0.25244942F, 0.028049935F, -0.19634955F},
            {0.0F, 0.0F, -0.19634955F},
            {0.25244942F, -0.028049935F, 0.19634955F},
            {0.0F, 0.0F, 0.19634955F},
            {-0.11219974F, 0.3926991F, 0.0F},
            {0.420749F, -0.05609987F, 0.0F},
            {0.30854928F, -0.28049934F, 0.0F},
            {-0.028049935F, 0.0F, 0.0F}
    };
    private static final float[] WALK_ROOT_Y = {0.0F, -1.0F, -1.0F, -1.0F, 0.0F};
    private static final float[] WALK_LEFT_LEG_X = {0.0F, 15.0F, -10.0F, 15.0F, 0.0F};
    private static final float[] WALK_LEFT_LEG_LOWER_X = {0.0F, 2.5F, 10.0F, 2.5F, 0.0F};
    private static final float[] WALK_RIGHT_LEG_X = {0.0F, -10.0F, 15.0F, -10.0F, 0.0F};
    private static final float[] WALK_RIGHT_LEG_LOWER_X = {0.0F, 10.0F, 2.5F, 10.0F, 0.0F};
    private static final float[] WALK_LOWER_LEG_Y = {0.0F, 1.0F, 1.0F, 1.0F, 0.0F};
    private static final float[] WALK_LEFT_ARM_X = {0.0F, -7.5F, 12.5F, -7.5F, 0.0F};
    private static final float[] WALK_LEFT_ARM_LOWER_X = {0.0F, -12.5F, -7.5F, -12.5F, 0.0F};
    private static final float[] WALK_RIGHT_ARM_X = {0.0F, 12.5F, -7.5F, 12.5F, 0.0F};
    private static final float[] WALK_RIGHT_ARM_LOWER_X = {0.0F, -7.5F, -12.5F, -7.5F, 0.0F};

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
        this.rightArmLower.setPos(0.0F, 4.0F, 0.0F);
        this.leftArmLower.setPos(0.0F, 4.0F, 0.0F);
        this.rightLegLower.setPos(0.0F, 6.0F, 0.0F);
        this.leftLegLower.setPos(0.0F, 6.0F, 0.0F);

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

        if (HeroEpicFightCompat.shouldUseEpicFightPose(entity)) {
            copyAllModelProperties();
            return;
        }

        float partialTick = ageInTicks - entity.tickCount;
        float floatAmount = entity.getFloatingAmount(partialTick);

        if (floatAmount <= 0.0F && entity.isBattleModeActive() && !entity.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
            setupBattleModeAnim(entity, limbSwing, limbSwingAmount, ageInTicks);
            return;
        }

        if (isVisualEatingPoseActive(entity)) {
            setupEatAnim(entity, ageInTicks);
            return;
        }

        if (isCookingPoseActive(entity)) {
            setupCookAnim(entity, ageInTicks);
            return;
        }

        float horizontalSpeed = (float) entity.getDeltaMovement().horizontalDistance();
        float frameDisplacement = Mth.sqrt(
                Mth.square((float) (entity.getX() - entity.xOld))
                        + Mth.square((float) (entity.getZ() - entity.zOld)));
        float vanillaWalkSpeed = entity.walkAnimation.speed(partialTick);
        float walkIntensity = Math.max(limbSwingAmount,
                Math.max(vanillaWalkSpeed, Mth.clamp(Math.max(horizontalSpeed, frameDisplacement) * 4.0F, 0.0F, 1.0F)));
        if (entity.isGroundWalking()) walkIntensity = Math.max(walkIntensity, 0.35F);
        if (!entity.isFloating()
                && !entity.isBattleModeActive()
                && !entity.isPassenger()
                && !entity.isCrouching()
                && !entity.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)
                && (entity.isGroundWalking() || walkIntensity > 0.001F)) {
            applyImportedWalkAnimation(entity, walkIntensity, partialTick);
            copyAllModelProperties();
            return;
        }

        boolean observerState = entity.getMindState() == SimpleNeuralNetwork.MindState.OBSERVER;
        float headTilt = observerState ? 0.0F : Mth.sin(ageInTicks * 0.05f) * 0.05f;
        if (!observerState) {
            headTilt += (netHeadYaw * 0.01f) * 0.2f;
        }
        this.head.zRot = headTilt;
        this.hat.zRot = this.head.zRot;

        float walkBodyY = 0.0F;
        float floatBodyY = -2.0F + Mth.sin(ageInTicks * 0.1f) * 3.0F;

        if (!entity.isCrouching()) {
            this.body.y = Mth.lerp(floatAmount, walkBodyY, floatBodyY);

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

        applyFlyingPresetPose(floatAmount);

        HeroChallengeAnimations.setupChallengeAnims(this, entity, ageInTicks);

        copyAllModelProperties();
    }

    private void applyImportedWalkAnimation(HeroEntity entity, float limbSwingAmount, float partialTick) {
        float blend = Mth.clamp(limbSwingAmount * 4.0F, 0.0F, 1.0F);
        float animationTime = positiveModulo(entity.getImportedWalkAnimationTime(partialTick), 2.0F);
        float rootOffsetY = -sampleWalkKeyframes(WALK_ROOT_Y, animationTime) * blend;
        this.body.y += rootOffsetY;
        this.head.y += rootOffsetY;
        this.rightArm.y += rootOffsetY;
        this.leftArm.y += rootOffsetY;
        this.rightLeg.y += rootOffsetY;
        this.leftLeg.y += rootOffsetY;
        this.leftLeg.xRot = blendDegrees(this.leftLeg.xRot, sampleWalkKeyframes(WALK_LEFT_LEG_X, animationTime), blend);
        this.leftLegLower.xRot = blendDegrees(this.leftLegLower.xRot, sampleWalkKeyframes(WALK_LEFT_LEG_LOWER_X, animationTime), blend);
        this.rightLeg.xRot = blendDegrees(this.rightLeg.xRot, sampleWalkKeyframes(WALK_RIGHT_LEG_X, animationTime), blend);
        this.rightLegLower.xRot = blendDegrees(this.rightLegLower.xRot, sampleWalkKeyframes(WALK_RIGHT_LEG_LOWER_X, animationTime), blend);
        float lowerLegOffset = sampleWalkKeyframes(WALK_LOWER_LEG_Y, animationTime) * blend;
        this.leftLegLower.y = 6.0F - lowerLegOffset;
        this.rightLegLower.y = 6.0F - lowerLegOffset;
        this.leftArm.xRot = blendDegrees(this.leftArm.xRot, sampleWalkKeyframes(WALK_LEFT_ARM_X, animationTime), blend);
        this.leftArmLower.xRot = blendDegrees(this.leftArmLower.xRot, sampleWalkKeyframes(WALK_LEFT_ARM_LOWER_X, animationTime), blend);
        this.rightArm.xRot = blendDegrees(this.rightArm.xRot, sampleWalkKeyframes(WALK_RIGHT_ARM_X, animationTime), blend);
        this.rightArmLower.xRot = blendDegrees(this.rightArmLower.xRot, sampleWalkKeyframes(WALK_RIGHT_ARM_LOWER_X, animationTime), blend);
    }

    private static float sampleWalkKeyframes(float[] values, float animationTime) {
        float scaledTime = Mth.clamp(animationTime * 2.0F, 0.0F, 3.9999F);
        int segment = Mth.floor(scaledTime);
        float progress = scaledTime - segment;
        float p0 = values[Math.max(0, segment - 1)];
        float p1 = values[segment];
        float p2 = values[Math.min(values.length - 1, segment + 1)];
        float p3 = values[Math.min(values.length - 1, segment + 2)];
        float p2t = progress * progress;
        float p3t = p2t * progress;
        return 0.5F * ((2.0F * p1) + (-p0 + p2) * progress
                + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * p2t
                + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * p3t);
    }

    private static float blendDegrees(float currentRadians, float targetDegrees, float blend) {
        return Mth.lerp(blend, currentRadians, targetDegrees * ((float) Math.PI / 180.0F));
    }

    private static float positiveModulo(float value, float modulus) {
        float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }

    private void applyFlyingPresetPose(float floatAmount) {
        if (floatAmount <= 0.0F) {
            return;
        }

        float[] cHead = FLYING_POSE[0];
        float[] cBody = FLYING_POSE[1];
        float[] cRArm = FLYING_POSE[2];
        float[] cRArmL = FLYING_POSE[3];
        float[] cLArm = FLYING_POSE[4];
        float[] cLArmL = FLYING_POSE[5];
        float[] cRLeg = FLYING_POSE[6];
        float[] cRLegL = FLYING_POSE[7];
        float[] cLLeg = FLYING_POSE[8];
        float[] cLLegL = FLYING_POSE[9];

        this.body.xRot = Mth.lerp(floatAmount, this.body.xRot, cBody[0]);
        this.body.yRot = Mth.lerp(floatAmount, this.body.yRot, cBody[1]);
        this.body.zRot = Mth.lerp(floatAmount, this.body.zRot, cBody[2]);

        this.head.xRot = Mth.lerp(floatAmount, this.head.xRot, cBody[0] + cHead[0]);
        this.head.yRot = Mth.lerp(floatAmount, this.head.yRot, cBody[1] + cHead[1]);
        this.head.zRot = Mth.lerp(floatAmount, this.head.zRot, cBody[2] + cHead[2]);

        if (this.attackTime <= 0 && this.rightArmPose == ArmPose.EMPTY) {
            blendRootedFlyingPose(this.rightArm, -5.0F, 2.0F, 0.0F, cBody, cRArm, floatAmount);
            blendLocalFlyingPose(this.rightArmLower, cRArmL, floatAmount);
        }
        if (this.attackTime <= 0 && this.leftArmPose == ArmPose.EMPTY) {
            blendRootedFlyingPose(this.leftArm, 5.0F, 2.0F, 0.0F, cBody, cLArm, floatAmount);
            blendLocalFlyingPose(this.leftArmLower, cLArmL, floatAmount);
        }

        blendRootedFlyingPose(this.rightLeg, -1.9F, 12.0F, 0.0F, cBody, cRLeg, floatAmount);
        blendRootedFlyingPose(this.leftLeg, 1.9F, 12.0F, 0.0F, cBody, cLLeg, floatAmount);
        blendLocalFlyingPose(this.rightLegLower, cRLegL, floatAmount);
        blendLocalFlyingPose(this.leftLegLower, cLLegL, floatAmount);
    }

    private void blendRootedFlyingPose(ModelPart part, float baseX, float baseY, float baseZ, float[] bodyAngles, float[] partAngles, float amount) {
        float x = baseX;
        float y = baseY;
        float z = baseZ;

        float sinX = Mth.sin(bodyAngles[0]);
        float cosX = Mth.cos(bodyAngles[0]);
        float rotatedY = y * cosX - z * sinX;
        float rotatedZ = y * sinX + z * cosX;
        y = rotatedY;
        z = rotatedZ;

        float sinY = Mth.sin(bodyAngles[1]);
        float cosY = Mth.cos(bodyAngles[1]);
        float rotatedX = x * cosY + z * sinY;
        rotatedZ = -x * sinY + z * cosY;
        x = rotatedX;
        z = rotatedZ;

        float sinZ = Mth.sin(bodyAngles[2]);
        float cosZ = Mth.cos(bodyAngles[2]);
        rotatedX = x * cosZ - y * sinZ;
        rotatedY = x * sinZ + y * cosZ;

        part.x = Mth.lerp(amount, part.x, rotatedX);
        part.y = Mth.lerp(amount, part.y, rotatedY + this.body.y);
        part.z = Mth.lerp(amount, part.z, z);
        part.xRot = Mth.lerp(amount, part.xRot, bodyAngles[0] + partAngles[0]);
        part.yRot = Mth.lerp(amount, part.yRot, bodyAngles[1] + partAngles[1]);
        part.zRot = Mth.lerp(amount, part.zRot, bodyAngles[2] + partAngles[2]);
    }

    private void blendLocalFlyingPose(ModelPart part, float[] angles, float amount) {
        part.xRot = Mth.lerp(amount, part.xRot, angles[0]);
        part.yRot = Mth.lerp(amount, part.yRot, angles[1]);
        part.zRot = Mth.lerp(amount, part.zRot, angles[2]);
    }

    private boolean isCookingPoseActive(HeroEntity entity) {
        if (entity.getInvitedAction() != HeroCookingCompat.INVITED_ACTION_COOK || entity.getInvitedPos() == null) {
            return false;
        }

        if (!HeroCookingCompat.isCookwareStation(entity.level(), entity.getInvitedPos())) {
            return false;
        }

        return entity.distanceToSqr(entity.getInvitedPos().getX() + 0.5D,
                entity.getInvitedPos().getY() + 0.5D,
                entity.getInvitedPos().getZ() + 0.5D) <= 9.0D;
    }

    private boolean isVisualEatingPoseActive(HeroEntity entity) {
        return entity.isVisualEatingActive() && !entity.getVisualMainHandItem().isEmpty();
    }

    private void setupCookAnim(HeroEntity entity, float ageInTicks) {
        ItemStack mainDisplay = HeroCookingCompat.getCookMainHandDisplay(entity);
        ItemStack visualOffDisplay = entity.getVisualOffHandItem();
        ItemStack offDisplay = !visualOffDisplay.isEmpty() ? visualOffDisplay : HeroCookingCompat.getCookOffhandDisplay(entity);
        String mainPath = getItemPath(mainDisplay);
        boolean chopping = mainPath.contains("knife");
        boolean stirring = mainPath.contains("shovel");
        boolean serving = !visualOffDisplay.isEmpty();
        boolean pouring = !serving && !offDisplay.isEmpty();

        float phase = ageInTicks * (chopping ? 0.95F : 0.38F);
        float primarySwing = Mth.sin(phase);
        float secondarySwing = Mth.sin(phase * 0.55F + 0.7F);
        float lift = Mth.cos(phase * 0.5F) * 0.05F;

        this.body.xRot = -0.12F;
        this.body.yRot *= 0.35F;
        this.head.xRot = Mth.lerp(0.7F, this.head.xRot, -0.42F + lift);
        this.head.yRot *= 0.45F;
        this.head.zRot = 0.0F;

        this.rightArm.yRot = chopping ? -0.14F : -0.28F;
        this.rightArm.zRot = chopping ? 0.10F : 0.16F + primarySwing * 0.08F;
        this.rightArm.xRot = chopping
                ? -1.75F + Mth.sin(phase) * 0.70F
                : -1.05F + primarySwing * 0.24F + lift;
        this.rightArmLower.xRot = chopping ? -0.12F : -0.18F;
        this.rightArmLower.yRot = 0.0F;
        this.rightArmLower.zRot = 0.0F;

        if (serving) {
            this.leftArm.xRot = -0.74F + secondarySwing * 0.04F;
            this.leftArm.yRot = 0.32F;
            this.leftArm.zRot = -0.08F;
            this.leftArmLower.xRot = -0.42F;
        } else if (pouring) {
            this.leftArm.xRot = -0.90F + secondarySwing * 0.06F;
            this.leftArm.yRot = 0.18F;
            this.leftArm.zRot = -0.20F;
            this.leftArmLower.xRot = -0.10F;
        } else if (stirring) {
            this.leftArm.xRot = -0.48F + secondarySwing * 0.10F;
            this.leftArm.yRot = 0.20F;
            this.leftArm.zRot = -0.16F;
            this.leftArmLower.xRot = -0.14F;
        } else {
            this.leftArm.xRot = -0.34F + secondarySwing * 0.08F;
            this.leftArm.yRot = 0.12F;
            this.leftArm.zRot = -0.10F;
            this.leftArmLower.xRot = -0.08F;
        }
        this.leftArmLower.yRot = 0.0F;
        this.leftArmLower.zRot = 0.0F;

        this.rightLeg.xRot = 0.04F;
        this.leftLeg.xRot = -0.04F;
        this.rightLeg.zRot = 0.02F;
        this.leftLeg.zRot = -0.02F;

        copyAllModelProperties();
    }

    private void setupEatAnim(HeroEntity entity, float ageInTicks) {
        HumanoidArm mainArm = entity.getMainArm();
        ModelPart eatingArm = mainArm == HumanoidArm.RIGHT ? this.rightArm : this.leftArm;
        ModelPart supportArm = mainArm == HumanoidArm.RIGHT ? this.leftArm : this.rightArm;
        ModelPart eatingArmLower = mainArm == HumanoidArm.RIGHT ? this.rightArmLower : this.leftArmLower;
        ModelPart supportArmLower = mainArm == HumanoidArm.RIGHT ? this.leftArmLower : this.rightArmLower;
        float armSide = mainArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float biteSwing = Mth.sin(ageInTicks * 1.15F) * 0.12F;
        float bodyBob = Mth.sin(ageInTicks * 0.55F) * 0.03F;

        this.body.xRot = -0.08F;
        this.body.yRot = -0.08F * armSide;
        this.head.xRot = Mth.lerp(0.75F, this.head.xRot, -0.18F + biteSwing * 0.35F);
        this.head.yRot *= 0.35F;
        this.head.zRot = 0.02F * armSide;

        eatingArm.xRot = -1.68F + biteSwing;
        eatingArm.yRot = -0.42F * armSide;
        eatingArm.zRot = 0.20F * armSide;
        eatingArmLower.xRot = -0.55F + biteSwing * 0.45F;
        eatingArmLower.yRot = 0.0F;
        eatingArmLower.zRot = 0.0F;

        supportArm.xRot = -0.42F + bodyBob;
        supportArm.yRot = 0.18F * armSide;
        supportArm.zRot = -0.16F * armSide;
        supportArmLower.xRot = -0.12F;
        supportArmLower.yRot = 0.0F;
        supportArmLower.zRot = 0.0F;

        this.rightLeg.xRot = 0.06F - bodyBob;
        this.leftLeg.xRot = -0.04F + bodyBob;
        this.rightLeg.zRot = 0.02F;
        this.leftLeg.zRot = -0.02F;

        copyAllModelProperties();
    }

    private String getItemPath(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId == null ? "" : itemId.getPath();
    }

    private void setupBattleModeAnim(HeroEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks) {
        float walkAmount = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        float battleBreath = Mth.sin(ageInTicks * 0.18F) * 0.04F;
        float guardBlend = 0.82F;

        applyBattleHeight(-1.0F + battleBreath * 0.25F);

        this.body.xRot = Mth.lerp(guardBlend, this.body.xRot, -0.18F);
        this.body.yRot = Mth.lerp(guardBlend, this.body.yRot, 0.0F);
        this.body.zRot = Mth.lerp(guardBlend, this.body.zRot, 0.0F);
        this.head.xRot = Mth.lerp(guardBlend, this.head.xRot, -0.08F + battleBreath);

        this.rightArm.xRot = Mth.lerp(guardBlend, this.rightArm.xRot, -1.1F + battleBreath);
        this.rightArm.yRot = Mth.lerp(guardBlend, this.rightArm.yRot, -0.18F);
        this.rightArm.zRot = Mth.lerp(guardBlend, this.rightArm.zRot, 0.10F);
        this.leftArm.xRot = Mth.lerp(guardBlend, this.leftArm.xRot, -0.55F - battleBreath * 0.5F);
        this.leftArm.yRot = Mth.lerp(guardBlend, this.leftArm.yRot, 0.24F);
        this.leftArm.zRot = Mth.lerp(guardBlend, this.leftArm.zRot, -0.18F);

        this.rightArmLower.xRot = Mth.lerp(guardBlend, this.rightArmLower.xRot, -0.35F);
        this.leftArmLower.xRot = Mth.lerp(guardBlend, this.leftArmLower.xRot, -0.18F);

        if (HeroCombatWeaponHelper.isRangedLoadout(entity.getMainHandItem())) {
            applyRangedBattlePose(entity, limbSwing, walkAmount, ageInTicks);
            syncBattleTorsoAnchors();
            copyAllModelProperties();
            return;
        }

        switch (entity.getBattleActionState()) {
            case HeroEntity.BATTLE_ACTION_APPROACH -> applyApproachPose(limbSwing, walkAmount, ageInTicks);
            case HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1 -> applyComboPose(entity, true, ageInTicks);
            case HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2 -> applyComboPose(entity, false, ageInTicks);
            default -> applyBattleIdlePose(ageInTicks);
        }

        syncBattleTorsoAnchors();
        copyAllModelProperties();
    }

    private void applyRangedBattlePose(HeroEntity entity, float limbSwing, float walkAmount, float ageInTicks) {
        float idlePulse = Mth.sin(ageInTicks * 0.14F) * 0.03F;
        float aimBlend = entity.isUsingItem() ? 1.0F : 0.55F;
        float headYaw = this.head.yRot;
        float headPitch = this.head.xRot;
        float strafe = Mth.cos(limbSwing * 0.8F) * 0.55F * walkAmount;

        applyBattleHeight(-1.15F + idlePulse * 0.25F);
        this.body.xRot += 0.06F + walkAmount * 0.04F;
        this.body.yRot += headYaw * 0.18F;
        this.head.yRot *= 0.72F;

        this.rightLeg.xRot = 0.18F - strafe;
        this.leftLeg.xRot = -0.12F + strafe;
        this.rightLegLower.xRot = Mth.clamp(strafe * 0.35F + 0.08F, -0.12F, 0.36F);
        this.leftLegLower.xRot = Mth.clamp(-strafe * 0.35F + 0.08F, -0.12F, 0.36F);
        this.rightLeg.zRot = 0.04F;
        this.leftLeg.zRot = -0.04F;

        this.rightArm.xRot = Mth.lerp(aimBlend, this.rightArm.xRot, -1.38F + headPitch * 0.85F);
        this.rightArm.yRot = Mth.lerp(aimBlend, this.rightArm.yRot, -0.16F + headYaw * 0.35F);
        this.rightArm.zRot = Mth.lerp(aimBlend, this.rightArm.zRot, 0.02F);
        this.rightArmLower.xRot = Mth.lerp(aimBlend, this.rightArmLower.xRot, -0.45F);

        this.leftArm.xRot = Mth.lerp(aimBlend, this.leftArm.xRot, entity.isUsingItem() ? -1.24F + headPitch * 0.92F : -0.62F + idlePulse);
        this.leftArm.yRot = Mth.lerp(aimBlend, this.leftArm.yRot, entity.isUsingItem() ? 0.52F + headYaw * 0.55F : 0.28F);
        this.leftArm.zRot = Mth.lerp(aimBlend, this.leftArm.zRot, entity.isUsingItem() ? -0.14F : -0.22F);
        this.leftArmLower.xRot = Mth.lerp(aimBlend, this.leftArmLower.xRot, entity.isUsingItem() ? -0.72F : -0.24F);

        if (!entity.isUsingItem()) {
            this.rightArm.xRot += strafe * 0.18F;
            this.leftArm.xRot -= strafe * 0.12F;
        }
    }

    private void applyBattleIdlePose(float ageInTicks) {
        float idlePulse = Mth.sin(ageInTicks * 0.16F) * 0.04F;
        applyBattleHeight(-1.0F + idlePulse * 0.15F);
        this.rightArm.xRot += idlePulse;
        this.leftArm.xRot -= idlePulse * 0.5F;
        this.rightLeg.xRot = 0.12F + idlePulse * 0.8F;
        this.leftLeg.xRot = -0.12F - idlePulse * 0.8F;
        this.rightLegLower.xRot = 0.08F;
        this.leftLegLower.xRot = 0.04F;
    }

    private void applyApproachPose(float limbSwing, float walkAmount, float ageInTicks) {
        float stride = Mth.cos(limbSwing * 0.85F) * 0.9F * walkAmount;
        float counterStride = Mth.cos(limbSwing * 0.85F + (float)Math.PI) * 0.9F * walkAmount;
        float combatBounce = Mth.sin(ageInTicks * 0.35F) * 0.04F * walkAmount;

        applyBattleHeight(-1.35F + combatBounce);
        this.body.xRot += 0.08F * walkAmount;
        this.rightLeg.xRot = stride;
        this.leftLeg.xRot = counterStride;
        this.rightLegLower.xRot = Mth.clamp(-stride * 0.55F, -0.25F, 0.45F);
        this.leftLegLower.xRot = Mth.clamp(-counterStride * 0.55F, -0.25F, 0.45F);

        this.rightArm.xRot += counterStride * 0.32F;
        this.leftArm.xRot += stride * 0.24F;
        this.rightArm.zRot += counterStride * 0.08F;
        this.leftArm.zRot -= stride * 0.08F;
        this.head.yRot *= 0.6F;
    }

    private void applyComboPose(HeroEntity entity, boolean firstCombo, float ageInTicks) {
        float progress = Mth.clamp(entity.getBattleActionTicks() / (float)getBattleActionDuration(entity, firstCombo), 0.0F, 1.0F);
        float windup = smoothWindow(progress, 0.0F, 0.24F);
        float strike = smoothWindow(progress, 0.22F, 0.68F);
        float recovery = smoothWindow(progress, 0.60F, 1.0F);
        float torsoTwist = firstCombo ? -0.55F : 0.42F;
        float followThrough = firstCombo ? 0.95F : 1.08F;
        float weaponShake = Mth.sin(ageInTicks * 1.6F + (firstCombo ? 0.0F : 0.7F)) * 0.035F * strike;

        applyBattleHeight(-1.6F + strike * 0.18F);
        this.body.xRot += 0.12F * windup + 0.22F * strike - 0.14F * recovery;
        this.body.yRot += torsoTwist * strike - torsoTwist * 0.35F * recovery;
        this.head.yRot += -torsoTwist * 0.35F * strike;
        this.head.xRot += -0.10F * strike + 0.08F * recovery;

        this.rightArm.xRot = Mth.lerp(windup, this.rightArm.xRot, firstCombo ? -1.8F : -1.55F);
        this.rightArm.yRot = Mth.lerp(windup, this.rightArm.yRot, firstCombo ? -0.95F : 0.55F);
        this.rightArm.zRot = Mth.lerp(windup, this.rightArm.zRot, firstCombo ? 0.24F : -0.32F);

        this.rightArm.xRot = Mth.lerp(strike, this.rightArm.xRot, firstCombo ? 0.55F : -0.10F);
        this.rightArm.yRot = Mth.lerp(strike, this.rightArm.yRot, followThrough);
        this.rightArm.zRot = Mth.lerp(strike, this.rightArm.zRot, firstCombo ? -0.78F : 0.62F);
        this.rightArm.xRot += weaponShake;
        this.rightArm.zRot += weaponShake * 0.7F;

        this.leftArm.xRot = Mth.lerp(windup + strike * 0.35F, this.leftArm.xRot, firstCombo ? -0.35F : -0.65F);
        this.leftArm.yRot = Mth.lerp(windup + strike * 0.35F, this.leftArm.yRot, firstCombo ? 0.55F : -0.18F);
        this.leftArm.zRot = Mth.lerp(windup + strike * 0.35F, this.leftArm.zRot, firstCombo ? -0.32F : -0.42F);

        this.rightArmLower.xRot = Mth.lerp(windup + strike, this.rightArmLower.xRot, firstCombo ? -1.05F : -0.82F);
        this.leftArmLower.xRot = Mth.lerp(windup + strike * 0.45F, this.leftArmLower.xRot, -0.35F);

        this.rightLeg.xRot = Mth.lerp(strike, this.rightLeg.xRot, firstCombo ? -0.48F : 0.32F);
        this.leftLeg.xRot = Mth.lerp(strike, this.leftLeg.xRot, firstCombo ? 0.38F : -0.42F);
        this.rightLegLower.xRot = Mth.lerp(strike, this.rightLegLower.xRot, firstCombo ? 0.24F : 0.12F);
        this.leftLegLower.xRot = Mth.lerp(strike, this.leftLegLower.xRot, firstCombo ? 0.12F : 0.28F);
        this.rightLeg.zRot = Mth.lerp(strike, this.rightLeg.zRot, firstCombo ? 0.08F : -0.05F);
        this.leftLeg.zRot = Mth.lerp(strike, this.leftLeg.zRot, firstCombo ? -0.05F : 0.08F);

        this.rightArm.xRot = Mth.lerp(recovery, this.rightArm.xRot, -0.92F);
        this.rightArm.yRot = Mth.lerp(recovery, this.rightArm.yRot, -0.16F);
        this.rightArm.zRot = Mth.lerp(recovery, this.rightArm.zRot, 0.08F);
        this.leftArm.xRot = Mth.lerp(recovery, this.leftArm.xRot, -0.52F);
        this.leftArm.yRot = Mth.lerp(recovery, this.leftArm.yRot, 0.20F);
        this.leftArm.zRot = Mth.lerp(recovery, this.leftArm.zRot, -0.15F);
    }

    private void applyBattleHeight(float offsetY) {
        this.body.y = offsetY;
        this.head.y = offsetY;
        this.hat.y = this.head.y;

        this.rightArm.y = 2.0F + offsetY;
        this.leftArm.y = 2.0F + offsetY;
        this.rightLeg.y = 12.0F + offsetY;
        this.leftLeg.y = 12.0F + offsetY;

        this.jacket.y = this.body.y;
        this.rightSleeve.y = this.rightArm.y;
        this.leftSleeve.y = this.leftArm.y;
        this.rightPants.y = this.rightLeg.y;
        this.leftPants.y = this.leftLeg.y;
    }

    private void syncBattleTorsoAnchors() {
        float bodyOffsetY = this.body.y;

        org.joml.Vector3f rightShoulder = new org.joml.Vector3f(-5.0F, 2.0F, 0.0F);
        rightShoulder.rotateX(this.body.xRot).rotateY(this.body.yRot).rotateZ(this.body.zRot);
        this.rightArm.setPos(rightShoulder.x, bodyOffsetY + rightShoulder.y, rightShoulder.z);

        org.joml.Vector3f leftShoulder = new org.joml.Vector3f(5.0F, 2.0F, 0.0F);
        leftShoulder.rotateX(this.body.xRot).rotateY(this.body.yRot).rotateZ(this.body.zRot);
        this.leftArm.setPos(leftShoulder.x, bodyOffsetY + leftShoulder.y, leftShoulder.z);

        this.head.setPos(0.0F, bodyOffsetY, 0.0F);
        this.hat.setPos(0.0F, this.head.y, 0.0F);
        this.jacket.setPos(0.0F, this.body.y, 0.0F);
        this.rightSleeve.setPos(this.rightArm.x, this.rightArm.y, this.rightArm.z);
        this.leftSleeve.setPos(this.leftArm.x, this.leftArm.y, this.leftArm.z);
        this.rightPants.setPos(this.rightLeg.x, this.rightLeg.y, this.rightLeg.z);
        this.leftPants.setPos(this.leftLeg.x, this.leftLeg.y, this.leftLeg.z);
    }

    private int getBattleActionDuration(HeroEntity entity, boolean firstCombo) {
        if (entity.getMainHandItem().getItem() instanceof PoemOfTheEndItem poem) {
            return switch (poem.getMode(entity.getMainHandItem())) {
                case PoemOfTheEndItem.MODE_VOID_SHATTER -> firstCombo ? 7 : 8;
                case PoemOfTheEndItem.MODE_REALM_BREAKER, PoemOfTheEndItem.MODE_THUNDER_CALL -> firstCombo ? 14 : 16;
                default -> firstCombo ? 10 : 12;
            };
        }
        return firstCombo ? 8 : 10;
    }

    private float smoothWindow(float value, float start, float end) {
        if (end <= start) {
            return value >= end ? 1.0F : 0.0F;
        }
        return smoothStep(Mth.clamp((value - start) / (end - start), 0.0F, 1.0F));
    }

    private float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
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

        this.rightSleeveLower.copyFrom(this.rightArmLower);
        this.leftSleeveLower.copyFrom(this.leftArmLower);
        this.rightPantsLower.copyFrom(this.rightLegLower);
        this.leftPantsLower.copyFrom(this.leftLegLower);
    }

    @Override
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        translateToUpperArm(arm, poseStack);
    }

    public void translateToUpperArm(HumanoidArm arm, PoseStack poseStack) {
        ModelPart upperArm = arm == HumanoidArm.RIGHT ? this.rightArm : this.leftArm;
        upperArm.translateAndRotate(poseStack);
    }
}
