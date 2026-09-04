package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.init.ModEntities;
import com.whitecloud233.modid.herobrine_companion.init.ModParticles;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import yesman.epicfight.api.animation.AnimationManager;
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
        // 终末之诗刀光：注册自持的加法混合发光拖尾粒子生成器（仅 EpicFight 环境生效）
        modEventBus.addListener(HeroEpicFightClientBridge::onRegisterParticleProviders);
    }

    private static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.GLOW_TRAIL.get(), GlowTrailParticle.Provider::new);
    }

    private static void onAddPatchedRenderers(PatchedRenderersEvent.Add event) {
        event.addPatchedEntityRenderer(ModEntities.HERO.get(), entityType -> new HeroPatchedHumanoidRenderer(event.getContext(), entityType).initLayerLast(event.getContext(), entityType));
    }
}

