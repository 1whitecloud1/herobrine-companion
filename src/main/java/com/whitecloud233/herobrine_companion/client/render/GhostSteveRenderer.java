package com.whitecloud233.herobrine_companion.client.render;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.model.GhostSteveModel;
import com.whitecloud233.herobrine_companion.entity.GhostSteveEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer; // 更推荐给怪物使用 MobRenderer
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class GhostSteveRenderer extends MobRenderer<GhostSteveEntity, GhostSteveModel> {

    // [1.21.1 特性] 必须使用 fromNamespaceAndPath
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/entity/ghost_steve.png");

    public GhostSteveRenderer(EntityRendererProvider.Context context) {
        super(context, new GhostSteveModel(context.bakeLayer(GhostSteveModel.LAYER_LOCATION)), 0.5F);

        // 绑定手持物品渲染层
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(GhostSteveEntity entity) {
        return TEXTURE;
    }

    // [1.21.1 优化] 直接重写 shouldShowName 并返回 false 来隐藏名称，彻底避免覆写 renderNameTag 时出现参数签名不匹配的问题
    @Override
    protected boolean shouldShowName(GhostSteveEntity entity) {
        return false;
    }
}