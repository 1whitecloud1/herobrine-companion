package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

public class DragonRendererWrapper extends EntityRenderer<Entity> {

    private final HeroDragonRenderer realRenderer;

    public DragonRendererWrapper(EntityRendererProvider.Context context) {
        super(context);
        this.realRenderer = new HeroDragonRenderer(context);
    }

    @Override
    public void render(Entity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity instanceof EnderDragon dragon) {
            this.realRenderer.render(dragon, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }
        // 如果是 EnderDragonPart，直接忽略，防止崩溃
    }

    @Override
    public ResourceLocation getTextureLocation(Entity entity) {
        if (entity instanceof EnderDragon dragon) {
            return this.realRenderer.getTextureLocation(dragon);
        }
        return null; 
    }
}