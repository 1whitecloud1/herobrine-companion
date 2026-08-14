package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.List;

@EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public final class ChallengeBlackWhiteFlashHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation POST_CHAIN_LOCATION =
            ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "shaders/post/challenge_bw_flash.json");
    private static final int FLASH_TICKS = 20;
    private static final String FLASH_PASS_NAME = HerobrineCompanion.MODID + ":challenge_bw_flash";

    private static PostChain postChain;
    private static EffectInstance flashEffect;
    private static int postChainWidth = -1;
    private static int postChainHeight = -1;
    private static int flashTicks;
    private static boolean postChainUnavailable;

    private ChallengeBlackWhiteFlashHandler() {
    }

    public static void trigger() {
        flashTicks = FLASH_TICKS;
        postChainUnavailable = false;
        ensurePostChain();
        updateProgress(0.0F);
    }

    private static boolean ensurePostChain() {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (minecraft.level == null || mainTarget == null || postChainUnavailable) {
            return false;
        }

        if (postChain == null) {
            try {
                postChain = new PostChain(
                        minecraft.getTextureManager(),
                        minecraft.getResourceManager(),
                        mainTarget,
                        POST_CHAIN_LOCATION
                );
                flashEffect = findFlashEffect(postChain);
                if (flashEffect == null) {
                    throw new IllegalStateException("Missing post-process pass " + FLASH_PASS_NAME);
                }
            } catch (Exception exception) {
                releasePostChain();
                postChainUnavailable = true;
                LOGGER.error("Failed to load challenge black-white flash post chain {}", POST_CHAIN_LOCATION, exception);
                return false;
            }
        }

        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) {
            return false;
        }
        if (postChainWidth != width || postChainHeight != height) {
            postChain.resize(width, height);
            postChainWidth = width;
            postChainHeight = height;
        }
        return true;
    }

    private static EffectInstance findFlashEffect(PostChain chain) throws IllegalAccessException {
        for (Field field : PostChain.class.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(chain);
            if (!(value instanceof List<?> values)) {
                continue;
            }
            for (Object element : values) {
                if (element instanceof PostPass pass && FLASH_PASS_NAME.equals(pass.getName())) {
                    return pass.getEffect();
                }
            }
        }
        return null;
    }

    private static void updateProgress(float progress) {
        if (flashEffect == null) {
            return;
        }
        Uniform uniform = flashEffect.getUniform("FlashProgress");
        if (uniform != null) {
            uniform.set(Mth.clamp(progress, 0.0F, 1.0F));
        }
    }

    private static void releasePostChain() {
        if (postChain != null) {
            postChain.close();
        }
        postChain = null;
        flashEffect = null;
        postChainWidth = -1;
        postChainHeight = -1;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            flashTicks = 0;
            postChainUnavailable = false;
            releasePostChain();
            return;
        }
        if (flashTicks > 0) {
            flashTicks--;
        }
    }

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (flashTicks <= 0 || !ensurePostChain()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float remaining = Mth.clamp((flashTicks - partialTick) / FLASH_TICKS, 0.0F, 1.0F);
        updateProgress(1.0F - remaining);

        try {
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            postChain.process(partialTick);
        } catch (RuntimeException exception) {
            releasePostChain();
            postChainUnavailable = true;
            LOGGER.error("Failed to render challenge black-white flash post chain", exception);
        } finally {
            mainTarget.bindWrite(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (flashTicks <= 0 || !postChainUnavailable) {
            return;
        }

        float remaining = Mth.clamp((flashTicks - event.getPartialTick().getGameTimeDeltaPartialTick(false)) / FLASH_TICKS, 0.0F, 1.0F);
        float progress = 1.0F - remaining;
        boolean whitePhase = progress < 0.22F;
        int alpha = whitePhase ? 245 : Mth.clamp((int) ((1.0F - progress) * 235.0F), 0, 235);
        int color = (alpha << 24) | (whitePhase ? 0xFFFFFF : 0x000000);
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), color);
    }
}
