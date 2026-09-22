package com.whitecloud233.modid.herobrine_companion.client.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.animation.StandalonePoemAnimation;
import com.whitecloud233.modid.herobrine_companion.client.render.StandalonePoemRenderer;
import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemMotionLibrary;
import com.whitecloud233.modid.herobrine_companion.combat.poem.StandalonePoemController;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public final class StandalonePoemEvents {
    private StandalonePoemEvents() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!StandalonePoemController.enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.isUseItem() || (event.isAttack() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK)) {
            StandalonePoemAnimation.cancelLocal(); return;
        }
        // The server's animation contact windows now own melee hits, including attacks into empty air.
        if (event.isAttack() && event.shouldSwingHand() && StandalonePoemAnimation.validLocal(mc)) {
            StandalonePoemAnimation.attack();
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
