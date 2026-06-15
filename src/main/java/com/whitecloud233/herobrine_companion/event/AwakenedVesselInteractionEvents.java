package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.awakened.containment.AwakenedMobCaptureService;
import com.whitecloud233.herobrine_companion.entity.awakened.containment.AwakenedVesselActionGuard;
import com.whitecloud233.herobrine_companion.entity.awakened.containment.AwakenedVesselFeedback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class AwakenedVesselInteractionEvents {
    private AwakenedVesselInteractionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handleInteraction(event, event.getTarget());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handleInteraction(event, event.getTarget());
    }

    private static void handleInteraction(PlayerInteractEvent event, Entity rawTarget) {
        if (event.getEntity().level().isClientSide
                || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!stack.is(HerobrineCompanion.AWAKENED_VESSEL.get())) {
            return;
        }
        if (AwakenedVesselActionGuard.blocksDuplicateEntityInteraction(player, player.serverLevel())) {
            cancel(event);
            return;
        }
        if (AwakenedVesselActionGuard.blocksImmediateFollowUp(player, stack, player.serverLevel())) {
            cancel(event);
            return;
        }

        Entity target = resolveTarget(rawTarget);
        if (!(target instanceof Mob mob)) {
            AwakenedVesselFeedback.captureFailure(player, AwakenedMobCaptureService.Failure.UNSUPPORTED);
            cancel(event);
            return;
        }

        AwakenedMobCaptureService.CaptureResult result = AwakenedMobCaptureService.capture(player, mob, stack);
        if (result.success()) {
            AwakenedVesselActionGuard.lockAfterCapture(player, stack);
            AwakenedVesselFeedback.captureSuccess(player, mob, result.capturedName());
        } else {
            AwakenedVesselFeedback.captureFailure(player, result.failure());
        }
        cancel(event);
    }

    private static Entity resolveTarget(Entity target) {
        if (target instanceof EnderDragonPart part) {
            return part.parentMob;
        }
        return target;
    }

    private static void cancel(PlayerInteractEvent event) {
        if (event instanceof PlayerInteractEvent.EntityInteract entityInteract) {
            entityInteract.setCanceled(true);
            entityInteract.setCancellationResult(InteractionResult.SUCCESS);
        } else if (event instanceof PlayerInteractEvent.EntityInteractSpecific specificInteract) {
            specificInteract.setCanceled(true);
            specificInteract.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
