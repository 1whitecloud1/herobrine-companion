package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.whitecloud233.herobrine_companion.init.*;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.neoforged.bus.api.IEventBus;
import yesman.epicfight.api.event.EpicFightEventHooks;
import yesman.epicfight.api.event.types.registry.EntityPatchRegistryEvent;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

public final class HeroEpicFightBridge {
    private static boolean registered;
    private static boolean armatureRegistered;

    private HeroEpicFightBridge() {}

    public static synchronized void register(IEventBus modEventBus) {
        if (registered || modEventBus == null) {
            return;
        }
        registered = true;
        EpicFightEventHooks.Registry.ENTITY_PATCH.registerEvent(HeroEpicFightBridge::onEntityPatchRegistry, "herobrine_companion:hero_entity_patch");
    }

    public static synchronized void registerArmature() {
        if (armatureRegistered) {
            return;
        }
        armatureRegistered = true;
        Armatures.registerEntityTypeArmature(ModEntities.HERO.get(), Armatures.BIPED);
    }

    public static boolean isPatched(HeroEntity hero) {
        return hero != null && EpicFightCapabilities.getEntityPatch(hero, HeroEpicFightPatch.class) != null;
    }

    private static void onEntityPatchRegistry(EntityPatchRegistryEvent event) {
        event.registerEntityPatch(ModEntities.HERO.get(), HeroEpicFightPatch::new);
    }
}


