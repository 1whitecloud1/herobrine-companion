package com.whitecloud233.herobrine_companion.compat.epicfight;

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

import java.util.concurrent.ConcurrentHashMap;

/**
 * 自持的加法混合发光拖尾渲染类型（仿 bloom 的"泛光"效果）。
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
        // 贴图 wrap：S 轴必须 REPEAT，T 轴必须 CLAMP_TO_EDGE。
        // 基岩版同款 ramp 的 U 方向首尾像素相同（无缝），刀光靠 U 轴滚动让电弧沿刀身流动，
        // CLAMP 会把溢出的 U 拉成边缘色，流动到边界就断掉；V 轴则相反，上下边缘不透明区
        // 若 REPEAT 会互相渗透。注意 10497=GL_REPEAT、33071=GL_CLAMP_TO_EDGE，别写反。
        RenderSystem.bindTexture(Minecraft.getInstance().getTextureManager().getTexture(texture).getId());
        RenderSystem.texParameter(3553, 10242, 10497); // wrap S = GL_REPEAT
        RenderSystem.texParameter(3553, 10243, 33071); // wrap T = GL_CLAMP_TO_EDGE
        return new ParticleRenderType() {
            @Override
            public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
                RenderSystem.disableCull();
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                RenderSystem.depthMask(false);
                RenderSystem.setShader(GameRenderer::getParticleShader);
                RenderSystem.setShaderTexture(0, texture);
                return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
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
