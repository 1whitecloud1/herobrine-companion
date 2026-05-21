package com.whitecloud233.modid.herobrine_companion.compat.accessories;

import com.whitecloud233.modid.herobrine_companion.client.render.HeroRenderer;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;

public final class HeroAccessoriesClientCompat {

    private HeroAccessoriesClientCompat() {
    }

    public static void attachRenderLayer(HeroRenderer renderer) {
        if (!HeroAccessoriesCompat.isLoaded() || renderer == null) {
            return;
        }
        AccessoriesClientSafeInvoker.attachRenderLayer(renderer);
    }

    private static final class AccessoriesClientSafeInvoker {

        private AccessoriesClientSafeInvoker() {
        }

        static void attachRenderLayer(LivingEntityRenderer<HeroEntity, PlayerModel<HeroEntity>> renderer) {
            renderer.addLayer(new io.wispforest.accessories.client.AccessoriesRenderLayer<>(renderer));
        }
    }
}
