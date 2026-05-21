package com.whitecloud233.modid.herobrine_companion.compat.waveycapes;

import com.whitecloud233.modid.herobrine_companion.client.render.HeroRenderer;
import net.minecraftforge.fml.ModList;

public final class HeroWaveyCapesCompat {

    private HeroWaveyCapesCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("waveycapes");
    }

    public static void attachRenderLayer(HeroRenderer renderer) {
        if (!isLoaded() || renderer == null) {
            return;
        }
        WaveyCapesSafeInvoker.attachRenderLayer(renderer);
    }

    private static final class WaveyCapesSafeInvoker {

        private WaveyCapesSafeInvoker() {
        }

        static void attachRenderLayer(HeroRenderer renderer) {
            renderer.addHeroRenderLayer(new HeroWaveyCapeLayer(renderer));
        }
    }
}
