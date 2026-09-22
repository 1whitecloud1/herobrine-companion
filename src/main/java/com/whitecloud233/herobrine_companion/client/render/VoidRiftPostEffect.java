package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.projectile.VoidRiftEntity;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;

/** World-color lensing before the hand/HUD pass; no gameplay or optional animation dependencies. */
@EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public final class VoidRiftPostEffect {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PASS = HerobrineCompanion.MODID + ":void_rift_lens";
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(
            HerobrineCompanion.MODID, "shaders/post/void_rift_lens.json");
    private static final Map<Integer, VoidRiftProjection.Projected> RIFTS = new HashMap<>();
    private static ClientLevel level;
    private static LensChain chain;
    private static RenderTarget chainTarget;
    private static Matrix4f sceneProjection;
    private static int width = -1, height = -1;
    private static boolean collecting, unavailable;

    private VoidRiftPostEffect() { }

    /** False asks the entity renderer to draw the untextured fallback after a shader load failure. */
    public static boolean submit(VoidRiftEntity entity, Matrix4fc pose, float partialTick) {
        if (!collecting || entity.level() != level) return true;
        if (unavailable) return false;
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f viewPose = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose);
        var rift = VoidRiftProjection.project(projection, viewPose, target.width, target.height,
                entity.tickCount + partialTick, entity.getVisualLifetime(), entity.getRotation() * 0.017453292F);
        if (rift == null) return true;
        sceneProjection = projection;
        if (!RIFTS.containsKey(entity.getId()) && RIFTS.size() >= VoidRiftProjection.MAX_RIFTS) {
            var farthest = RIFTS.entrySet().stream().max(Comparator.comparingDouble(e -> e.getValue().depth())).orElseThrow();
            if (farthest.getValue().depth() <= rift.depth()) return true;
            RIFTS.remove(farthest.getKey());
        }
        RIFTS.put(entity.getId(), rift);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onWorldRender(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            syncLevel();
            RIFTS.clear();
            sceneProjection = null;
            collecting = true;
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            collecting = false;
            try {
                if (!RIFTS.isEmpty() && sceneProjection != null) render();
            } finally {
                RIFTS.clear();
                sceneProjection = null;
            }
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) { syncLevel(); }

    private static void syncLevel() {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != level) {
            reset();
            level = current;
        }
    }

    private static void reset() {
        release();
        RIFTS.clear();
        sceneProjection = null;
        collecting = false;
        unavailable = false;
    }

    private static void release() {
        if (chain != null) chain.close();
        chain = null;
        chainTarget = null;
        width = height = -1;
    }

    private static void ensureChain(Minecraft mc, RenderTarget target) throws IOException {
        if (chainTarget != target) release();
        if (chain == null) {
            chain = new LensChain(mc, target);
            chainTarget = target;
            if (chain.lens == null) throw new IOException("Missing rift lens pass");
        }
        if (width != target.width || height != target.height) {
            chain.resize(target.width, target.height);
            width = target.width;
            height = target.height;
        }
    }

    private static void render() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = mc.getMainRenderTarget();
        if (unavailable || target.width <= 0 || target.height <= 0) return;
        State state = new State();
        RenderTarget savedDepth = null;
        boolean failed = false;
        try {
            ensureChain(mc, target);
            RenderTarget depth = chain.getTempTarget("rift_depth");
            depth.copyDepthFrom(target);
            savedDepth = depth;
            EffectInstance shader = chain.lens;
            shader.safeGetUniform("InverseProjection").set(new Matrix4f(sceneProjection).invert());
            // Far-to-near makes an overlapping foreground rift cover the one behind it.
            var visible = RIFTS.values().stream().sorted(Comparator.comparingDouble(
                    VoidRiftProjection.Projected::depth).reversed()).toList();
            shader.safeGetUniform("RiftCount").set((float) visible.size());
            for (int i = 0; i < visible.size(); i++) {
                var r = visible.get(i);
                shader.safeGetUniform("Rift" + i).set(r.u(), r.v(), r.radius(), r.angle());
                shader.safeGetUniform("RiftState" + i).set(r.depth(), r.strength(), r.seed(), r.seconds());
            }
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            chain.process(0);
        } catch (IOException | RuntimeException exception) {
            failed = true;
            unavailable = true;
            LOGGER.error("Failed to render the void rift lens; using geometry until resource reload", exception);
        } finally {
            // PostPass clears its output, including the main framebuffer. Keep the world's depth intact.
            if (savedDepth != null) target.copyDepthFrom(savedDepth);
            if (failed) release();
            target.bindWrite(true);
            state.restore();
        }
    }

    private static final class LensChain extends PostChain {
        // addPass is invoked by the super-constructor: do not overwrite this field with an initializer.
        private EffectInstance lens;

        private LensChain(Minecraft mc, RenderTarget target) throws IOException {
            super(mc.getTextureManager(), mc.getResourceManager(), target, CHAIN);
        }

        @Override
        public PostPass addPass(String name, RenderTarget input, RenderTarget output, boolean linear) throws IOException {
            PostPass pass = super.addPass(name, input, output, linear);
            if (PASS.equals(name)) lens = pass.getEffect();
            return pass;
        }
    }

    private static final class State {
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int destRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int destAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int blendEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        private final int alphaEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);

        private void restore() {
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunc);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            GL20.glBlendEquationSeparate(blendEquation, alphaEquation);
            RenderSystem.blendFuncSeparate(sourceRgb, destRgb, sourceAlpha, destAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }

    @EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        private Resources() { }

        @SubscribeEvent
        public static void onReloadRegistration(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> reset());
        }
    }
}
