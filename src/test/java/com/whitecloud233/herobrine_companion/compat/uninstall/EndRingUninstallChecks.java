package com.whitecloud233.herobrine_companion.compat.uninstall;

import com.google.gson.JsonParser;
import com.sun.source.tree.MethodTree;
import com.whitecloud233.herobrine_companion.regression.SourceFixturesChecks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/** Exercise the real file writer and persistent pack selection without starting a world. */
public final class EndRingUninstallChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of("build"), "end-ring-卸载-");
        var write = EndRingUninstallCompatDataPack.class.getDeclaredMethod("writePackFiles", Path.class);
        write.setAccessible(true);
        write.invoke(null, root);
        Path meta = root.resolve("pack.mcmeta");
        check(JsonParser.parseString(Files.readString(meta)).getAsJsonObject()
                .getAsJsonObject("pack").get("pack_format").getAsInt() == 48, "1.21.1 server pack format");
        for (String resource : new String[]{"dimension_type/end_ring_type.json", "dimension/end_ring_dimension.json"}) {
            Path installed = root.resolve("data/herobrine_companion/" + resource);
            try (var stream = EndRingUninstallChecks.class.getResourceAsStream("/data/herobrine_companion/" + resource)) {
                if (stream == null) throw new AssertionError("Missing bundled dimension " + resource);
                check(JsonParser.parseString(Files.readString(installed)).equals(
                        JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))),
                        "Installed dimension matches bundled data: " + resource);
            }
        }
        FileTime pinned = FileTime.fromMillis(1000);
        Files.setLastModifiedTime(meta, pinned);
        write.invoke(null, root);
        check(Files.getLastModifiedTime(meta).equals(pinned), "Unchanged data is not rewritten");
        Files.writeString(meta, "obsolete");
        write.invoke(null, root);
        check(JsonParser.parseString(Files.readString(meta)).isJsonObject(), "Obsolete data is refreshed");

        String source = Files.readString(Path.of("src/main/java/com/whitecloud233/herobrine_companion/compat/uninstall/EndRingUninstallCompatDataPack.java"));
        String selection = SourceFixturesChecks.members(source, member -> member instanceof MethodTree method
                && method.getName().contentEquals("enablePackForNextLoad"));
        try (var fixture = SourceFixturesChecks.compile("UninstallSelectionFixture", "import java.util.*; import java.util.stream.*; public class UninstallSelectionFixture {"
                + HARNESS + selection + "}")) {
            checks += (int) fixture.type().getMethod("run").invoke(null);
        }
        // Only files beneath this uniquely-created directory are removed.
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
        System.out.println("EndRingUninstallChecks: " + checks + " checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static final String HARNESS = """
        static final String PACK_ID = "file/herobrine_companion_end_ring_compat";
        record DataPackConfig(List<String> enabled, List<String> disabled) {}
        record WorldDataConfiguration(DataPackConfig dataPacks, Set<String> enabledFeatures) {}
        static class PackRepository {
            boolean accept;
            List<String> selected = new ArrayList<>(List.of("vanilla", "file/another-pack"));
            void reload() {}
            void addPack(String id) { if (accept && !selected.contains(id)) selected.add(id); }
            List<String> getSelectedIds() { return selected; }
            List<String> getAvailableIds() { return List.of("vanilla", "file/another-pack", "file/disabled", PACK_ID); }
        }
        static class WorldData {
            WorldDataConfiguration config = new WorldDataConfiguration(new DataPackConfig(List.of(), List.of()), Set.of("feature-a"));
            WorldDataConfiguration getDataConfiguration() { return config; }
            void setDataConfiguration(WorldDataConfiguration value) { config = value; }
        }
        static class MinecraftServer {
            PackRepository repository = new PackRepository(); WorldData world = new WorldData();
            PackRepository getPackRepository() { return repository; }
            WorldData getWorldData() { return world; }
        }
        public static int run() {
            int checks = 0;
            for (boolean accepts : new boolean[]{false, true}) {
                MinecraftServer server = new MinecraftServer(); server.repository.accept = accepts;
                enablePackForNextLoad(server); enablePackForNextLoad(server);
                var config = server.world.config;
                if (!config.dataPacks.enabled.equals(List.of("vanilla", "file/another-pack", PACK_ID))) throw new AssertionError("Pack must be persisted once, preserving selected packs");
                if (config.dataPacks.disabled.contains(PACK_ID) || !config.dataPacks.disabled.contains("file/disabled")) throw new AssertionError("Installed pack must not remain disabled");
                if (!config.enabledFeatures.equals(Set.of("feature-a"))) throw new AssertionError("World features changed");
                checks += 3;
            }
            return checks;
        }
        """;
}
