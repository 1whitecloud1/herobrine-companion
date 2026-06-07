package com.whitecloud233.modid.herobrine_companion.client.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.render.AwakenedMobNameplateRenderer;
import com.whitecloud233.modid.herobrine_companion.client.render.EntitySpeechBubbleRenderer;
import com.whitecloud233.modid.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import com.whitecloud233.modid.herobrine_companion.entity.dialogue.SpeechBubbleAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpeechBubbleClientEvents {
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
}
