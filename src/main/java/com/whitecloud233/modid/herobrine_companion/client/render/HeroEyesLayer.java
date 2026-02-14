package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;

public class HeroEyesLayer extends RenderLayer<HeroEntity, PlayerModel<HeroEntity>> {
    private static final ResourceLocation EYES = new ResourceLocation(HerobrineCompanion.MODID, "textures/entity/hero_eyes.png");

    public HeroEyesLayer(RenderLayerParent<HeroEntity, PlayerModel<HeroEntity>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HeroEntity livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.eyes(EYES));
        
        // [新增] 动态调整眼睛亮度
        // 如果环境光很暗，眼睛会显得更亮 (通过 alpha 或者 color 调整，但 RenderType.eyes 默认是全亮的)
        // 这里我们保持全亮，因为 RenderType.eyes 本身就是发光的 (ignore light)
        
        int blockLight = livingEntity.level().getBrightness(LightLayer.BLOCK, livingEntity.blockPosition());
        int skyLight = livingEntity.level().getBrightness(LightLayer.SKY, livingEntity.blockPosition());
        
        // 如果光照很低，我们稍微增强一点渲染效果 (例如渲染两次)
        if (blockLight < 7 && skyLight < 7) {
             // 渲染第一层
             this.getParentModel().renderToBuffer(poseStack, vertexConsumer, 15728880, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
             
             // 稍微膨胀一点渲染第二层，模拟光晕 (Hack)
             poseStack.pushPose();
             // 稍微放大一点，产生发光/模糊边缘的效果
             float scale = 1.02f; 
             poseStack.scale(scale, scale, scale);
             // RenderType.eyes 是 additive 的，所以叠加会变亮
             this.getParentModel().renderToBuffer(poseStack, vertexConsumer, 15728880, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 0.5F);
             poseStack.popPose();
        } else {
             this.getParentModel().renderToBuffer(poseStack, vertexConsumer, 15728880, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
