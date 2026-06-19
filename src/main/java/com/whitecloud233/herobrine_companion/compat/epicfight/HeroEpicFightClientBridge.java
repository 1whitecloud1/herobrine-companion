package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.whitecloud233.herobrine_companion.init.*;

import net.neoforged.bus.api.IEventBus;
import yesman.epicfight.api.client.event.EpicFightClientEventHooks;
import yesman.epicfight.api.client.event.types.registry.RegisterPatchedRenderersEvent;

public final class HeroEpicFightClientBridge {
    private static boolean registered;

    private HeroEpicFightClientBridge() {
    }

    public static synchronized void register(IEventBus modEventBus) {
        if (registered || modEventBus == null) {
            return;
        }
        registered = true;
        EpicFightClientEventHooks.Registry.ADD_PATCHED_ENTITY.registerEvent(HeroEpicFightClientBridge::onAddPatchedRenderers, "herobrine_companion:hero_patched_renderer");
    }

    private static void onAddPatchedRenderers(RegisterPatchedRenderersEvent.AddEntity event) {
        event.addPatchedEntityRenderer(ModEntities.HERO.get(), entityType -> new HeroPatchedHumanoidRenderer(event.getContext(), entityType).initLayerLast(event.getContext(), entityType));
    }
}

