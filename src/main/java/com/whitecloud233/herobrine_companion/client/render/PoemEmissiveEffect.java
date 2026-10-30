package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.MemoryStack;

/** Draws unlit weapon pixels after Iris final, with their submission-time view transforms. */
@EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PoemEmissiveEffect {
    public enum Material { SURFACE, TRAIL_BODY, TRAIL_GLOW }
    private static final List<Draw> DRAWS = new ArrayList<>();
    private static final List<ByteBufferBuilder> STORAGE = new ArrayList<>();
    private static ShaderInstance shader;
    private static boolean collecting;
    private static int width, height;
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        public VertexConsumer addVertex(float x, float y, float z) { return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    };
    private PoemEmissiveEffect() { }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        discardWorldFrame(); shader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "poem_emissive"), DefaultVertexFormat.NEW_ENTITY),
                    loaded -> shader = loaded);
        } catch (IOException error) { LogUtils.getLogger().error("Cannot load Poem emissive shader; using vanilla materials", error); }
    }

    public static void beginWorldFrame() {
        discardWorldFrame();
        collecting = shader != null && PoemShaderCompat.shadersEnabled();
        var main = Minecraft.getInstance().getMainRenderTarget(); width = main.width; height = main.height;
    }

    public static VertexConsumer buffer(MultiBufferSource fallback, RenderType type, ResourceLocation texture, Material material) {
        if (PoemShaderCompat.shadowPass()) return DISCARD;
        if (!collecting) return fallback.getBuffer(type);
        int index = DRAWS.size();
        if (index == STORAGE.size()) STORAGE.add(new ByteBufferBuilder(4096));
        ByteBufferBuilder storage = STORAGE.get(index);
        // Preserve the caller's vertex format, including the existing ribbon format.
        BufferBuilder builder = new BufferBuilder(storage, type.mode(), type.format());
        int[] viewport = new int[4];
        double[] depthRange = new double[2];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetDoublev(GL11.GL_DEPTH_RANGE, depthRange);
        DRAWS.add(new Draw(builder, storage, texture, material, new Matrix4f(RenderSystem.getProjectionMatrix()),
                new Matrix4f(RenderSystem.getModelViewMatrix()), RenderSystem.getVertexSorting(),
                viewport, depthRange, RenderSystem.getShaderColor().clone()));
        return builder;
    }

    public static void finishWorldFrame() {
        collecting = false;
        if (DRAWS.isEmpty()) return;
        PoemOutlineEffect.State state;
        int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try (MemoryStack stack = MemoryStack.stackPush()) { state = new PoemOutlineEffect.State(stack, viewport); }
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        try {
            var main = Minecraft.getInstance().getMainRenderTarget();
            if (shader == null || width != main.width || height != main.height) return;
            main.bindWrite(true); RenderSystem.disableScissor(); GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.colorMask(true, true, true, true); RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.disableCull();
            RenderSystem.setShader(() -> shader);
            for (Material material : Material.values()) for (Draw draw : DRAWS) {
                if (draw.material != material) continue;
                RenderSystem.setProjectionMatrix(draw.projection, draw.sorting);
                modelView.set(draw.modelView); RenderSystem.applyModelViewMatrix();
                int[] v = draw.viewport;
                RenderSystem.viewport(v[0], v[1], v[2], v[3]);
                GL11.glDepthRange(draw.depthRange[0], draw.depthRange[1]);
                RenderSystem.setShaderColor(draw.color[0], draw.color[1], draw.color[2], draw.color[3]);
                RenderSystem.setShaderTexture(0, draw.texture);
                shader.safeGetUniform("OpaqueSurface").set(material == Material.SURFACE ? 1 : 0);
                RenderSystem.depthMask(material == Material.SURFACE);
                RenderSystem.enableBlend(); RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, material == Material.TRAIL_GLOW ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_ZERO, GL11.GL_ONE);
                MeshData mesh = draw.builder.build(); draw.built = true;
                if (mesh == null) continue;
                if (material == Material.TRAIL_BODY) mesh.sortQuads(draw.storage, draw.sorting);
                BufferUploader.drawWithShader(mesh);
            }
        } finally {
            discardWorldFrame(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting); state.restore();
        }
    }

    public static void discardWorldFrame() {
        collecting = false;
        for (Draw draw : DRAWS) if (!draw.built) {
            MeshData mesh = draw.builder.build(); if (mesh != null) mesh.close();
        }
        DRAWS.clear(); STORAGE.forEach(ByteBufferBuilder::clear);
    }

    private static final class Draw {
        final BufferBuilder builder;
        final ByteBufferBuilder storage;
        final ResourceLocation texture;
        final Material material;
        final Matrix4f projection, modelView;
        final VertexSorting sorting;
        final int[] viewport;
        final double[] depthRange;
        final float[] color;
        boolean built;
        Draw(BufferBuilder builder, ByteBufferBuilder storage, ResourceLocation texture, Material material,
             Matrix4f projection, Matrix4f modelView, VertexSorting sorting, int[] viewport, double[] depthRange, float[] color) {
            this.builder = builder; this.storage = storage; this.texture = texture; this.material = material;
            this.projection = projection; this.modelView = modelView; this.sorting = sorting;
            this.viewport = viewport; this.depthRange = depthRange; this.color = color;
        }
    }
    @EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            Runnable clear = () -> { discardWorldFrame(); STORAGE.forEach(ByteBufferBuilder::close); STORAGE.clear(); };
            if (RenderSystem.isOnRenderThread()) clear.run(); else RenderSystem.recordRenderCall(clear::run);
        }
    }
}
