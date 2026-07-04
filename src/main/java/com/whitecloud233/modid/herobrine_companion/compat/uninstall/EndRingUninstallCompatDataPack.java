package com.whitecloud233.modid.herobrine_companion.compat.uninstall;

import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EndRingUninstallCompatDataPack {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PACK_DIR_NAME = "herobrine_companion_end_ring_compat";
    private static final String PACK_ID = "file/" + PACK_DIR_NAME;

    private static final String PACK_META = """
            {
              "pack": {
                "pack_format": 15,
                "description": "Keeps Herobrine Companion End Ring world data loadable after uninstall."
              }
            }
            """;

    private static final String END_RING_TYPE = """
            {
              "ultrawarm": false,
              "natural": false,
              "coordinate_scale": 1.0,
              "has_skylight": false,
              "has_ceiling": false,
              "ambient_light": 1.0,
              "fixed_time": 6000,
              "infiniburn": "#minecraft:infiniburn_end",
              "respawn_anchor_works": false,
              "has_raids": false,
              "monster_spawn_light_level": 0,
              "monster_spawn_block_light_limit": 0,
              "piglin_safe": false,
              "bed_works": false,
              "min_y": 0,
              "height": 1024,
              "logical_height": 1024,
              "effects": "herobrine_companion:end_ring_type"
            }
            """;

    private static final String END_RING_DIMENSION = """
            {
              "type": "herobrine_companion:end_ring_type",
              "generator": {
                "type": "minecraft:flat",
                "settings": {
                  "layers": [
                    {
                      "block": "minecraft:air",
                      "height": 1
                    }
                  ],
                  "biome": "minecraft:the_void"
                }
              }
            }
            """;

    private EndRingUninstallCompatDataPack() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        install(event.getServer());
    }

    private static void install(MinecraftServer server) {
        try {
            Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_DIR_NAME);
            writePackFiles(packRoot);
            enablePackForNextLoad(server);
            server.saveEverything(false, true, true);
        } catch (IOException e) {
            LOGGER.error("Failed to install End Ring uninstall compatibility datapack.", e);
        }
    }

    private static void writePackFiles(Path packRoot) throws IOException {
        writeUtf8(packRoot.resolve("pack.mcmeta"), PACK_META);
        writeUtf8(packRoot.resolve("data")
                .resolve(HerobrineCompanion.MODID)
                .resolve("dimension_type")
                .resolve("end_ring_type.json"), END_RING_TYPE);
        writeUtf8(packRoot.resolve("data")
                .resolve(HerobrineCompanion.MODID)
                .resolve("dimension")
                .resolve("end_ring_dimension.json"), END_RING_DIMENSION);
    }

    private static void enablePackForNextLoad(MinecraftServer server) {
        PackRepository packRepository = server.getPackRepository();
        packRepository.reload();
        packRepository.addPack(PACK_ID);

        List<String> enabled = new ArrayList<>(packRepository.getSelectedIds());
        if (!enabled.contains(PACK_ID)) {
            enabled.add(PACK_ID);
        }

        Collection<String> selectedIds = enabled;
        List<String> disabled = packRepository.getAvailableIds().stream()
                .filter(id -> !selectedIds.contains(id))
                .collect(Collectors.toList());
        disabled.remove(PACK_ID);

        WorldDataConfiguration current = server.getWorldData().getDataConfiguration();
        server.getWorldData().setDataConfiguration(new WorldDataConfiguration(
                new DataPackConfig(enabled, disabled),
                current.enabledFeatures()
        ));
    }

    private static void writeUtf8(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.isRegularFile(path)) {
            String existing = Files.readString(path, StandardCharsets.UTF_8);
            if (existing.equals(content)) {
                return;
            }
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
