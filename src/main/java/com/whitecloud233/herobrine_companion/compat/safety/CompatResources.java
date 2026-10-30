package com.whitecloud233.herobrine_companion.compat.safety;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.fml.ModList;

import java.util.List;

/** Keep foreign capability/profile JSON out of the resource set when its integration is disabled. */
public final class CompatResources {
    private CompatResources() { }

    public static void register(AddPackFindersEvent event) {
        for (String id : List.of("accessories", "armourers_workshop", "epicfight")) {
            if (!CompatPreflight.available(id)) continue;
            if (event.getPackType() == PackType.CLIENT_RESOURCES && !id.equals("epicfight")) continue;
            event.addRepositorySource(consumer -> {
                if (!CompatSafety.allowed(id)) return;
                CompatSafety.run(id, "register integration resources", () -> {
                    var modFile = ModList.get().getModFileById("herobrine_companion");
                    if (modFile == null) throw new IllegalStateException("Cannot locate mod resources");
                    var root = modFile.getFile().findResource("compat_packs", id);
                    String name = "herobrine_companion:compat/" + id;
                    var location = new net.minecraft.server.packs.PackLocationInfo(name, Component.literal("Herobrine Companion: " + id),
                            PackSource.BUILT_IN, java.util.Optional.empty());
                    Pack pack = Pack.readMetaAndCreate(location, new PathPackResources.PathResourcesSupplier(root),
                            event.getPackType(), new net.minecraft.server.packs.PackSelectionConfig(true, Pack.Position.TOP, true));
                    if (pack == null) throw new IllegalStateException("Cannot read integration pack " + id);
                    consumer.accept(pack);
                });
            });
        }
    }
}
