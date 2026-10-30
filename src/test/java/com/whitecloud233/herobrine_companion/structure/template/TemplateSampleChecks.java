package com.whitecloud233.herobrine_companion.structure.template;

import net.minecraft.nbt.Tag;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Optional real-file regression, run in a separate JVM with a 256 MiB heap. Never changes the input file. */
public final class TemplateSampleChecks {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Use -PtemplateFixture=<path to 汀洲书院.litematic>");
        Path file = Path.of(args[0]); byte[] bytes = Files.readAllBytes(file);
        long start = System.nanoTime();
        try { TemplateImportService.decode(file.getFileName().toString(), bytes, 3465); throw new AssertionError("Newer version must be rejected before decoding"); }
        catch (IOException expected) {
            if (!expected.getMessage().contains("3700") || !expected.getMessage().contains("3465")) throw expected;
            System.out.println("EARLY_VERSION_REJECTION_MS=" + (System.nanoTime() - start) / 1_000_000);
        }
        start = System.nanoTime();
        // Format-level inspection is separate from permission to import into the 1.20.1 server.
        ImportedTemplate imported = TemplateImportService.decode(file.getFileName().toString(), bytes);
        if (imported.x() != 463 || imported.y() != 141 || imported.z() != 323 || imported.blocks() != 1435030) throw new AssertionError("Real sample geometry/count changed");
        if (imported.nbt().getList("blocks", Tag.TAG_COMPOUND).size() != 3474) throw new AssertionError("Only actual block entities may become compound block records");
        System.out.println("COMPACT_DECODE_MS=" + (System.nanoTime() - start) / 1_000_000);
        TemplateBlockCursor cursor = new TemplateBlockCursor(imported.nbt(), true, 13, 11);
        Set<Long> chunks = new HashSet<>(); long previous = Long.MIN_VALUE; int cells = 0;
        while (cursor.next()) {
            long chunk = ((long) ((cursor.x() + 13) >> 4) << 32) | (((cursor.z() + 11) >> 4) & 0xffffffffL);
            if (chunk != previous && !chunks.add(chunk)) throw new AssertionError("Destination chunk revisited");
            previous = chunk; cells++;
        }
        if (cells != 21086409 || chunks.size() != 630) throw new AssertionError("Real sample traversal lost cells or revisited chunks");
        Path temp = Files.createTempDirectory("herobrine-real-template-");
        try {
            TemplateRepository repository = new TemplateRepository(temp, UUID.randomUUID());
            String id = repository.id("a".repeat(64));
            var entry = new TemplateEntry(id, file.getFileName().toString(), imported.format(), imported.x(), imported.y(), imported.z(), imported.blocks());
            try (var staged = repository.stage(entry, imported.nbt())) {
                if (!repository.list().isEmpty()) throw new AssertionError("Staging must not publish the catalog");
                repository.commit(staged);
            }
            if (repository.list().size() != 1 || repository.read(id).getList(CompactTemplateBuilder.REGIONS, Tag.TAG_COMPOUND).size() != 1) throw new AssertionError("Compact storage roundtrip failed");
            repository.delete(id);
        } finally {
            try (var paths = Files.walk(temp)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
        System.out.println("REAL_TEMPLATE_OK: 21086409 cells, 1435030 non-air blocks, 3474 block entities, 630 chunks; heap limit=" + Runtime.getRuntime().maxMemory() / 1048576 + " MiB");
    }
}
