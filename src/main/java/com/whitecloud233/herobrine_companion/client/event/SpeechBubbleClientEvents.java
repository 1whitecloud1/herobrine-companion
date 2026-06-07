package com.whitecloud233.herobrine_companion.client.event;

import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.client.render.AwakenedMobNameplateRenderer;
import com.whitecloud233.herobrine_companion.client.render.EntitySpeechBubbleRenderer;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobProfiles;
import com.whitecloud233.herobrine_companion.entity.dialogue.SpeechBubbleAccessor;
import net.minecraft.world.entity.LivingEntity;
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
        boolean hasSpeechBubble = entity instanceof SpeechBubbleAccessor speechAccessor
                && speechAccessor.herobrineCompanion$hasSpeechBubble();

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

    private static boolean shouldShowCompanionNameplate(LivingEntity entity) {
        if (entity instanceof AwakenedMobAccessor awakenedAccessor && awakenedAccessor.herobrineCompanion$isAwakenedMob()) {
            return true;
        }
        return entity.hasCustomName() && AwakenedMobProfiles.supports(entity);
    }
}
