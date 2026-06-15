package com.whitecloud233.modid.herobrine_companion.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.render.AwakenedMobNameplateRenderer;
import com.whitecloud233.modid.herobrine_companion.client.render.EntitySpeechBubbleRenderer;
import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import com.whitecloud233.modid.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import com.whitecloud233.modid.herobrine_companion.entity.dialogue.SpeechBubbleAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpeechBubbleClientEvents {
    private static final double EPIC_FIGHT_BOSS_LABEL_DISTANCE_SQR = 4096.0D;

    private SpeechBubbleClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }

        boolean awakened = entity instanceof AwakenedMobAccessor awakenedAccessor
                && awakenedAccessor.herobrineCompanion$isAwakenedMob();
        boolean hasSpeechBubble = entity instanceof SpeechBubbleAccessor speechAccessor
                && speechAccessor.herobrineCompanion$hasSpeechBubble();

        if (!awakened && !hasSpeechBubble) {
            return;
        }

        if (awakened) {
            AwakenedMobNameplateRenderer.render(event.getPoseStack(), event.getMultiBufferSource(), entity, event.getPackedLight());
        }
        if (hasSpeechBubble) {
            EntitySpeechBubbleRenderer.render(event.getPoseStack(), event.getMultiBufferSource(), entity, event.getPackedLight());
        }

        event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Vec3 cameraPos = event.getCamera().getPosition();
        boolean renderedAny = false;
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity livingEntity) || !needsBossFallback(livingEntity, minecraft)) {
                continue;
            }
            renderFallbackAtEntity(event.getPoseStack(), bufferSource, livingEntity, cameraPos, event.getPartialTick());
            renderedAny = true;
        }

        if (renderedAny) {
            bufferSource.endBatch();
        }
    }

    private static boolean needsBossFallback(LivingEntity entity, Minecraft minecraft) {
        boolean isDragon = entity.getType() == EntityType.ENDER_DRAGON;
        boolean isEpicFightBoss = HeroEpicFightCompat.isLoaded()
                && (isDragon || entity.getType() == EntityType.WITHER);
        if (!isDragon && !isEpicFightBoss) {
            return false;
        }

        boolean awakened = entity instanceof AwakenedMobAccessor awakenedAccessor
                && awakenedAccessor.herobrineCompanion$isAwakenedMob();
        boolean hasSpeechBubble = entity instanceof SpeechBubbleAccessor speechAccessor
                && speechAccessor.herobrineCompanion$hasSpeechBubble();
        return hasSpeechBubble || awakened
                && entity.hasCustomName()
                && minecraft.player != null
                && minecraft.player.distanceToSqr(entity) <= EPIC_FIGHT_BOSS_LABEL_DISTANCE_SQR;
    }

    private static void renderFallbackAtEntity(PoseStack poseStack, MultiBufferSource bufferSource,
                                               LivingEntity entity, Vec3 cameraPos, float partialTick) {
        double x = entity.xOld + (entity.getX() - entity.xOld) * partialTick;
        double y = entity.yOld + (entity.getY() - entity.yOld) * partialTick;
        double z = entity.zOld + (entity.getZ() - entity.zOld) * partialTick;

        poseStack.pushPose();
        poseStack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
        AwakenedMobNameplateRenderer.render(poseStack, bufferSource, entity, 0xF000F0);
        EntitySpeechBubbleRenderer.render(poseStack, bufferSource, entity, 0xF000F0);
        poseStack.popPose();
    }
}
