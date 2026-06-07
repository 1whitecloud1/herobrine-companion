package com.whitecloud233.modid.herobrine_companion.destructiongod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.destructiongod.client.model.DestructionGodHerobrineModel;
import com.whitecloud233.modid.herobrine_companion.destructiongod.entity.DestructionGodHerobrineEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class DestructionGodEyesLayer extends RenderLayer<DestructionGodHerobrineEntity, DestructionGodHerobrineModel> {
    private static final ResourceLocation EYES = ResourceLocation.tryParse(HerobrineCompanion.MODID + ":textures/entity/hero_eyes.png");

    public DestructionGodEyesLayer(RenderLayerParent<DestructionGodHerobrineEntity, DestructionGodHerobrineModel> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, DestructionGodHerobrineEntity entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.eyes(EYES));
        float alpha = entity.getBossPhase() >= DestructionGodHerobrineEntity.PHASE_4 ? 1.0F : 0.85F;
        this.getParentModel().renderToBuffer(poseStack, vertexConsumer, 15728880, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
    }
}


