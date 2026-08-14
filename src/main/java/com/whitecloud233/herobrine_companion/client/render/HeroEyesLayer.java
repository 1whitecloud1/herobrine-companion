package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class HeroEyesLayer extends RenderLayer<HeroEntity, PlayerModel<HeroEntity>> {

    public HeroEyesLayer(RenderLayerParent<HeroEntity, PlayerModel<HeroEntity>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HeroEntity livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // 动态选择眼睛发光贴图:内置皮肤用固定图;自定义皮肤用从其像素自动识别生成的对齐叠加图;无白眼则不渲染
        ResourceLocation eyes = HeroRenderer.getEyesTexture(livingEntity);
        if (eyes == null) {
            return;
        }
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.eyes(eyes));
        // RenderType.eyes 全亮发光(忽略光照)。15728880 = 满亮度;0xFFFFFFFF = 白色不透明
        this.getParentModel().renderToBuffer(poseStack, vertexConsumer, 15728880, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }
}
