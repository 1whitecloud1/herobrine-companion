package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class HeroEyesLayer extends RenderLayer<HeroEntity, PlayerModel<HeroEntity>> {
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/entity/hero_eyes.png");

    public HeroEyesLayer(RenderLayerParent<HeroEntity, PlayerModel<HeroEntity>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HeroEntity livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // Use RenderType.eyes() which is standard for glowing eyes.
        // It uses additive blending or standard alpha blending depending on implementation.
        // To make it "glow" onto the face, the texture itself needs to have semi-transparent pixels around the eyes.
        
        // If you want a stronger "bloom" effect without shaders, you can try rendering it slightly offset or scaled,
        // but texture modification is the best way.
        
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.eyes(EYES));
        
        // Render the model with the eyes texture
        // 15728880 is full light (Sky 15, Block 15)
        this.getParentModel().renderToBuffer(poseStack, vertexConsumer, 15728880, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }
}
