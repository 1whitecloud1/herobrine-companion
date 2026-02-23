package com.whitecloud233.modid.herobrine_companion.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class EndRingDimensionEffects extends DimensionSpecialEffects {
    
    private static final ResourceLocation[] REAL_WORLD_TEXTURES = new ResourceLocation[] {
        new ResourceLocation(HerobrineCompanion.MODID, "textures/environment/real_world_down.png"),  // 0: 底面 (地面)
        new ResourceLocation(HerobrineCompanion.MODID, "textures/environment/real_world_up.png"),    // 1: 顶面 (天空)
        new ResourceLocation(HerobrineCompanion.MODID, "textures/environment/real_world_north.png"), // 2: 北面
        new ResourceLocation(HerobrineCompanion.MODID, "textures/environment/real_world_south.png"), // 3: 南面
        new ResourceLocation(HerobrineCompanion.MODID, "textures/environment/real_world_east.png"),  // 4: 东面
        new ResourceLocation(HerobrineCompanion.MODID, "textures/environment/real_world_west.png")   // 5: 西面
    };

    public EndRingDimensionEffects() {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
        return biomeFogColor;
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public boolean renderSky(net.minecraft.client.multiplayer.ClientLevel level, int ticks, float partialTick, PoseStack poseStack, net.minecraft.client.Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();
        
        Matrix4f modelViewMatrix = poseStack.last().pose();

        for (int i = 0; i < 6; ++i) {
            RenderSystem.setShaderTexture(0, REAL_WORLD_TEXTURES[i]);
            
            bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

            Matrix4f matrix4f = new Matrix4f(modelViewMatrix);
            
            switch (i) {
                case 1 -> matrix4f.rotateX((float) Math.PI);        // Top
                case 2 -> matrix4f.rotateX((float) Math.PI / 2);    // North
                case 3 -> matrix4f.rotateX(-(float) Math.PI / 2);   // South
                case 4 -> matrix4f.rotateZ((float) Math.PI / 2);    // East
                case 5 -> matrix4f.rotateZ(-(float) Math.PI / 2);   // West
            }

            bufferbuilder.vertex(matrix4f, -100.0F, -100.0F, -100.0F).uv(0.0F, 0.0F).endVertex();
            bufferbuilder.vertex(matrix4f, -100.0F, -100.0F, 100.0F).uv(0.0F, 1.0F).endVertex();
            bufferbuilder.vertex(matrix4f, 100.0F, -100.0F, 100.0F).uv(1.0F, 1.0F).endVertex();
            bufferbuilder.vertex(matrix4f, 100.0F, -100.0F, -100.0F).uv(1.0F, 0.0F).endVertex();

            try {
                tesselator.end();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        return true;
    }
}