package com.whitecloud233.herobrine_companion.client.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.animation.StandalonePoemAnimation;
import com.whitecloud233.herobrine_companion.client.render.StandalonePoemRenderer;
import com.whitecloud233.herobrine_companion.combat.poem.PoemMotionLibrary;
import com.whitecloud233.herobrine_companion.combat.poem.StandalonePoemController;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public final class StandalonePoemEvents {
    private StandalonePoemEvents() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!StandalonePoemController.enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.isUseItem() || (event.isAttack() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK)) {
            StandalonePoemAnimation.cancelLocal(); return;
        }
        // The server's animation contact windows own melee hits, including attacks into empty air.
        if (event.isAttack() && event.shouldSwingHand() && StandalonePoemAnimation.validLocal(mc)) {
            StandalonePoemAnimation.attack();
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        StandalonePoemRenderer.clearContext();
        StandalonePoemAnimation.tick();
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (StandalonePoemController.enabled()) CompletableFuture.runAsync(() -> PoemMotionLibrary.get().preload());
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) { StandalonePoemRenderer.renderFirstPerson(event); }
}
