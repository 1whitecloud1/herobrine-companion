package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import yesman.epicfight.api.animation.AnimationManager.AnimationRegistryEvent;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

public final class HeroEpicFightBridge {
    private static boolean registered;
    private static boolean armatureRegistered;
    private static final AssetAccessor<HumanoidArmature> HERO_NIGHTFALL_ARMATURE = Armatures.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "entity/hero_biped_nightfall"),
            HumanoidArmature::new
    );

    private HeroEpicFightBridge() {}

    public static synchronized void register(IEventBus modEventBus) {
        if (registered || modEventBus == null) {
            return;
        }
        registered = true;
        modEventBus.addListener(HeroEpicFightBridge::onEntityPatchRegistry);
        modEventBus.addListener(HeroEpicFightBridge::onAnimationRegistry);
    }

    public static synchronized void registerArmature() {
        if (armatureRegistered) {
            return;
        }
        armatureRegistered = true;
        Armatures.registerEntityTypeArmature(ModEvents.HERO.get(), HERO_NIGHTFALL_ARMATURE);
    }

    public static boolean isPatched(HeroEntity hero) {
        return hero != null && EpicFightCapabilities.getEntityPatch(hero, HeroEpicFightPatch.class) != null;
    }

    static AssetAccessor<HumanoidArmature> heroNightfallArmature() {
        return HERO_NIGHTFALL_ARMATURE;
    }

    private static void onEntityPatchRegistry(EntityPatchRegistryEvent event) {
        event.getTypeEntry().put(ModEvents.HERO.get(), entity -> HeroEpicFightPatch::new);
    }

    private static void onAnimationRegistry(AnimationRegistryEvent event) {
        HeroNightfallAnimationRegistry.onAnimationRegistry(event);
    }
}


