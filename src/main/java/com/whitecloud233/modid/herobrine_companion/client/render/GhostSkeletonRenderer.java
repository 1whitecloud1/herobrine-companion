package com.whitecloud233.modid.herobrine_companion.client.render;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.AbstractSkeleton;

public class GhostSkeletonRenderer extends SkeletonRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(HerobrineCompanion.MODID, "textures/entity/ghost_skeleton.png");

    public GhostSkeletonRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected RenderType getRenderType(AbstractSkeleton entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return bodyVisible ? RenderType.entityTranslucent(getTextureLocation(entity))
                : super.getRenderType(entity, bodyVisible, translucent, glowing);
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractSkeleton entity) {
        return TEXTURE;
    }
}
