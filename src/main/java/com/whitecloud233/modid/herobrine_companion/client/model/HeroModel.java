package com.whitecloud233.modid.herobrine_companion.client.model;

import com.whitecloud233.modid.herobrine_companion.client.animation.HeroPoemAnimation;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingService;
import com.whitecloud233.modid.herobrine_companion.api.compat.HeroEpicFightCompat;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.HeroCombatWeaponHelper;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.modid.herobrine_companion.entity.visual.HeroClip;
import com.whitecloud233.modid.herobrine_companion.entity.visual.HeroScytheComboLibrary;
import com.whitecloud233.modid.herobrine_companion.entity.visual.HeroGestureLibrary;
import com.whitecloud233.modid.herobrine_companion.entity.visual.HeroIdleClips;
import com.whitecloud233.modid.herobrine_companion.entity.visual.HeroLocomotion;
import com.whitecloud233.modid.herobrine_companion.entity.visual.HeroVisualState;
import com.whitecloud233.modid.herobrine_companion.fight.animation.HeroChallengeAnimations;
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

    // 裁剪/公式动画工作缓冲(HeroClip 规范 10 关节 × 3 度 + 微动位置)
    private final float[] clipPoseBuf = new float[30];
    private final float[] microBuf = new float[32];
    private final float[] walkBuf = new float[32];
    private final boolean[] clipTouched = new boolean[HeroClip.CANONICAL_JOINTS];

    /**
     * 磨盘转一圈，腿要迈几个行走环。
     *
     * <p>石磨把 Hero 摆在半径 2 格的圆周上 → 周长 4π ≈ 12.57 格；
     * {@code HeroVisualState} 的行走环每循环前进约 9.43 格（{@code WALK_CYCLE_PER_BLOCK} 的倒数）。
     * 两者相除 ≈ 1.33，按这个比例驱动相位，脚底位移才和圆周位移一致、不打滑。
     */
    private static final float MILLSTONE_CYCLES_PER_LAP = 1.33F;

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
    // 注:行走姿态已由 Bedrock animation.hero.move 的公式移植接管(见 applyImportedWalkAnimation),
    // 原 WALK_* 手写关键帧数组已移除;飞行动画数值经核验与 Bedrock animation.hero.flying 一致。

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
        // 下半段 overlay 高度收到 5.75：外扩 0.25 后底面缩回肢体底端之上 0.25，藏进实体方块内部，
        // 否则底面悬在手脚下方 0.25 处、且采样到分段 UV 里常见的深色/半透明像素，表现为手脚底下多出的黑线。
        rightSleeve.addOrReplaceChild("right_sleeve_lower", CubeListBuilder.create().texOffs(40, 38).addBox(-3.0F, -0.25F, -2.0F, 4.0F, 5.75F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 4.0F, 0.0F));

        // 2. 左臂
        PartDefinition leftArm = partdefinition.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 6.25F, 4.0F), PartPose.offset(5.0F, 2.0F, 0.0F));
        leftArm.addOrReplaceChild("left_arm_lower", CubeListBuilder.create().texOffs(32, 54).addBox(-1.0F, -0.25F, -2.0F, 4.0F, 6.25F, 4.0F), PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition leftSleeve = partdefinition.addOrReplaceChild("left_sleeve", CubeListBuilder.create().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 6.25F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(5.0F, 2.0F, 0.0F));
        leftSleeve.addOrReplaceChild("left_sleeve_lower", CubeListBuilder.create().texOffs(48, 54).addBox(-1.0F, -0.25F, -2.0F, 4.0F, 5.75F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 4.0F, 0.0F));

        // 3. 右腿
        PartDefinition rightLeg = partdefinition.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.25F, 4.0F), PartPose.offset(-1.9F, 12.0F, 0.0F));
        rightLeg.addOrReplaceChild("right_leg_lower", CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, -0.25F, -2.0F, 4.0F, 6.25F, 4.0F), PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition rightPants = partdefinition.addOrReplaceChild("right_pants", CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.25F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(-1.9F, 12.0F, 0.0F));
        rightPants.addOrReplaceChild("right_pants_lower", CubeListBuilder.create().texOffs(0, 38).addBox(-2.0F, -0.25F, -2.0F, 4.0F, 5.75F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 6.0F, 0.0F));

        // 4. 左腿
        PartDefinition leftLeg = partdefinition.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.25F, 4.0F), PartPose.offset(1.9F, 12.0F, 0.0F));
        leftLeg.addOrReplaceChild("left_leg_lower", CubeListBuilder.create().texOffs(16, 54).addBox(-2.0F, -0.25F, -2.0F, 4.0F, 6.25F, 4.0F), PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition leftPants = partdefinition.addOrReplaceChild("left_pants", CubeListBuilder.create().texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.25F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(1.9F, 12.0F, 0.0F));
        leftPants.addOrReplaceChild("left_pants_lower", CubeListBuilder.create().texOffs(0, 54).addBox(-2.0F, -0.25F, -2.0F, 4.0F, 5.75F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 6.0F, 0.0F));

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

        // --- 混合权重层(Bedrock blend_transition 的等价实现) ---
        // 赠礼手势 / 待机变体不再"硬接管"姿态,而是按权重叠加在基础姿态之上,进出各有一段淡入淡出;
        // 权重由 HeroVisuals.tickBlendWeights 每 tick 步进,这里按 partial tick 插值取用。
        float partialTick = ageInTicks - entity.tickCount;
        HeroVisualState visualState = entity.getVisualState();
        float offerBlend = visualState.getOfferBlend(partialTick);
        float variantBlend = visualState.getVariantBlend(partialTick);
        boolean gestureDriving = offerBlend > 0.001F && visualState.getGestureClipId() != 0;

        // --- 姿势编辑器接管渲染(手工姿势优先级最高,不参与手势混合)---
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

        if (HeroPoemAnimation.shouldAnimate(entity, partialTick)) {
            // Native skinning uses the rig's complete transforms, not the legacy Euler-angle battle pose.
            this.head.resetPose(); this.body.resetPose();
            this.rightArm.resetPose(); this.leftArm.resetPose();
            this.rightLeg.resetPose(); this.leftLeg.resetPose();
            this.rightArmLower.resetPose(); this.leftArmLower.resetPose();
            this.rightLegLower.resetPose(); this.leftLegLower.resetPose();
            copyAllModelProperties();
            return;
        }

        // --- 以下是原有的战斗/浮空等原生动画逻辑 ---
        if (!gestureDriving && entity.isInspectingScythe()) {
            setupScytheInspectAnim(entity, ageInTicks);
            return;
        }

        if (!gestureDriving && entity.isDebugAnim()) {
            setupDebugAnim(entity, ageInTicks);
            return;
        }


        // 赠礼手势进行中时不让 EpicFight 姿势接管(手势优先级最高)
        if (!gestureDriving && HeroEpicFightCompat.shouldUseEpicFightPose(entity)) {
            copyAllModelProperties();
            return;
        }

        float floatAmount = entity.getFloatingAmount(partialTick);

        if (!gestureDriving && floatAmount <= 0.0F && entity.isBattleModeActive() && !entity.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
            setupBattleModeAnim(entity, limbSwing, limbSwingAmount, ageInTicks);
            return;
        }

        if (!gestureDriving && isVisualEatingPoseActive(entity)) {
            setupEatAnim(entity, ageInTicks);
            return;
        }

        if (!gestureDriving && isCookingPoseActive(entity)) {
            setupCookAnim(entity, ageInTicks);
            return;
        }

        // 推磨：整个人压在磨杆上往前推，比走路的摆臂幅度更大也更沉。
        if (!gestureDriving && entity.isMillstoneBound()) {
            setupMillstonePushAnim(entity, ageInTicks);
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
        // 送东西时不再播走路姿态:服务端已停走位,这里再从渲染层挡掉"原地踏步"
        if (gestureDriving) {
            walkIntensity = 0.0F;
        }
        if (!gestureDriving
                && !entity.isFloating()
                && !entity.isBattleModeActive()
                && !entity.isPassenger()
                && !entity.isCrouching()
                && !entity.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)
                && (entity.isGroundWalking() || walkIntensity > 0.001F)) {
            applyImportedWalkAnimation(entity, walkIntensity, partialTick);
            copyAllModelProperties();
            return;
        }

        // --- 待机变体:不再在这里硬接管(改为下方按权重叠加,见 applyClipOverlay)---

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

        // --- 基础待机微动(Bedrock animation.hero.idle 公式移植;浮空时由飞行动画接管)---
        if (floatAmount <= 0.0F) {
            HeroLocomotion.computeIdleMicro(ageInTicks / 20.0F, microBuf);
            this.body.x += microBuf[30];
            this.body.y += microBuf[31];
            this.body.xRot += deg2rad(microBuf[3]);
            this.body.zRot += deg2rad(microBuf[5]);
            this.head.zRot += deg2rad(microBuf[2]);
            if (this.attackTime <= 0 && this.rightArmPose == ArmPose.EMPTY) {
                this.rightArm.zRot += deg2rad(microBuf[8]);
                this.leftArm.zRot += deg2rad(microBuf[11]);
            }
            this.head.y = this.body.y;
            this.hat.y = this.head.y;
            this.jacket.y = this.body.y;
            this.rightArm.y = 2.0F + this.body.y;
            this.leftArm.y = 2.0F + this.body.y;
            this.rightSleeve.y = this.rightArm.y;
            this.leftSleeve.y = this.leftArm.y;
        }

        applyFlyingPresetPose(floatAmount);

        HeroChallengeAnimations.setupChallengeAnims(this, entity, ageInTicks);

        // --- 裁剪叠加层(赠礼手势 / 待机变体):按权重混合到基础姿态上,替代原来的硬接管 ---
        if (gestureDriving) {
            applyClipOverlay(HeroGestureLibrary.byClipId(visualState.getGestureClipId()),
                    visualState.getGestureStartTick(), offerBlend, ageInTicks);
        } else if (variantBlend > 0.001F
                && visualState.getIdleVariantClip() >= 0
                && visualState.getIdleVariantClip() < HeroIdleClips.IDLE_VARIANTS.length) {
            applyClipOverlay(HeroIdleClips.IDLE_VARIANTS[visualState.getIdleVariantClip()],
                    visualState.getIdleVariantStartTick(), variantBlend, ageInTicks);
        }

        copyAllModelProperties();
    }

    private void applyImportedWalkAnimation(HeroEntity entity, float limbSwingAmount, float partialTick) {
        float blend = Mth.clamp(limbSwingAmount * 4.0F, 0.0F, 1.0F);
        float animationTime = positiveModulo(entity.getImportedWalkAnimationTime(partialTick), 2.0F);
        float cycle = animationTime / 2.0F; // 0..1,一个完整步态周期(2 秒环)
        // Bedrock animation.hero.move 姿态数学移植(见 HeroLocomotion)
        HeroLocomotion.computeWalkPose(cycle, walkBuf);

        float rootOffsetY = walkBuf[31] * blend;
        this.body.y += rootOffsetY;
        this.head.y += rootOffsetY;
        this.rightArm.y += rootOffsetY;
        this.leftArm.y += rootOffsetY;
        this.rightLeg.y += rootOffsetY;
        this.leftLeg.y += rootOffsetY;
        this.body.x += walkBuf[30] * blend;

        this.body.xRot = blendDegrees(this.body.xRot, walkBuf[3], blend);
        this.body.yRot = blendDegrees(this.body.yRot, walkBuf[4], blend);
        this.body.zRot = blendDegrees(this.body.zRot, walkBuf[5], blend);
        this.head.xRot = blendDegrees(this.head.xRot, walkBuf[0], blend);
        this.head.yRot = blendDegrees(this.head.yRot, walkBuf[1], blend);
        this.head.zRot = blendDegrees(this.head.zRot, walkBuf[2], blend);

        this.leftLeg.xRot = blendDegrees(this.leftLeg.xRot, walkBuf[21], blend);
        this.leftLegLower.xRot = blendDegrees(this.leftLegLower.xRot, walkBuf[27], blend);
        this.rightLeg.xRot = blendDegrees(this.rightLeg.xRot, walkBuf[18], blend);
        this.rightLegLower.xRot = blendDegrees(this.rightLegLower.xRot, walkBuf[24], blend);

        if (this.attackTime <= 0 && this.rightArmPose == ArmPose.EMPTY) {
            this.leftArm.xRot = blendDegrees(this.leftArm.xRot, walkBuf[9], blend);
            this.leftArmLower.xRot = blendDegrees(this.leftArmLower.xRot, walkBuf[15], blend);
            this.rightArm.xRot = blendDegrees(this.rightArm.xRot, walkBuf[6], blend);
            this.rightArmLower.xRot = blendDegrees(this.rightArmLower.xRot, walkBuf[12], blend);
        }
    }

    private static float blendDegrees(float currentRadians, float targetDegrees, float blend) {
        return Mth.lerp(blend, currentRadians, targetDegrees * ((float) Math.PI / 180.0F));
    }

    private static float positiveModulo(float value, float modulus) {
        float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }

    // ------------------------------------------------------------------
    // 赠礼手势 / 待机变体的规范姿势应用(Bedrock POSE_BONES ↔ Java 部件映射)
    // ------------------------------------------------------------------
    private static final int P_HEAD = HeroClip.J_HEAD;
    private static final int P_BODY = HeroClip.J_BODY;
    private static final int P_R_ARM = HeroClip.J_RIGHT_ARM_UPPER;
    private static final int P_L_ARM = HeroClip.J_LEFT_ARM_UPPER;
    private static final int P_R_FORE = HeroClip.J_RIGHT_ARM_LOWER;
    private static final int P_L_FORE = HeroClip.J_LEFT_ARM_LOWER;
    private static final int P_R_LEG = HeroClip.J_RIGHT_LEG_UPPER;
    private static final int P_L_LEG = HeroClip.J_LEFT_LEG_UPPER;
    private static final int P_R_LOWER = HeroClip.J_RIGHT_LEG_LOWER;
    private static final int P_L_LOWER = HeroClip.J_LEFT_LEG_LOWER;

    private static float deg2rad(float deg) {
        return deg * ((float) Math.PI / 180.0F);
    }

    /**
     * 裁剪叠加层:把 HeroClip 的规范姿势按权重混合到当前(基础)姿态上,替代原来的"硬接管 + 提前 return"。
     *
     * <p>权重 0 → 完全保持基础姿态(走路/待机微动/浮空等);1 → 完全等于裁剪姿势(与旧实现一致)。
     * 进出过程中的权重由 {@code HeroVisuals.tickBlendWeights} 每 tick 步进(对齐 Bedrock
     * {@code blend_transition}),因此手势/待机变体的出现与退出都是可见的交叉淡化,而不是一帧跳变。
     *
     * <p>未被裁剪触及的关节保持基础姿态(等价 Bedrock 的"基础 idle 常开 + 变体叠加")。
     */
    private void applyClipOverlay(HeroClip clip, int startTick, float weight, float ageInTicks) {
        if (clip == null || weight <= 0.001F) {
            return;
        }
        applyClipPose(clip, Mth.clamp((ageInTicks - startTick) / 20.0F, 0.0F, clip.duration), weight);
    }

    /**
     * 在裁剪内时间 t（秒）处以权重 weight 应用规范姿势。
     * 战斗镰刀裁剪按服务端攻击进度换算出 t 后直接走这里（真实时钟驱动的手势仍用上面的重载）。
     */
    private void applyClipPose(HeroClip clip, float t, float weight) {
        if (clip == null || weight <= 0.001F) {
            return;
        }
        HeroClip.samplePose(clip, t, clipPoseBuf);
        buildTouched(clip);
        float w = Mth.clamp(weight, 0.0F, 1.0F);

        float bX = deg2rad(clipPoseBuf[3 * P_BODY]);
        float bY = deg2rad(clipPoseBuf[3 * P_BODY + 1]);
        float bZ = deg2rad(clipPoseBuf[3 * P_BODY + 2]);
        if (clipTouched[P_BODY]) {
            this.body.xRot = Mth.lerp(w, this.body.xRot, bX);
            this.body.yRot = Mth.lerp(w, this.body.yRot, bY);
            this.body.zRot = Mth.lerp(w, this.body.zRot, bZ);
        }
        if (clipTouched[P_HEAD]) {
            this.head.xRot = Mth.lerp(w, this.head.xRot, bX + deg2rad(clipPoseBuf[3 * P_HEAD]));
            this.head.yRot = Mth.lerp(w, this.head.yRot, bY + deg2rad(clipPoseBuf[3 * P_HEAD + 1]));
            this.head.zRot = Mth.lerp(w, this.head.zRot, bZ + deg2rad(clipPoseBuf[3 * P_HEAD + 2]));
        }
        if (clipTouched[P_R_ARM]) {
            blendRootedPart(this.rightArm, -5.0F, 2.0F, 0.0F, bX, bY, bZ,
                    clipPoseBuf[3 * P_R_ARM], clipPoseBuf[3 * P_R_ARM + 1], clipPoseBuf[3 * P_R_ARM + 2], w);
        }
        if (clipTouched[P_L_ARM]) {
            blendRootedPart(this.leftArm, 5.0F, 2.0F, 0.0F, bX, bY, bZ,
                    clipPoseBuf[3 * P_L_ARM], clipPoseBuf[3 * P_L_ARM + 1], clipPoseBuf[3 * P_L_ARM + 2], w);
        }
        if (clipTouched[P_R_LEG]) {
            blendRootedPart(this.rightLeg, -1.9F, 12.0F, 0.0F, bX, bY, bZ,
                    clipPoseBuf[3 * P_R_LEG], clipPoseBuf[3 * P_R_LEG + 1], clipPoseBuf[3 * P_R_LEG + 2], w);
        }
        if (clipTouched[P_L_LEG]) {
            blendRootedPart(this.leftLeg, 1.9F, 12.0F, 0.0F, bX, bY, bZ,
                    clipPoseBuf[3 * P_L_LEG], clipPoseBuf[3 * P_L_LEG + 1], clipPoseBuf[3 * P_L_LEG + 2], w);
        }
        if (clipTouched[P_R_FORE]) {
            this.rightArmLower.xRot = Mth.lerp(w, this.rightArmLower.xRot, deg2rad(clipPoseBuf[3 * P_R_FORE]));
            this.rightArmLower.yRot = Mth.lerp(w, this.rightArmLower.yRot, 0.0F);
            this.rightArmLower.zRot = Mth.lerp(w, this.rightArmLower.zRot, 0.0F);
        }
        if (clipTouched[P_L_FORE]) {
            this.leftArmLower.xRot = Mth.lerp(w, this.leftArmLower.xRot, deg2rad(clipPoseBuf[3 * P_L_FORE]));
            this.leftArmLower.yRot = Mth.lerp(w, this.leftArmLower.yRot, 0.0F);
            this.leftArmLower.zRot = Mth.lerp(w, this.leftArmLower.zRot, 0.0F);
        }
        if (clipTouched[P_R_LOWER]) {
            this.rightLegLower.xRot = Mth.lerp(w, this.rightLegLower.xRot, deg2rad(clipPoseBuf[3 * P_R_LOWER]));
            this.rightLegLower.yRot = Mth.lerp(w, this.rightLegLower.yRot, 0.0F);
            this.rightLegLower.zRot = Mth.lerp(w, this.rightLegLower.zRot, 0.0F);
        }
        if (clipTouched[P_L_LOWER]) {
            this.leftLegLower.xRot = Mth.lerp(w, this.leftLegLower.xRot, deg2rad(clipPoseBuf[3 * P_L_LOWER]));
            this.leftLegLower.yRot = Mth.lerp(w, this.leftLegLower.yRot, 0.0F);
            this.leftLegLower.zRot = Mth.lerp(w, this.leftLegLower.zRot, 0.0F);
        }
    }

    /** 根部跟随躯干联动的部件:位置与旋转都按权重混合(对齐 Bedrock 骨架的父子关系)。 */
    private void blendRootedPart(ModelPart part, float baseX, float baseY, float baseZ,
                                 float bX, float bY, float bZ,
                                 float pX, float pY, float pZ, float weight) {
        org.joml.Vector3f pos = new org.joml.Vector3f(baseX, baseY, baseZ);
        pos.rotateX(bX).rotateY(bY).rotateZ(bZ);
        part.x = Mth.lerp(weight, part.x, pos.x);
        part.y = Mth.lerp(weight, part.y, pos.y);
        part.z = Mth.lerp(weight, part.z, pos.z);
        part.xRot = Mth.lerp(weight, part.xRot, bX + deg2rad(pX));
        part.yRot = Mth.lerp(weight, part.yRot, bY + deg2rad(pY));
        part.zRot = Mth.lerp(weight, part.zRot, bZ + deg2rad(pZ));
    }

    private void buildTouched(HeroClip clip) {
        for (int j = 0; j < HeroClip.CANONICAL_JOINTS; j++) {
            clipTouched[j] = false;
        }
        clipTouched[P_HEAD] = clip.trackIndex(HeroClip.B_HEAD) >= 0;
        clipTouched[P_BODY] = clip.trackIndex(HeroClip.B_BODY) >= 0
                || clip.trackIndex(HeroClip.B_BSS_BODY) >= 0
                || clip.trackIndex(HeroClip.B_BSS_CHEST) >= 0;
        clipTouched[P_R_ARM] = clip.trackIndex(HeroClip.B_RIGHT_ARM) >= 0;
        clipTouched[P_L_ARM] = clip.trackIndex(HeroClip.B_LEFT_ARM) >= 0;
        clipTouched[P_R_FORE] = clip.trackIndex(HeroClip.B_RIGHT_FOREARM) >= 0;
        clipTouched[P_L_FORE] = clip.trackIndex(HeroClip.B_LEFT_FOREARM) >= 0;
        clipTouched[P_R_LEG] = clip.trackIndex(HeroClip.B_RIGHT_LEG) >= 0;
        clipTouched[P_L_LEG] = clip.trackIndex(HeroClip.B_LEFT_LEG) >= 0;
        clipTouched[P_R_LOWER] = clip.trackIndex(HeroClip.B_RIGHT_LOWER_LEG) >= 0;
        clipTouched[P_L_LOWER] = clip.trackIndex(HeroClip.B_LEFT_LOWER_LEG) >= 0;
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
        if (entity.getInvitedAction() != CookingService.INVITED_ACTION_COOK || entity.getInvitedPos() == null) {
            return false;
        }

        if (!CookingService.isCookwareStation(entity.level(), entity.getInvitedPos())) {
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
        ItemStack mainDisplay = CookingService.getCookMainHandDisplay(entity);
        ItemStack visualOffDisplay = entity.getVisualOffHandItem();
        ItemStack offDisplay = !visualOffDisplay.isEmpty() ? visualOffDisplay : CookingService.getCookOffhandDisplay(entity);
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

    /**
     * 推磨姿态。
     *
     * <p>要点是「绕圈走」而不是「原地踏步」：石磨每 tick 把 Hero 摆到圆周上（半径 2 格）并让它
     * 面朝切线方向，所以真实位移是圆周。这里直接复用行走环的腿部数学，只把相位绑到实际线速度上，
     * 再让上身压低前倾、双手扶住磨杆——看起来就是人被磨带着走，而不是僵尸式前伸。
     *
     * <p>步频按「圆周周长 ÷ 行走环每圈覆盖的距离」算，脚底位移与行走动画一致，不会打滑：
     * 半径 2 → 周长约 12.57 格，行走环一圈约 9.43 格，因此磨一圈腿要迈约 1.33 个循环。
     */
    private void setupMillstonePushAnim(HeroEntity entity, float ageInTicks) {        // 每条腿每 tick 前进多少"步态环"：与石磨摆位的真实线速度对齐。
        float lapsPerTick = 1.0F / Math.max(1.0F, CookingService.MILLSTONE_ROT_SPEED_TICK);
        float phase = positiveModulo(ageInTicks * lapsPerTick * MILLSTONE_CYCLES_PER_LAP, 1.0F);
        HeroLocomotion.computeWalkPose(phase, this.walkBuf);

        // 腿部：直接接管行走环，这是"像在走路"的关键。
        this.rightLeg.xRot = deg2rad(this.walkBuf[18]);
        this.leftLeg.xRot = deg2rad(this.walkBuf[21]);
        this.rightLegLower.xRot = deg2rad(this.walkBuf[24]);
        this.leftLegLower.xRot = deg2rad(this.walkBuf[27]);

        // 上身：压低前倾，重心压在磨杆上。躯干小幅左右摆，像在跟磨较劲。
        // 正 xRot = 前倾（与 vanilla 潜行 body.xRot = 0.5 同向）。
        float effort = Mth.sin(phase * (float) (Math.PI * 4.0));
        this.body.xRot = deg2rad(16.0F + this.walkBuf[3] * 0.5F);
        this.body.yRot = deg2rad(this.walkBuf[4] * 0.35F);
        this.body.zRot = deg2rad(this.walkBuf[5] * 0.5F + effort * 1.2F);

        // 头：由于躯干压低了，头要抬回来一点，保持看向前方（这是"不像僵尸"的关键）。
        this.head.xRot = deg2rad(-10.0F + this.walkBuf[0] * 0.4F);
        this.head.yRot = deg2rad(this.walkBuf[1] * 0.5F);
        this.head.zRot = deg2rad(this.walkBuf[2] * 0.4F);

        // 双臂：扶住磨杆的固定姿势（不是前伸僵直）。肘部明显弯下去，手落在胸口高度，
        // 这样读起来是"双手压着杆子被磨带着走"，而不是僵尸伸手。
        // 只有很小的推拉起伏——人靠腿在走，手臂主要是稳住杆子。
        float push = Mth.sin(phase * (float) (Math.PI * 2.0)) * 0.035F;
        this.rightArm.xRot = deg2rad(-49.0F + push * 3.0F);
        this.rightArm.yRot = deg2rad(-7.0F);
        this.rightArm.zRot = deg2rad(12.0F);
        this.rightArmLower.xRot = deg2rad(20.0F - push * 4.0F);

        this.leftArm.xRot = deg2rad(-49.0F - push * 3.0F);
        this.leftArm.yRot = deg2rad(7.0F);
        this.leftArm.zRot = deg2rad(-12.0F);
        this.leftArmLower.xRot = deg2rad(20.0F + push * 4.0F);

        // 躯干随步伐轻微上下起伏（走路的重量感）。
        float bob = this.walkBuf[31] * 0.35F;
        this.body.y += bob;
        this.head.y += bob;
        this.rightArm.y += bob;
        this.leftArm.y += bob;

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

        // 远程武器（弓/弩/枪）：瞄准姿势只在「真的在拉弓/开火」时接管，
        // 平时战斗待机与走位一律使用终末之诗的骨架（动作资源只有这一套）。
        if (HeroCombatWeaponHelper.isRangedLoadout(entity.getMainHandItem()) && entity.isUsingItem()) {
            applyRangedBattlePose(entity, limbSwing, walkAmount, ageInTicks);
            syncBattleTorsoAnchors();
            copyAllModelProperties();
            return;
        }

        // 无 Epic Fight 的战斗攻击段一律由终末之诗动作库驱动（主手武器不参与判断）：
        // 按攻击序号轮换库内全部普攻裁剪；接近/待机段仍走下方基础姿势。
        // 原先「非终末之诗武器 → 程序化挥砍」的回退动作已废弃并删除。
        int battleAction = entity.getBattleActionState();
        if (battleAction == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1
                || battleAction == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2) {
            HeroClip poemClip = HeroScytheComboLibrary.shouldDriveBattlePose(entity)
                    ? HeroScytheComboLibrary.clipForSerial(entity.getBattleActionSerial())
                    : null;
            if (poemClip != null) {
                float partialTick = ageInTicks - entity.tickCount;
                float elapsed = entity.getBattleActionTicks() + Math.max(0.0F, partialTick);
                int durationTicks = HeroScytheComboLibrary.actionDurationTicks(entity, battleAction);
                float progress = durationTicks <= 0 ? 1.0F : Mth.clamp(elapsed / durationTicks, 0.0F, 1.0F);
                // 前两 tick 从基础战斗架势淡入，连招切换不跳变
                float weight = Mth.clamp(elapsed / 2.0F, 0.0F, 1.0F);
                applyClipPose(poemClip, progress * poemClip.duration, weight);
            } else {
                // 动作库不可用：保持战斗架势，不再用程序化挥砍顶替
                applyBattleIdlePose(ageInTicks);
            }
            syncBattleTorsoAnchors();
            copyAllModelProperties();
            return;
        }

        switch (battleAction) {
            case HeroEntity.BATTLE_ACTION_APPROACH -> applyApproachPose(limbSwing, walkAmount, ageInTicks);
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

    // 原先的 applyComboPose（按进度程序化挥砍的轻击连段）与 getBattleActionDuration
    // 属于「非终末之诗武器」的默认回退攻击动作，已随动作统一到终末之诗而废弃删除。

    private void applyBattleHeight(float offsetY) {
        PlayerModelParts.anchorY(this, offsetY);
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

    private void copyAllModelProperties() {
        PlayerModelParts.syncOverlays(this);

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
