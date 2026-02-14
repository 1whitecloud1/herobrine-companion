package com.whitecloud233.herobrine_companion.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class EndRingDimensionEffects extends DimensionSpecialEffects {
    
    // 定义 6 张不同的贴图路径
    // 命名规范参考：real_sky_up, real_sky_down, real_sky_north...
    private static final ResourceLocation[] REAL_WORLD_TEXTURES = new ResourceLocation[] {
        ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/environment/real_world_down.png"),  // 0: 底面 (地面)
        ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/environment/real_world_up.png"),    // 1: 顶面 (天空)
        ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/environment/real_world_north.png"), // 2: 北面
        ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/environment/real_world_south.png"), // 3: 南面
        ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/environment/real_world_east.png"),  // 4: 东面
        ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/environment/real_world_west.png")   // 5: 西面
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
    public boolean renderSky(net.minecraft.client.multiplayer.ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, net.minecraft.client.Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        Tesselator tesselator = Tesselator.getInstance();
        
        // 循环绘制 6 个面
        for (int i = 0; i < 6; ++i) {
            // 关键修改：每次循环绑定不同的纹理
            RenderSystem.setShaderTexture(0, REAL_WORLD_TEXTURES[i]);
            
            // 注意：RenderSystem.setShaderTexture 改变状态后，必须重新 begin buffer
            // 因为 BufferBuilder 是一次性提交的，如果中途换贴图，通常建议分批绘制
            BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

            Matrix4f matrix4f = new Matrix4f(modelViewMatrix);
            
            // 旋转逻辑保持不变，确保面朝向正确
            switch (i) {
                case 1 -> matrix4f.rotateX((float) Math.PI);        // Top
                case 2 -> matrix4f.rotateX((float) Math.PI / 2);    // North
                case 3 -> matrix4f.rotateX(-(float) Math.PI / 2);   // South
                case 4 -> matrix4f.rotateZ((float) Math.PI / 2);    // East
                case 5 -> matrix4f.rotateZ(-(float) Math.PI / 2);   // West
            }

            bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F);
            bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 1.0F);
            bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(1.0F, 1.0F);
            bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(1.0F, 0.0F);

            // 绘制当前面
            try {
                BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        return true;
    }
}
