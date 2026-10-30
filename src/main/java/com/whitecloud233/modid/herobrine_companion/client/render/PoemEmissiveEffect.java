package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.MemoryStack;

/** Unlit weapon surfaces and ribbons, replayed after a shader pack's final composite. */
@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PoemEmissiveEffect {
    public enum Material { SURFACE, TRAIL_BODY, TRAIL_GLOW }

    private static final List<Draw> DRAWS = new ArrayList<>();
    private static final List<BufferBuilder> BUILDERS = new ArrayList<>();
    private static ShaderInstance shader;
    private static boolean collecting;
    private static int width, height;
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        public VertexConsumer vertex(double x, double y, double z) { return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() { }
        public void defaultColor(int r, int g, int b, int a) { }
        public void unsetDefaultColor() { }
    };

    private PoemEmissiveEffect() { }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        discardWorldFrame();
        shader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(HerobrineCompanion.MODID, "poem_emissive"), DefaultVertexFormat.NEW_ENTITY),
                    loaded -> shader = loaded);
        } catch (IOException exception) {
            LogUtils.getLogger().error("Failed to load Poem emissive shader; using vanilla materials", exception);
        }
    }

    public static void beginWorldFrame() {
        discardWorldFrame();
        collecting = shader != null && PoemShaderCompat.shadersEnabled();
        var main = Minecraft.getInstance().getMainRenderTarget();
        width = main.width;
        height = main.height;
    }

    public static VertexConsumer buffer(MultiBufferSource fallback, RenderType type,
                                        ResourceLocation texture, Material material) {
        if (PoemShaderCompat.shadowPass()) return DISCARD;
        if (!collecting || StandalonePoemRenderer.auxiliaryFirstPersonBody()) return fallback.getBuffer(type);
        // Capture at vertex submission, not at the batcher's later flush. In particular,
        // Oculus restores the world projection before flushing its translucent hand batch.
        int index = DRAWS.size();
        BufferBuilder builder;
        if (index < BUILDERS.size()) builder = BUILDERS.get(index);
        else {
            builder = new BufferBuilder(4096);
            if (index < 64) BUILDERS.add(builder);
        }
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        DRAWS.add(new Draw(builder, texture, material, new Matrix4f(RenderSystem.getProjectionMatrix()),
                new Matrix4f(RenderSystem.getModelViewMatrix()), RenderSystem.getVertexSorting()));
        return builder;
    }

    public static void finishWorldFrame() {
        collecting = false;
        if (DRAWS.isEmpty()) return;
        PoemOutlineEffect.State state;
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            state = new PoemOutlineEffect.State(stack, viewport);
        }
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            var main = Minecraft.getInstance().getMainRenderTarget();
            if (shader == null || width != main.width || height != main.height) return;
            main.bindWrite(true);
            RenderSystem.disableScissor();
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableCull();
            RenderSystem.setShader(() -> shader);
            // Opaque luminous pixels must fill the holes removed from the base texture
            // and occlude ribbons behind the blade. Ribbons themselves never write depth.
            for (Material material : Material.values()) {
                for (Draw draw : DRAWS) {
                    if (draw.material != material) continue;
                    RenderSystem.setProjectionMatrix(draw.projection, draw.sorting);
                    modelView.last().pose().set(draw.modelView);
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.setShaderTexture(0, draw.texture);
                    shader.safeGetUniform("OpaqueSurface").set(material == Material.SURFACE ? 1 : 0);
                    // Blend belongs to this draw, not to the shader's cached BlendMode.
                    shader.apply();
                    RenderSystem.depthMask(material == Material.SURFACE);
                    RenderSystem.enableBlend();
                    RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                    RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,
                            material == Material.TRAIL_GLOW ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA,
                            GL11.GL_ZERO, GL11.GL_ONE);
                    if (material == Material.TRAIL_BODY) draw.builder.setQuadSorting(draw.sorting);
                    BufferUploader.drawWithShader(draw.builder.end());
                }
            }
        } finally {
            discardWorldFrame();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            state.restore();
        }
    }

    public static void discardWorldFrame() {
        collecting = false;
        for (Draw draw : DRAWS) {
            if (draw.builder.building()) {
                var vertices = draw.builder.end();
                vertices.release();
            }
            draw.builder.discard();
        }
        DRAWS.clear();
    }

    private record Draw(BufferBuilder builder, ResourceLocation texture, Material material,
                        Matrix4f projection, Matrix4f modelView, VertexSorting sorting) { }

    @Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() { }
        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            Runnable clear = () -> { discardWorldFrame(); BUILDERS.clear(); };
            if (RenderSystem.isOnRenderThread()) clear.run(); else RenderSystem.recordRenderCall(clear::run);
        }
    }
}
