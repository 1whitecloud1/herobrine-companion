package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.whitecloud233.modid.herobrine_companion.client.animation.PoemTrailMesh;
import com.whitecloud233.modid.herobrine_companion.client.animation.StandalonePoemAnimation.Frame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Full-bright, depth-tested blade ribbons without an Epic Fight or GeckoLib dependency. */
public final class StandalonePoemTrailRenderer extends RenderType {
    /**
     * 贴图沿 U 轴滚动的速度（纹理周期 / 秒），与基岩版终末之诗 {@code hc_poem_trail}
     * 材质的 {@code uv_anim} 同值；电弧因此沿刀身流动，而不是死板地贴在刀上。
     */
    private static final float UV_SPEED = 0.65F;
    private static final ResourceLocation TEXTURE = new ResourceLocation("herobrine_companion",
            "textures/trail/poem_standalone_trail.png");
    private static final TransparencyStateShard GLOW = new TransparencyStateShard("poem_blade_glow", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });
    private static final RenderType BODY = create("herobrine_companion:standalone_poem_trail_body",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 4096, false, true,
            CompositeState.builder().setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                    .setTextureState(new TextureStateShard(TEXTURE, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(LIGHTMAP)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private static final RenderType TRAIL = create("herobrine_companion:standalone_poem_trail",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 4096, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                    .setTextureState(new TextureStateShard(TEXTURE, true, false))
                    .setTransparencyState(GLOW).setCullState(NO_CULL).setLightmapState(LIGHTMAP)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    /** 上一次设过 REPEAT 的纹理 id；换图或 F3+T 重载后 id 会变，需要重设一次。 */
    private static int repeatAppliedTo = -1;

    private StandalonePoemTrailRenderer() {
        super("poem_trail", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS,
                4096, false, false, () -> { }, () -> { });
    }

    public static void render(Frame frame, HumanoidModel<?> model, PoseStack stack, MultiBufferSource buffers) {
        if (frame.trail().isEmpty()) return;
        float scroll = scroll();
        enableRepeatOnce();
        PoemTrailMesh.renderBody(frame, model, stack, buffers.getBuffer(BODY), scroll);
        PoemTrailMesh.render(frame, model, stack, buffers.getBuffer(TRAIL), scroll);
    }

    /**
     * 基岩版同款 ramp 的 U 方向首尾像素相同（无缝），刀光靠 U 轴滚动让电弧流动。
     * {@code TextureStateShard} 只能给 blur/mipmap，拿不到 wrap，所以这里显式把
     * S 轴改成 REPEAT（T 轴保持 CLAMP，避免 V 方向上下边缘互相渗透）。
     * wrap 是纹理对象自身的属性，之后 RenderType 每次 bind 都不会把它重置掉。
     */
    private static void enableRepeatOnce() {
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        AbstractTexture texture = manager.getTexture(TEXTURE);
        int id = texture == null ? -1 : texture.getId();
        if (id < 0 || id == repeatAppliedTo) return;
        RenderSystem.bindTexture(id);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        repeatAppliedTo = id;
    }

    private static float scroll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0f;
        return (mc.level.getGameTime() + mc.getFrameTime()) / 20f * UV_SPEED;
    }
}
