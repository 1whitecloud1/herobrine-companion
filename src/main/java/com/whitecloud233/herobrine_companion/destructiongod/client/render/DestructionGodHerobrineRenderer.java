package com.whitecloud233.herobrine_companion.destructiongod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.destructiongod.client.model.DestructionGodHerobrineModel;
import com.whitecloud233.herobrine_companion.destructiongod.entity.DestructionGodHerobrineEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class DestructionGodHerobrineRenderer extends LivingEntityRenderer<DestructionGodHerobrineEntity, DestructionGodHerobrineModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.tryParse(HerobrineCompanion.MODID + ":textures/entity/herobrine.png");

    public DestructionGodHerobrineRenderer(EntityRendererProvider.Context context) {
        super(context, new DestructionGodHerobrineModel(context.bakeLayer(ModelLayers.PLAYER)), 0.65F);
        this.addLayer(new DestructionGodEyesLayer(this));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(DestructionGodHerobrineEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(DestructionGodHerobrineEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.isCastingSkill()) {
            float hover = entity.getSkillState() == DestructionGodHerobrineEntity.SKILL_WORLD_COLLAPSE ? 0.18F : 0.08F;
            poseStack.translate(0.0D, hover, 0.0D);
        }
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected void renderNameTag(DestructionGodHerobrineEntity entity, Component displayName, PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick) {
    }
}


