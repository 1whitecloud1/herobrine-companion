package com.whitecloud233.modid.herobrine_companion.compat.safety;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.LoggerFactory;

public final class CompatBootstrap {
    private CompatBootstrap() { }

    public static void register(IEventBus bus) {
        bus.addListener(CompatResources::register);
        if (CompatPreflight.available("epicfight")) {
            CompatSafety.run("epicfight", "register integration", () -> HeroEpicFightCompat.bootstrap(bus));
        }
        bus.addListener(CompatBootstrap::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (CompatPreflight.available("epicfight")) {
                CompatSafety.run("epicfight", "initialize runtime bridge", HeroEpicFightCompat::tryRegisterRuntimeBridge);
            }
            for (String id : CompatSafety.INTEGRATIONS) {
                String version = CompatPreflight.version(id);
                if (version != null) {
                    boolean available = CompatPreflight.available(id);
                    LoggerFactory.getLogger("HerobrineCompanion/Compat").info("{} {}: {}", id, version,
                            available ? "enabled" : CompatSafety.reason(id));
                }
            }
        });
    }
}
