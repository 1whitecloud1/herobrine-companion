package com.whitecloud233.herobrine_companion.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.client.render.AwakenedMobNameplateRenderer;
import com.whitecloud233.herobrine_companion.client.render.EntitySpeechBubbleRenderer;
import com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobProfiles;
import com.whitecloud233.herobrine_companion.entity.dialogue.SpeechBubbleAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.slf4j.Logger;

public final class SpeechBubbleClientEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean loggedFirstOverlay;

    private SpeechBubbleClientEvents() {
    }

    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (entity instanceof HeroEntity) {
            return;
        }

        boolean showCompanionNameplate = shouldShowCompanionNameplate(entity);
        boolean hasSpeechBubble = hasSpeechBubble(entity);

        if (!showCompanionNameplate && !hasSpeechBubble) {
            return;
        }

        if (!loggedFirstOverlay) {
            loggedFirstOverlay = true;
            LOGGER.info("Rendering Herobrine Companion overlay for {} companionNameplate={} speechBubble={}",
                    entity.getType(), showCompanionNameplate, hasSpeechBubble);
        }

        if (showCompanionNameplate) {
            AwakenedMobNameplateRenderer.renderForced(event.getPoseStack(), event.getMultiBufferSource(), entity, event.getPackedLight(), event.getPartialTick(), 0xFFFF0000);
        }
        if (hasSpeechBubble) {
            EntitySpeechBubbleRenderer.render(event.getPoseStack(), event.getMultiBufferSource(), entity, event.getPackedLight(), event.getPartialTick());
        }

        event.setCanRender(TriState.FALSE);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !HeroEpicFightCompat.isLoaded()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean renderedAny = false;

        for (Entity renderedEntity : minecraft.level.entitiesForRendering()) {
            if (!(renderedEntity instanceof LivingEntity entity) || !isEpicFightBossOverlayTarget(entity)) {
                continue;
            }

            if (renderEpicFightBossOverlay(event.getPoseStack(), bufferSource, entity, cameraPosition, partialTick)) {
                renderedAny = true;
            }
        }

        if (renderedAny) {
            bufferSource.endBatch();
        }
    }

    private static boolean renderEpicFightBossOverlay(PoseStack poseStack, MultiBufferSource bufferSource,
                                                      LivingEntity entity, Vec3 cameraPosition, float partialTick) {
        boolean showCompanionNameplate = shouldShowCompanionNameplate(entity);
        boolean hasSpeechBubble = hasSpeechBubble(entity);

        if (!showCompanionNameplate && !hasSpeechBubble) {
            return false;
        }

        double x = Mth.lerp((double) partialTick, entity.xo, entity.getX()) - cameraPosition.x;
        double y = Mth.lerp((double) partialTick, entity.yo, entity.getY()) - cameraPosition.y;
        double z = Mth.lerp((double) partialTick, entity.zo, entity.getZ()) - cameraPosition.z;

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        // Epic Fight's Wither and Ender Dragon patched renderers bypass RenderNameTagEvent.
        if (showCompanionNameplate) {
            AwakenedMobNameplateRenderer.renderForced(poseStack, bufferSource, entity, LightTexture.FULL_BRIGHT, partialTick, 0xFFFF0000);
        }
        if (hasSpeechBubble) {
            EntitySpeechBubbleRenderer.render(poseStack, bufferSource, entity, LightTexture.FULL_BRIGHT, partialTick);
        }
        poseStack.popPose();

        return true;
    }

    private static boolean isEpicFightBossOverlayTarget(LivingEntity entity) {
        EntityType<?> entityType = entity.getType();
        return (entityType == EntityType.WITHER || entityType == EntityType.ENDER_DRAGON)
                && entity instanceof AwakenedMobAccessor awakenedAccessor
                && awakenedAccessor.herobrineCompanion$isAwakenedMob();
    }

    private static boolean hasSpeechBubble(LivingEntity entity) {
        return entity instanceof SpeechBubbleAccessor speechAccessor
                && speechAccessor.herobrineCompanion$hasSpeechBubble();
    }

    private static boolean shouldShowCompanionNameplate(LivingEntity entity) {
        if (entity instanceof AwakenedMobAccessor awakenedAccessor && awakenedAccessor.herobrineCompanion$isAwakenedMob()) {
            return true;
        }
        return entity.hasCustomName() && AwakenedMobProfiles.supports(entity);
    }
}
