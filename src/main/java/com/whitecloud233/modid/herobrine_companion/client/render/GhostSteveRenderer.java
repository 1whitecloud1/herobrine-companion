package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.GhostSteveEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class GhostSteveRenderer extends LivingEntityRenderer<GhostSteveEntity, PlayerModel<GhostSteveEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(HerobrineCompanion.MODID, "textures/entity/ghost_steve.png");

    public GhostSteveRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        // [修复] 添加手持物品渲染层
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
