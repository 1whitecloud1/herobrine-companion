package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** A weapon-only silhouette with a one-pixel, depth-tested exterior edge. */
public final class PoemOutlineEffect {
    private static final ResourceLocation NATIVE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HerobrineCompanion.MODID, "textures/item/poem_of_the_end_outline.png");
    // Iris wrappers delegate setup/clear but not custom RenderType.draw; keep the mask out of caller buffers.
    private static final ByteBufferBuilder MASK_BUFFER = new ByteBufferBuilder(262144);
    private static final ByteBufferBuilder QUAD_BUFFER = new ByteBufferBuilder(256);
    private static ShaderInstance maskShader, compositeShader;
    private static RenderTarget mask;
    private static RenderTarget worldMask;
    private static boolean collectingWorld, worldMaskDirty;
    private static boolean replayingWorld;
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final List<ByteBufferBuilder> WORLD_BUFFERS = new ArrayList<>();
    private static boolean unavailable;

    private PoemOutlineEffect() { }

    public static boolean enabled() {
        return com.whitecloud233.herobrine_companion.config.Config.poemOutlineEnabled;
    }

    public static void beginWorldFrame() {
        discardWorldFrame();
        if (!enabled()) { releaseTargets(); return; }
        collectingWorld = PoemShaderCompat.shadersEnabled();
    }

    public static void discardWorldFrame() {
        collectingWorld = false;
        worldMaskDirty = false;
        replayingWorld = false;
        for (Pending pending : PENDING) if (!pending.consumed) pending.mesh.close();
        PENDING.clear();
        WORLD_BUFFERS.forEach(ByteBufferBuilder::clear);
    }

    /** Iris has finished both hand passes and its final composite; scene depth still exists. */
    public static void finishWorldFrame() {
        collectingWorld = false;
        if (PENDING.isEmpty() && !worldMaskDirty) return;
        // Never bind/apply our mask shader while Iris owns the world passes: Iris
        // can reroute unknown shaders to its gbuffer and lock color/depth writes.
        // Replay the geometry, with its original matrices, only after Iris final.
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        int[] originalViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, originalViewport);
        modelView.pushMatrix();
        replayingWorld = true;
        try {
            if (enabled() && !unavailable) for (int i = 0; i < PENDING.size(); i++) {
                Pending pending = PENDING.get(i);
                RenderSystem.setProjectionMatrix(pending.projection, pending.sorting);
                modelView.set(pending.modelView); RenderSystem.applyModelViewMatrix();
                int[] v = pending.viewport;
                RenderSystem.viewport(v[0], v[1], v[2], v[3]);
                pending.consumed = true;
                draw(pending.texture, pending.mesh);
            }
        } finally {
            replayingWorld = false;
            modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.viewport(originalViewport[0], originalViewport[1], originalViewport[2], originalViewport[3]);
            for (Pending pending : PENDING) if (!pending.consumed) pending.mesh.close();
            PENDING.clear(); WORLD_BUFFERS.forEach(ByteBufferBuilder::clear);
        }
        if (!enabled() || !worldMaskDirty || worldMask == null || unavailable) return;
        worldMaskDirty = false;
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        State state;
        try (MemoryStack stack = MemoryStack.stackPush()) { state = new State(stack, viewport); }
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            if (main.width != worldMask.width || main.height != worldMask.height) return;
            main.bindWrite(true);
            RenderSystem.disableScissor();
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            compositeShader.safeGetUniform("MaskBounds").set(0f, 0f, (float) main.width, (float) main.height);
            composite(worldMask, new int[] {0, 0, main.width, main.height}, main.width, main.height);
        } catch (RuntimeException exception) {
            failed(exception);
        } finally { state.restore(); }
    }

    public static void render(Consumer<VertexConsumer> geometry) {
        render(NATIVE_TEXTURE, geometry);
    }

    public static void render(ResourceLocation texture, Consumer<VertexConsumer> geometry) {
        if (!enabled() || unavailable || maskShader == null || compositeShader == null || PoemShaderCompat.shadowPass()) return;
        if (collectingWorld) {
            int index = PENDING.size();
            if (index == WORLD_BUFFERS.size()) WORLD_BUFFERS.add(new ByteBufferBuilder(16384));
            ByteBufferBuilder storage = WORLD_BUFFERS.get(index);
            BufferBuilder builder = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            geometry.accept(builder);
            MeshData mesh = builder.build();
            if (mesh == null) { storage.clear(); return; }
            int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            PENDING.add(new Pending(mesh, texture, new Matrix4f(RenderSystem.getProjectionMatrix()),
                    new Matrix4f(RenderSystem.getModelViewMatrix()), RenderSystem.getVertexSorting(), viewport));
            return;
        }
        BufferBuilder vertices = new BufferBuilder(MASK_BUFFER, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        try {
            geometry.accept(vertices);
            draw(texture, vertices.build());
        } finally {
            MASK_BUFFER.clear();
        }
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, name);
    }

    private static void releaseTargets() {
        if (mask != null) mask.destroyBuffers();
        if (worldMask != null) worldMask.destroyBuffers();
        mask = null;
        worldMask = null;
        discardWorldFrame();
    }

    private static RenderTarget ensureTarget(int width, int height, boolean deferred) {
        RenderTarget target = deferred ? worldMask : mask;
        if (target != null && target.width == width && target.height == height) return target;
        if (target != null) target.destroyBuffers();
        target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        target.setClearColor(0, 0, 0, 0);
        if (deferred) {
            worldMask = target;
            worldMaskDirty = false;
        } else mask = target;
        return target;
    }

    private static void failed(RuntimeException exception) {
        unavailable = true;
        releaseTargets();
        LogUtils.getLogger().error("Failed to render the Poem outline; retrying on resource reload", exception);
    }

    private static void draw(ResourceLocation texture, MeshData vertices) {
        if (unavailable || maskShader == null || compositeShader == null || vertices == null
                || vertices.drawState().vertexCount() == 0) {
            if (vertices != null) vertices.close();
            return;
        }
        boolean uploaded = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            PoemOutlineBounds bounds = PoemOutlineBounds.project(vertices.vertexBuffer().order(ByteOrder.nativeOrder()),
                    vertices.drawState().vertexCount(), vertices.drawState().format().getVertexSize(),
                    new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()),
                    viewport[0], viewport[1], viewport[2], viewport[3], 2);
            if (bounds == null) return;
            State state = new State(stack, viewport);
            try {
                int width = viewport[0] + viewport[2], height = viewport[1] + viewport[3];
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                boolean deferred = replayingWorld;
                RenderTarget target = ensureTarget(width, height, deferred);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(true);
                if (deferred) {
                    // Keep every weapon/hand silhouette until final composite, clearing once per frame.
                    if (!worldMaskDirty) {
                        RenderSystem.disableScissor();
                        target.clear(Minecraft.ON_OSX);
                    }
                } else {
                    RenderSystem.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
                    target.clear(Minecraft.ON_OSX);
                }
                target.bindWrite(false);
                RenderSystem.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
                RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.disableCull();
                RenderSystem.disableBlend();
                RenderSystem.setShaderTexture(0, texture);
                RenderSystem.setShader(() -> maskShader);
                // drawWithShader owns the mesh and closes it; scene depth stays untouched so
                // overlapping weapon cubes never cut holes into each other.
                uploaded = true;
                BufferUploader.drawWithShader(vertices);

                if (deferred) {
                    worldMaskDirty = true;
                    return;
                }

                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer);
                RenderSystem.depthMask(false);
                if (state.stencil) GL11.glEnable(GL11.GL_STENCIL_TEST);
                int left = bounds.x(), bottom = bounds.y();
                int right = left + bounds.width(), top = bottom + bounds.height();
                if (state.scissor) {
                    left = Math.max(left, state.scissorBox[0]);
                    bottom = Math.max(bottom, state.scissorBox[1]);
                    right = Math.min(right, state.scissorBox[0] + state.scissorBox[2]);
                    top = Math.min(top, state.scissorBox[1] + state.scissorBox[3]);
                }
                RenderSystem.enableScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
                compositeShader.safeGetUniform("MaskBounds").set((float) bounds.x(), (float) bounds.y(),
                        (float) (bounds.x() + bounds.width()), (float) (bounds.y() + bounds.height()));
                composite(target, state.viewport, width, height);
            } catch (RuntimeException exception) {
                failed(exception);
            } finally {
                state.restore();
            }
        } finally {
            if (!uploaded && vertices != null) vertices.close();
        }
    }

    private static void composite(RenderTarget target, int[] viewport, int width, int height) {
        RenderSystem.setShader(() -> compositeShader);
        compositeShader.setSampler("Sampler0", target.getColorTextureId());
        compositeShader.setSampler("Sampler1", target.getDepthTextureId());
        try {
            compositeShader.apply();
            // 1.21.1 core shaders no longer carry blend state; blending belongs to this draw only.
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            float u0 = (float) viewport[0] / width, v0 = (float) viewport[1] / height;
            float u1 = (float) (viewport[0] + viewport[2]) / width;
            float v1 = (float) (viewport[1] + viewport[3]) / height;
            BufferBuilder quad = new BufferBuilder(QUAD_BUFFER, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            quad.addVertex(-1, -1, 0).setUv(u0, v0);
            quad.addVertex(1, -1, 0).setUv(u1, v0);
            quad.addVertex(1, 1, 0).setUv(u1, v1);
            quad.addVertex(-1, 1, 0).setUv(u0, v1);
            BufferUploader.draw(quad.buildOrThrow());
        } finally {
            compositeShader.clear();
        }
    }

    /** Restore the caller's state even when used inside a GUI preview or a hand render. */
    static final class State {
        private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int[] viewport;
        private final int[] scissorBox = new int[4];
        private final float[] clearColor = new float[4];
        private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        private final double[] depthRange = new double[2];
        private final float[] shaderColor = RenderSystem.getShaderColor().clone();
        private final boolean[] colorMask = new boolean[4];
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int destRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int destAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int rgbEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        private final int alphaEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        private final int activeTexture = GlStateManager._getActiveTexture();
        private final int shaderTexture = RenderSystem.getShaderTexture(0);
        private final ShaderInstance shader = RenderSystem.getShader();
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int[] boundTextures = new int[2];

        State(MemoryStack stack, int[] viewport) {
            this.viewport = viewport;
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            GL11.glGetDoublev(GL11.GL_DEPTH_RANGE, depthRange);
            ByteBuffer channels = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, channels);
            for (int i = 0; i < 4; i++) colorMask[i] = channels.get(i) != 0;
            for (int i = 0; i < boundTextures.length; i++) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
                boundTextures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            }
            RenderSystem.activeTexture(activeTexture);
        }

        void restore() {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            RenderSystem.clearDepth(clearDepth);
            GL11.glDepthRange(depthRange[0], depthRange[1]);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunc);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            GL20.glBlendEquationSeparate(rgbEquation, alphaEquation);
            RenderSystem.blendFuncSeparate(sourceRgb, destRgb, sourceAlpha, destAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            else RenderSystem.disableScissor();
            if (stencil) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.setShaderTexture(0, shaderTexture);
            for (int i = 0; i < boundTextures.length; i++) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
                RenderSystem.bindTexture(boundTextures[i]);
            }
            RenderSystem.activeTexture(activeTexture);
            RenderSystem.setShader(() -> shader);
            GlStateManager._glUseProgram(program);
        }
    }

    private static final class Pending {
        final MeshData mesh;
        final ResourceLocation texture;
        final Matrix4f projection, modelView;
        final VertexSorting sorting;
        final int[] viewport;
        boolean consumed;
        Pending(MeshData mesh, ResourceLocation texture, Matrix4f projection, Matrix4f modelView,
                VertexSorting sorting, int[] viewport) {
            this.mesh = mesh; this.texture = texture; this.projection = projection;
            this.modelView = modelView; this.sorting = sorting; this.viewport = viewport;
        }
    }

    @EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() { }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            Runnable clear = () -> {
                releaseTargets(); WORLD_BUFFERS.forEach(ByteBufferBuilder::close); WORLD_BUFFERS.clear();
            };
            if (RenderSystem.isOnRenderThread()) clear.run();
            else RenderSystem.recordRenderCall(clear::run);
        }
    }

    @EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        private Resources() { }

        @SubscribeEvent
        public static void registerShaders(RegisterShadersEvent event) throws IOException {
            releaseTargets();
            unavailable = false;
            maskShader = compositeShader = null;
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), id("poem_outline_mask"),
                        DefaultVertexFormat.NEW_ENTITY), shader -> maskShader = shader);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), id("poem_outline_composite"),
                        DefaultVertexFormat.POSITION_TEX), shader -> compositeShader = shader);
            } catch (IOException exception) {
                unavailable = true;
                LogUtils.getLogger().error("Failed to load Poem outline shaders; retaining the base and emissive materials", exception);
            }
        }
    }
}
