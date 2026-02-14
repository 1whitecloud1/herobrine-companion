package com.whitecloud233.herobrine_companion.client.model;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class HeroModel extends PlayerModel<HeroEntity> {

    // [新增] 自定义 LayerLocation，避免与原版 minecraft:player 冲突
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "hero"), "main");

    public HeroModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(HeroEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // 1. 调用父类 (原版行走逻辑)
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

        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float floatAmount = entity.getFloatingAmount(partialTick);
        
        // --- 头部细节 (保持不变) ---
        float headTilt = Mth.sin(ageInTicks * 0.05f) * 0.05f;
        headTilt += (netHeadYaw * 0.01f) * 0.2f;
        this.head.zRot = headTilt;
        this.hat.zRot = this.head.zRot;

        // =========================================================
        // [修复] 混合动画系统
        // =========================================================

        // --- 1. 定义行走状态 (Walk) ---
        float walkBodyY = 0.0F; 
        float walkBodyXRot = this.body.xRot; // 捕获原版的奔跑前倾角度

        // --- 2. 定义浮空状态 (Float) ---
        // 身体上下浮动
        float floatBodyY = -2.0F + Mth.sin(ageInTicks * 0.1f) * 3.0F;
        
        // [修复核心 1] 浮空时的身体旋转
        // 强制身体直立 (0.0F) 或者非常轻微的前倾 (0.1F)，绝对不要乘以 limbSwingAmount！
        // 这样就解决了"像披风一样乱飞"的问题
        float floatBodyXRot = 0.05F; 

        // 腿部动作 (保持下垂)
        float legLag = Mth.cos(ageInTicks * 0.1f) * 0.1f;
        float floatRightLegX = 0.3f + legLag; 
        float floatLeftLegX = 0.2f + legLag * 0.8f; 
        float floatLegZ = 0.05f;

        // 手臂动作 (呼吸感)
        float armBreath = Mth.sin(ageInTicks * 0.06f) * 0.1f;
        float floatRightArmZ = 0.2f + armBreath;
        float floatLeftArmZ = -0.2f - armBreath; 
        float floatArmX = -0.2f + armBreath * 0.5f;

        // --- 3. 插值混合 (Lerping) ---
        
        if (!entity.isCrouching()) {
            // A. 位置混合 (解决上下分离)
            this.body.y = Mth.lerp(floatAmount, walkBodyY, floatBodyY);
            
            // B. [修复核心 2] 旋转混合 (解决腰部断裂)
            // 当浮空程度变高时，强行把身体原本的"奔跑前倾"过渡到"直立"
            this.body.xRot = Mth.lerp(floatAmount, walkBodyXRot, floatBodyXRot);
            // 同时消除身体的左右扭动，增加神性稳重感
            this.body.yRot = Mth.lerp(floatAmount, this.body.yRot, 0.0F);

            // C. 应用到其他部位
            this.head.y = this.body.y;
            this.rightArm.y = 2.0F + this.body.y;
            this.leftArm.y = 2.0F + this.body.y;
            this.rightLeg.y = 12.0F + this.body.y;
            this.leftLeg.y = 12.0F + this.body.y;
            
            // D. 同步外层
            this.jacket.y = this.body.y;
            this.hat.y = this.head.y;
            this.rightSleeve.y = this.rightArm.y;
            this.leftSleeve.y = this.leftArm.y;
            this.rightPants.y = this.rightLeg.y;
            this.leftPants.y = this.leftLeg.y;
        }

        // 腿部混合
        this.rightLeg.xRot = Mth.lerp(floatAmount, this.rightLeg.xRot, floatRightLegX);
        this.leftLeg.xRot = Mth.lerp(floatAmount, this.leftLeg.xRot, floatLeftLegX);
        
        this.rightLeg.yRot = Mth.lerp(floatAmount, this.rightLeg.yRot, 0.0F); // 浮空时腿不要乱转
        this.leftLeg.yRot = Mth.lerp(floatAmount, this.leftLeg.yRot, 0.0F);
        
        this.rightLeg.zRot = Mth.lerp(floatAmount, 0.0F, floatLegZ);
        this.leftLeg.zRot = Mth.lerp(floatAmount, 0.0F, -floatLegZ);

        // 手臂混合
        // 定义行走时的手臂摆动幅度 (需要重新计算，因为上面没有定义 walkRightArmZ)
        float walkRightArmZ = 0.0F; // 原版行走时 Z 轴摆动很小，可以忽略或设为 0
        float walkLeftArmZ = 0.0F;

        if (this.attackTime <= 0 && this.rightArmPose == ArmPose.EMPTY) {
            this.rightArm.xRot = Mth.lerp(floatAmount, this.rightArm.xRot, floatArmX);
            this.rightArm.zRot = Mth.lerp(floatAmount, walkRightArmZ, floatRightArmZ);
        }
        if (this.attackTime <= 0 && this.leftArmPose == ArmPose.EMPTY) {
            this.leftArm.xRot = Mth.lerp(floatAmount, this.leftArm.xRot, floatArmX);
            this.leftArm.zRot = Mth.lerp(floatAmount, walkLeftArmZ, floatLeftArmZ);
        }

        // 最后同步所有属性
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
        // 强制低头一点点，向右看一点点
        this.head.xRot = Mth.lerp(blend, this.head.xRot, 0.3F); 
        this.head.yRot = Mth.lerp(blend, this.head.yRot, -0.4F); // 负数是向右(模型视角)

        // 2. 右臂：抬起姿态 (Holding the screen)
        // xRot: -1.5F (抬平，约90度)
        // yRot: -0.6F (向内收，放在脸前)
        float baseArmX = -1.5F;
        float baseArmY = -0.5F;

        // 3. 模拟“打字”微动 (Typing Jitter)
        // 使用高频的正弦波，加上一点随机噪音
        // 速度 0.8F (很快)，幅度 0.05F (很小)
        float typeAction = Mth.sin(ageInTicks * 0.8F) * 0.05F;
        
        // 偶尔加一点剧烈的“点击”动作 (模拟敲回车)
        if (entity.getRandom().nextFloat() < 0.1F) {
            typeAction += 0.15F;
        }

        // 应用到右臂
        // 我们把抖动加在 xRot (上下点按) 和 zRot (手腕转动) 上
        this.rightArm.xRot = Mth.lerp(blend, this.rightArm.xRot, baseArmX + typeAction);
        this.rightArm.yRot = Mth.lerp(blend, this.rightArm.yRot, baseArmY);
        // zRot 微动能增加灵动感
        this.rightArm.zRot = Mth.lerp(blend, this.rightArm.zRot, typeAction * 0.5F);

        // 4. 左臂：自然下垂或背在身后
        // 让他看起来很从容，单手操作
        this.leftArm.xRot = Mth.lerp(blend, this.leftArm.xRot, 0.2F); // 稍微往后摆
        this.leftArm.zRot = Mth.lerp(blend, this.leftArm.zRot, 0.1F); // 稍微张开

        // 最后同步所有属性
        copyAllModelProperties();
    }

    private void setupThunderAnim(HeroEntity entity, float ageInTicks) {
        float progress = entity.getThunderProgress(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
        
        // 如果不在动作中，直接返回
        if (progress <= 0.01F) return;

        // --- 1. 计算平滑曲线 (SmoothStep) ---
        // 这会让动作看起来不生硬
        float smooth = progress * progress * (3.0F - 2.0F * progress);
        
        // --- 2. 肢体动作 ---

        // A. 右臂：高举擎天 (The Thunder Arm)
        // 目标：竖直向上 (-180度 = -3.14F)，稍微向外张开
        float targetRightArmX = -3.2F; // 甚至超过180度，向后一点点，更张狂
        float targetRightArmZ = 0.2F;  // 向外张开

        this.rightArm.xRot = Mth.lerp(smooth, this.rightArm.xRot, targetRightArmX);
        this.rightArm.zRot = Mth.lerp(smooth, this.rightArm.zRot, targetRightArmZ);
        // 修正手腕，让镰刀/物品垂直
        this.rightArm.yRot = Mth.lerp(smooth, this.rightArm.yRot, 0.0F);


        // B. 左臂：握拳下压 (Counter Balance)
        // 另一只手用力下压，增加力量感
        this.leftArm.xRot = Mth.lerp(smooth, this.leftArm.xRot, 0.8F); // 向下前伸
        this.leftArm.zRot = Mth.lerp(smooth, this.leftArm.zRot, -0.4F); // 向外张开


        // C. 头部：仰望天空 (Look at the Sky)
        // 目标：仰视 (-1.2F)
        this.head.xRot = Mth.lerp(smooth, this.head.xRot, -1.2F);
        // 锁定头部旋转，不让它乱看玩家，而是看天
        this.head.yRot = Mth.lerp(smooth, this.head.yRot, 0.0F);


        // D. 脊柱/身体：后仰挺胸 (Arch Back)
        // 这是一个展现力量的关键细节
        this.body.xRot = Mth.lerp(smooth, this.body.xRot, -0.3F); // 后仰
        this.body.y = Mth.lerp(smooth, this.body.y, -2.0F); // 身体稍微被能量提起来一点点


        // --- 3. 能量过载震颤 (Overload Shake) ---
        // 只有在动作后半段 (progress > 0.6) 才开始抖动
        if (progress > 0.6F) {
            // 抖动幅度随进度增加
            float shakeIntensity = (progress - 0.6F) * 0.05F;
            
            // 使用高频 sin 函数模拟电流通过身体
            float shakeX = Mth.sin(ageInTicks * 2.5F) * shakeIntensity;
            float shakeZ = Mth.cos(ageInTicks * 2.5F) * shakeIntensity;

            // 应用抖动到所有部件
            this.rightArm.xRot += shakeX * 2.0F; // 手臂抖动最剧烈
            this.rightArm.zRot += shakeZ;
            
            this.head.yRot += shakeX; // 头不自觉地摆动
            
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
