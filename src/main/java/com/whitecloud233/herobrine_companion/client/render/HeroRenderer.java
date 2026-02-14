package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.model.HeroModel;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public class HeroRenderer extends LivingEntityRenderer<HeroEntity, PlayerModel<HeroEntity>> {
    
    private static final ResourceLocation HERO_TEXTURE = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/entity/hero.png");
    private static final ResourceLocation HEROBRINE_TEXTURE = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/entity/herobrine.png");

    public HeroRenderer(EntityRendererProvider.Context context) {
        super(context, new HeroModel(context.bakeLayer(HeroModel.LAYER_LOCATION), false), 0.5f);
        this.addLayer(new HeroEyesLayer(this));
        // [新增] 添加持握物品渲染层，用于抚摸镰刀动画
        this.addLayer(new HeroHeldItemLayer(this, context.getItemInHandRenderer()));
        // [新增] 添加全息屏渲染层
        this.addLayer(new HeroHologramLayer(this));
        // [新增] 添加雷电电弧渲染层
        // [修复] 现在 HeroChargedLayer 的泛型已经改为 PlayerModel<HeroEntity>，可以直接添加了
        this.addLayer(new HeroChargedLayer(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(HeroEntity entity) {
        // [修复] End Ring 维度强制显示 Herobrine 皮肤
        if (entity.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
            return HEROBRINE_TEXTURE;
        }

        int variant = entity.getSkinVariant();
        if (variant == HeroEntity.SKIN_HEROBRINE) {
            return HEROBRINE_TEXTURE;
        } else if (variant == HeroEntity.SKIN_HERO) {
            return HERO_TEXTURE;
        }
        
        // Auto mode (default)
        return HERO_TEXTURE;
    }

    @Nullable
    @Override
    protected RenderType getRenderType(HeroEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        // [修复] 使用 entityCutoutNoCull 以支持皮肤外层透明度，同时保持光照
        // 原先使用 entitySolid 会导致皮肤外层（如外套、袖子）的透明部分被渲染为黑色不透明，从而遮挡内部皮肤
        return RenderType.entityCutoutNoCull(this.getTextureLocation(entity));
    }
    
    @Override
    public void render(HeroEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // --- 浮空逻辑 ---
        float floatAmount = entity.getFloatingAmount(partialTicks);
        if (floatAmount > 0.01f) {
            float ageInTicks = entity.tickCount + partialTicks;
            float floatHeight = Mth.sin(ageInTicks * 0.1f) * 0.05f * floatAmount; 
            float baseOffset = 0.05f * floatAmount;
            poseStack.translate(0.0D, floatHeight + baseOffset, 0.0D); 
        }

        // --- 故障残影逻辑 ---
        boolean isGlitching = entity.isGlitching();

        // [新增] 1. 绘制真身 (带高频闪现抖动)
        poseStack.pushPose();
        if (isGlitching) {
            // 在每一帧渲染时都应用随机位移，实现比 tick 更快的视觉闪烁
            double mainJitterX = (entity.getRandom().nextDouble() - 0.5) * 0.5; 
            double mainJitterY = (entity.getRandom().nextDouble() - 0.5) * 0.5;
            double mainJitterZ = (entity.getRandom().nextDouble() - 0.5) * 0.5;
            poseStack.translate(mainJitterX, mainJitterY, mainJitterZ);
        }
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        poseStack.popPose();

        // 2. 如果在故障状态，绘制红蓝残影
        if (isGlitching) {
            float ageInTicks = entity.tickCount + partialTicks;
            // 必须手动设置模型的动作，否则影子会摆大字
            this.getModel().setupAnim(entity, 0, 0, ageInTicks, entity.getYRot(), entity.getXRot());

            // --- 红色残影 (随机闪现) ---
            poseStack.pushPose();
            
            // 1. 【核心修复】翻转模型 + 高度修正
            poseStack.scale(-1.0F, -1.0F, 1.0F); 
            poseStack.translate(0.0D, -1.501D, 0.0D); 

            // 2. 应用位移 (故障闪现)
            // [修改] 使用随机偏移代替固定平移，实现闪现效果
            double rX = (entity.getRandom().nextDouble() - 0.5) * 0.8; // 范围更大
            double rY = (entity.getRandom().nextDouble() - 0.5) * 0.5;
            double rZ = (entity.getRandom().nextDouble() - 0.5) * 0.8;
            poseStack.translate(rX, rY, rZ); 
            
            VertexConsumer redBuffer = buffer.getBuffer(RenderType.entityTranslucent(this.getTextureLocation(entity)));
            // [修复] 使用 int color 版本: 0x80FF0000 (Alpha=128, R=255, G=0, B=0)
            this.getModel().renderToBuffer(poseStack, redBuffer, packedLight, OverlayTexture.NO_OVERLAY, 0x80FF0000);
            poseStack.popPose();

            // --- 蓝色残影 (随机闪现) ---
            poseStack.pushPose();
            
            // 1. 翻转 + 高度修正
            poseStack.scale(-1.0F, -1.0F, 1.0F); 
            poseStack.translate(0.0D, -1.501D, 0.0D); 
            
            // 2. 故障偏移 (随机闪现)
            // [修改] 使用随机偏移代替固定平移
            double bX = (entity.getRandom().nextDouble() - 0.5) * 0.8;
            double bY = (entity.getRandom().nextDouble() - 0.5) * 0.5;
            double bZ = (entity.getRandom().nextDouble() - 0.5) * 0.8;
            poseStack.translate(bX, bY, bZ);
            
            VertexConsumer blueBuffer = buffer.getBuffer(RenderType.entityTranslucent(this.getTextureLocation(entity)));
            // [修复] 使用 int color 版本: 0x8000FFFF (Alpha=128, R=0, G=255, B=255)
            this.getModel().renderToBuffer(poseStack, blueBuffer, packedLight, OverlayTexture.NO_OVERLAY, 0x8000FFFF);
            poseStack.popPose();
        }
    }

    @Override
    protected void renderNameTag(HeroEntity entity, Component displayName, PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick) {
        double d0 = this.entityRenderDispatcher.distanceToSqr(entity);
        if (d0 <= 4096.0D) {
            boolean isSneaking = !entity.isDiscrete();
            float height = entity.getBbHeight() + 0.5F;
            int yOffset = "deadmau5".equals(displayName.getString()) ? -10 : 0;

            poseStack.pushPose();
            poseStack.translate(0.0D, (double)height, 0.0D);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);
            
            Matrix4f matrix4f = poseStack.last().pose();
            float f1 = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
            int j = (int)(f1 * 255.0F) << 24;
            
            float xOffset = (float)(-this.getFont().width(displayName) / 2);
            
            Font.DisplayMode displayMode = isSneaking ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;

            this.getFont().drawInBatch(
                displayName, 
                xOffset, 
                (float)yOffset, 
                0xFFFF0000, 
                false, 
                matrix4f, 
                buffer, 
                displayMode, 
                j, 
                packedLight
            );

            if (isSneaking) {
                this.getFont().drawInBatch(
                    displayName, 
                    xOffset, 
                    (float)yOffset, 
                    -1, 
                    false, 
                    matrix4f, 
                    buffer, 
                    Font.DisplayMode.NORMAL, 
                    0, 
                    packedLight
                );
            }

            poseStack.popPose();
        }
    }
}