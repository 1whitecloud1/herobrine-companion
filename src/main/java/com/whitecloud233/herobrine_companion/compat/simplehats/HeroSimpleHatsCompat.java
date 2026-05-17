package com.whitecloud233.herobrine_companion.compat.simplehats;

import com.whitecloud233.herobrine_companion.client.render.HeroRenderer;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.neoforged.fml.ModList;

public class HeroSimpleHatsCompat {

    public static boolean isLoaded() {
        return ModList.get().isLoaded("simplehats");
    }

    public static void attachRenderLayer(HeroRenderer renderer) {
        if (!isLoaded() || renderer == null) {
            return;
        }

        SimpleHatsSafeInvoker.attachRenderLayer(renderer);
    }

    private static class SimpleHatsSafeInvoker {

        static void attachRenderLayer(LivingEntityRenderer<HeroEntity, PlayerModel<HeroEntity>> renderer) {
            renderer.addLayer(new fonnymunkey.simplehats.client.hat.HatLayer<>(renderer));
        }
    }
}
