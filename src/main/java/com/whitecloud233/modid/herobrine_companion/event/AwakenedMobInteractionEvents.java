package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.awakened.AwakenedMobBrain;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AwakenedMobInteractionEvents {
    private AwakenedMobInteractionEvents() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().level().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Entity target = resolveTarget(event.getTarget());
        handleInteraction(event, AwakenedMobBrain.handlePlayerInteraction(event.getEntity(), target, event.getItemStack()));
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity().level().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Entity target = resolveTarget(event.getTarget());
        handleInteraction(event, AwakenedMobBrain.handlePlayerInteraction(event.getEntity(), target, event.getItemStack()));
    }

    private static Entity resolveTarget(Entity target) {
        if (target instanceof EnderDragonPart part) {
            return part.parentMob;
        }
        return target;
    }

    private static void handleInteraction(PlayerInteractEvent event, boolean handled) {
        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
