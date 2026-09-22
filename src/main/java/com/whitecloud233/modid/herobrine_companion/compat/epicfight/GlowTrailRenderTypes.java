package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 自持的加法混合发光拖尾渲染类型（1.20.1 Forge 移植版）。
 *
 * <p>与 EpicFight 自带的 {@code TRAIL_EFFECT} 渲染类型唯一区别是混合模式：
 * 原生用 {@code SRC_ALPHA / ONE_MINUS_SRC_ALPHA}（普通半透明，最多"被照亮"），
 * 这里用 {@code SRC_ALPHA / ONE}（加法混合）——亮色直接叠加进画面，
 * 配合 item skin 的 {@code block_light/sky_light=15} 满亮光照，在黑夜里真正发光。</p>
 */
public final class GlowTrailRenderTypes {
    private static final ConcurrentHashMap<ResourceLocation, ParticleRenderType> CACHE = new ConcurrentHashMap<>();

    public static ParticleRenderType get(ResourceLocation texture) {
        return CACHE.computeIfAbsent(texture, GlowTrailRenderTypes::create);
    }

    private static ParticleRenderType create(ResourceLocation texture) {
        // 与 EpicFight 1.20.1 TRAIL_EFFECT 相同的初始化，但 S 轴用 REPEAT 而非 CLAMP：
        // 基岩版终末之诗同款 ramp 的 U 方向首尾像素相同（无缝），刀光靠 U 轴滚动让电弧
        // 沿刀身流动；CLAMP 会把溢出的 U 拉成边缘色，流动到边界就断掉。T 轴仍必须 CLAMP，
        // 否则 V 方向上下边缘会互相渗透。
        RenderSystem.bindTexture(Minecraft.getInstance().getTextureManager().getTexture(texture).getId());
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return new ParticleRenderType() {
            @Override
            public void begin(BufferBuilder bufferBuilder, TextureManager textureManager) {
                RenderSystem.disableCull();
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                RenderSystem.depthMask(false);
                RenderSystem.setShader(GameRenderer::getParticleShader);
                RenderSystem.setShaderTexture(0, texture);
                bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            }

            @Override
            public void end(Tesselator tesselator) {
                tesselator.end();
                RenderSystem.enableCull();
            }

            @Override
            public String toString() {
                return "herobrine_companion:GLOW_TRAIL";
            }
        };
    }

    private GlowTrailRenderTypes() {
    }
}
