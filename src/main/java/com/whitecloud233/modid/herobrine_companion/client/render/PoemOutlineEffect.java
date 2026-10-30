package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.mixin.client.BlendModeAccessor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** A weapon-only silhouette with a one-pixel, depth-tested exterior edge. */
@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PoemOutlineEffect {
    private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
    private static final List<Draw> DRAWS = new ArrayList<>();
    private static final List<BufferBuilder> BUILDERS = new ArrayList<>();
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
    // Separate from the caller's builder: a BufferSource may flush us while it is switching materials.
    private static final BufferBuilder QUAD = new BufferBuilder(256);
    private static ShaderInstance maskShader, compositeShader;
    private static RenderTarget mask;
    private static RenderTarget worldMask;
    private static boolean collectingWorld, worldMaskDirty;
    private static boolean unavailable;

    private PoemOutlineEffect() { }

    public static boolean enabled() {
        return com.whitecloud233.modid.herobrine_companion.config.Config.poemOutlineEnabled
                && !StandalonePoemRenderer.auxiliaryFirstPersonBody();
    }

    public static void beginWorldFrame() {
        discardWorldFrame();
        if (!enabled()) { releaseTargets(); return; }
        collectingWorld = PoemShaderCompat.shadersEnabled();
    }

    public static void discardWorldFrame() {
        collectingWorld = false;
        worldMaskDirty = false;
        for (Draw draw : DRAWS) {
            if (draw.builder.building()) draw.builder.end().release();
            draw.builder.discard();
        }
        DRAWS.clear();
    }

    public static VertexConsumer buffer(MultiBufferSource fallback, ResourceLocation texture) {
        if (!enabled() || unavailable || maskShader == null || compositeShader == null || PoemShaderCompat.shadowPass()) return DISCARD;
        if (!collectingWorld) return fallback.getBuffer(renderType(texture));
        // Never switch framebuffer or core shader inside Oculus' shared entity batch.
        // Its shader state also belongs to the players rendered after this weapon.
        int index = DRAWS.size();
        BufferBuilder builder;
        if (index < BUILDERS.size()) builder = BUILDERS.get(index);
        else {
            builder = new BufferBuilder(4096);
            if (index < 64) BUILDERS.add(builder);
        }
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        DRAWS.add(new Draw(builder, texture, new Matrix4f(RenderSystem.getProjectionMatrix()),
                new Matrix4f(RenderSystem.getModelViewMatrix()), RenderSystem.getVertexSorting(), viewport()));
        return builder;
    }

    private static void drawWorldMasks() {
        if (DRAWS.isEmpty()) return;
        List<Draw> pending = new ArrayList<>(DRAWS);
        DRAWS.clear();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        int[] viewport = viewport();
        modelView.pushPose();
        collectingWorld = true;
        try {
            var main = Minecraft.getInstance().getMainRenderTarget();
            for (Draw draw : pending) {
                if (!enabled() || unavailable) break;
                if (draw.viewport[0] + draw.viewport[2] != main.width
                        || draw.viewport[1] + draw.viewport[3] != main.height) continue;
                RenderSystem.setProjectionMatrix(draw.projection, draw.sorting);
                modelView.last().pose().set(draw.modelView);
                RenderSystem.applyModelViewMatrix();
                RenderSystem.viewport(draw.viewport[0], draw.viewport[1], draw.viewport[2], draw.viewport[3]);
                draw(draw.texture, draw.builder.end());
            }
        } finally {
            collectingWorld = false;
            for (Draw draw : pending) {
                if (draw.builder.building()) draw.builder.end().release();
                draw.builder.discard();
            }
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    private record Draw(BufferBuilder builder, ResourceLocation texture, Matrix4f projection,
                        Matrix4f modelView, VertexSorting sorting, int[] viewport) { }

    /** Runs after Oculus' final pass, before GameRenderer clears the shared scene depth. */
    public static void finishWorldFrame() {
        collectingWorld = false;
        drawWorldMasks();
        if (!enabled() || !worldMaskDirty || worldMask == null || unavailable) return;
        worldMaskDirty = false;
        State state;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            state = new State(stack, viewport());
        }
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            // A resize/reload in the middle of a frame must not stretch stale silhouettes.
            if (main.width != worldMask.width || main.height != worldMask.height) return;
            main.bindWrite(true);
            RenderSystem.disableScissor();
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            compositeShader.safeGetUniform("MaskBounds").set(0f, 0f, (float) worldMask.width, (float) worldMask.height);
            // Oculus shares main's depth texture. The outline shader writes the weapon
            // edge's original depth, so foreground blocks/entities still occlude it.
            composite(worldMask, new int[] {0, 0, main.width, main.height});
        } catch (RuntimeException exception) {
            failed(exception);
        } finally {
            state.restore();
        }
    }

    public static RenderType renderType(ResourceLocation texture) {
        return TYPES.computeIfAbsent(texture, OutlineType::new);
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
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

    private static ResourceLocation id(String name) {
        return new ResourceLocation(HerobrineCompanion.MODID, name);
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

    private static void draw(ResourceLocation texture, BufferBuilder.RenderedBuffer vertices) {
        if (!enabled() || unavailable || maskShader == null || compositeShader == null || vertices.isEmpty()) {
            vertices.release();
            return;
        }
        boolean uploaded = false;
        try {
            int[] viewport = viewport();
            PoemOutlineBounds bounds = PoemOutlineBounds.project(vertices.vertexBuffer().order(ByteOrder.nativeOrder()),
                    vertices.drawState().vertexCount(), vertices.drawState().format().getVertexSize(),
                    new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()),
                    viewport[0], viewport[1], viewport[2], viewport[3], 2);
            if (bounds == null) return;
            try (MaskPass pass = new MaskPass(texture, viewport, bounds)) {
                if (!pass.ready) return;
                uploaded = true;
                try {
                    BufferUploader.drawWithShader(vertices);
                } catch (RuntimeException exception) {
                    pass.ready = false;
                    failed(exception);
                }
            }
        } finally {
            if (!uploaded) vertices.release();
        }
    }

    private static int[] viewport() {
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        return viewport;
    }

    private static void failed(RuntimeException exception) {
        unavailable = true;
        releaseTargets();
        LogUtils.getLogger().error("Failed to render the Poem outline; retrying on resource reload", exception);
    }

    /** Shared by vanilla's end() and batchers which only call setup/clearRenderState(). */
    private static final class MaskPass implements AutoCloseable {
        private final State state;
        private final PoemOutlineBounds bounds;
        private final boolean deferred;
        private RenderTarget target;
        private boolean ready;

        private MaskPass(ResourceLocation texture, int[] viewport, PoemOutlineBounds bounds) {
            this.bounds = bounds;
            deferred = collectingWorld;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                state = new State(stack, viewport);
            }
            try {
                if (!enabled() || PoemShaderCompat.shadowPass() || unavailable || maskShader == null || compositeShader == null
                        || bounds.width() <= 0 || bounds.height() <= 0) {
                    suppressDraw();
                    return;
                }
                int width = viewport[0] + viewport[2], height = viewport[1] + viewport[3];
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                target = ensureTarget(width, height, deferred);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(true);
                // All weapons/material batches share one frame mask. Clear once, not
                // once per item; otherwise only the last rendered weapon gets an edge.
                if (!deferred || !worldMaskDirty) {
                    if (deferred) RenderSystem.disableScissor();
                    else RenderSystem.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
                    RenderSystem.clearDepth(1.0);
                    target.clear(Minecraft.ON_OSX);
                }
                RenderSystem.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
                target.bindWrite(false);
                RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.disableCull();
                RenderSystem.disableBlend();
                RenderSystem.setShaderTexture(0, texture);
                RenderSystem.setShader(() -> maskShader);
                // Scene depth must not cut holes between the weapon's overlapping cubes.
                ready = true;
            } catch (RuntimeException exception) {
                failed(exception);
                state.restore();
                suppressDraw();
            }
        }

        private void suppressDraw() {
            // A batcher will still submit its vertices. Never let a missing shader paint the weapon black.
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(false);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            if (maskShader != null) RenderSystem.setShader(() -> maskShader);
        }

        @Override
        public void close() {
            try {
                if (!ready) return;
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
                composite(target, state.viewport);
            } catch (RuntimeException exception) {
                failed(exception);
            } finally {
                state.restore();
            }
        }
    }

    private static void composite(RenderTarget target, int[] viewport) {
        int width = target.width, height = target.height;
        RenderSystem.setShader(() -> compositeShader);
        compositeShader.setSampler("Sampler0", target.getColorTextureId());
        compositeShader.setSampler("Sampler1", target.getDepthTextureId());
        try {
            compositeShader.apply();
            // Core shaders keep the opaque BlendMode cache; blending belongs to this draw, not the shader JSON.
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            float u0 = (float) viewport[0] / width, v0 = (float) viewport[1] / height;
            float u1 = (float) (viewport[0] + viewport[2]) / width;
            float v1 = (float) (viewport[1] + viewport[3]) / height;
            QUAD.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            QUAD.vertex(-1, -1, 0).uv(u0, v0).endVertex();
            QUAD.vertex(1, -1, 0).uv(u1, v0).endVertex();
            QUAD.vertex(1, 1, 0).uv(u1, v1).endVertex();
            QUAD.vertex(-1, 1, 0).uv(u0, v1).endVertex();
            BufferUploader.draw(QUAD.end());
        } finally {
            compositeShader.clear();
        }
    }

    private static final class OutlineType extends RenderType {
        private final ResourceLocation texture;
        private MaskPass batchedPass;

        private OutlineType(ResourceLocation texture) {
            super("herobrine_companion:poem_outline/" + texture, DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS, 4096, false, false, () -> { }, () -> { });
            this.texture = texture;
        }

        @Override
        public void end(BufferBuilder builder, VertexSorting sorting) {
            if (builder.building()) draw(texture, builder.end());
        }

        @Override
        public void setupRenderState() {
            // Oculus/Iris draws buffer segments directly without calling RenderType.end().
            // Keep the mask bound for every segment and composite once when the batch ends.
            int[] viewport = viewport();
            batchedPass = new MaskPass(texture, viewport,
                    new PoemOutlineBounds(viewport[0], viewport[1], viewport[2], viewport[3]));
        }

        @Override
        public void clearRenderState() {
            if (batchedPass == null) return;
            try {
                batchedPass.close();
            } finally {
                batchedPass = null;
            }
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
        private final boolean[] colorMask = new boolean[4];
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final BlendMode cachedBlend = BlendModeAccessor.poem$getLastApplied();
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
            // Restore the cache as well as OpenGL: the next vanilla shader may otherwise replace the sun's blend.
            BlendModeAccessor.poem$setLastApplied(cachedBlend);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            RenderSystem.clearDepth(clearDepth);
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

    @Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() { }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            Runnable clear = () -> { releaseTargets(); BUILDERS.clear(); };
            if (RenderSystem.isOnRenderThread()) clear.run();
            else RenderSystem.recordRenderCall(clear::run);
        }
    }
}
