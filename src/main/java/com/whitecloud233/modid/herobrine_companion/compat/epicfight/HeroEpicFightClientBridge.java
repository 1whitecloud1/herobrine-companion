package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.init.ModEntities;
import net.minecraftforge.eventbus.api.IEventBus;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;

public final class HeroEpicFightClientBridge {
    private static boolean registered;

    private HeroEpicFightClientBridge() {
    }

    public static synchronized void register(IEventBus modEventBus) {
        if (registered || modEventBus == null) {
            return;
        }
        registered = true;
        modEventBus.addListener(HeroEpicFightClientBridge::onAddPatchedRenderers);
    }

    private static void onAddPatchedRenderers(PatchedRenderersEvent.Add event) {
        event.addPatchedEntityRenderer(ModEntities.HERO.get(), entityType -> new HeroPatchedHumanoidRenderer(event.getContext(), entityType).initLayerLast(event.getContext(), entityType));
    }
}

