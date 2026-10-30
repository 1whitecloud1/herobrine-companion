package com.whitecloud233.modid.herobrine_companion.compat.simplehats;

import com.whitecloud233.modid.herobrine_companion.client.render.HeroRenderer;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraftforge.fml.ModList;

public class HeroSimpleHatsCompat {

    public static boolean isLoaded() {
        return com.whitecloud233.modid.herobrine_companion.compat.safety.CompatPreflight.available("simplehats");
    }

    public static void attachRenderLayer(HeroRenderer renderer) {
        if (!isLoaded() || renderer == null) {
            return;
        }

        SimpleHatsSafeInvoker.attachRenderLayer(renderer);
    }

    private static class SimpleHatsSafeInvoker {

        static void attachRenderLayer(LivingEntityRenderer<HeroEntity, PlayerModel<HeroEntity>> renderer) {
            renderer.addLayer(new com.whitecloud233.modid.herobrine_companion.compat.safety.GuardedHeroLayer(renderer, "simplehats",
                    new fonnymunkey.simplehats.client.hat.HatLayer<>(renderer)));
        }
    }
}
