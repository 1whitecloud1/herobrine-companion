package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobBrain;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class AwakenedMobInteractionEvents {
    private AwakenedMobInteractionEvents() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().level().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (AwakenedMobBrain.handlePlayerInteraction(event.getEntity(), event.getTarget(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity().level().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (AwakenedMobBrain.handlePlayerInteraction(event.getEntity(), event.getTarget(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
