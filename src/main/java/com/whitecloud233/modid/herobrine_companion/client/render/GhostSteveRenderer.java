package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.model.GhostSteveModel;
import com.whitecloud233.modid.herobrine_companion.entity.GhostSteveEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class GhostSteveRenderer extends LivingEntityRenderer<GhostSteveEntity, GhostSteveModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(HerobrineCompanion.MODID, "textures/entity/ghost_steve.png");

    public GhostSteveRenderer(EntityRendererProvider.Context context) {
        // [修复] 不再使用 PlayerModel，改为使用我们手写的 GhostSteveModel
        super(context, new GhostSteveModel(context.bakeLayer(GhostSteveModel.LAYER_LOCATION)), 0.5F);

        // 渲染手持物品，独立模型已实现 ArmedModel 接口，这部分代码可以完美工作
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(GhostSteveEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void renderNameTag(GhostSteveEntity entity, Component displayName, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 覆盖此方法并留空，以禁止渲染名字
    }
}