package com.whitecloud233.modid.herobrine_companion.compat.safety;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;

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
                    Pack pack = Pack.readMetaAndCreate(name, Component.literal("Herobrine Companion: " + id), true,
                            packId -> new PathPackResources(packId, root, true), event.getPackType(), Pack.Position.TOP, PackSource.BUILT_IN);
                    if (pack == null) throw new IllegalStateException("Cannot read integration pack " + id);
                    consumer.accept(pack);
                });
            });
        }
    }
}
