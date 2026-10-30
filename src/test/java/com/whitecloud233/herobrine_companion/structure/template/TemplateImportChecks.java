package com.whitecloud233.herobrine_companion.structure.template;

import net.minecraft.nbt.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Executable fixtures cover format semantics, corrupt input, upload ordering and durable catalogs. */
public final class TemplateImportChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        vanilla(); sponge(); litematic(); legacy(); upload(); storage();
        largeVanilla(); largeSponge(); largeLitematic(); largeLegacy();
        reject(() -> TemplateImportService.decode("bad.nbt", new byte[]{1, 2, 3}));
        reject(() -> TemplateImportService.decode("bad.nbt", new byte[TemplateLimits.MAX_FILE_BYTES + 1]));
        check(TemplateLimits.volume(256, 256, 256) == 16777216L, "Large bounding volumes must be accepted");
        check(TemplateLimits.volume(1024, 1024, 4096) == 4294967296L, "Bounding volume must not overflow int");
        reject(() -> TemplateLimits.volume(0, 1, 1));
        reject(() -> TemplateLimits.volume(-1, 1, 1));
        reject(() -> TemplateLimits.volume(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        reject(() -> TemplateLimits.denseVolume(1024, 1024, 4096));
        System.out.println("TemplateImportChecks: " + checks + " checks passed");
    }

    private static void vanilla() throws Exception {
        CompoundTag root = new CompoundTag(); root.put("size", TemplateNbtBuilder.ints(2, 1, 1));
        ListTag palette = new ListTag(); palette.add(state("minecraft:stone")); palette.add(state("minecraft:air")); root.put("palette", palette);
        ListTag blocks = new ListTag();
        for (int x = 0; x < 2; x++) { CompoundTag block = new CompoundTag(); block.put("pos", TemplateNbtBuilder.ints(x, 0, 0)); block.putInt("state", x); blocks.add(block); }
        root.put("blocks", blocks); root.putInt("DataVersion", 3465);
        ListTag entities = new ListTag(); entities.add(new CompoundTag()); root.put("entities", entities);
        for (boolean compressed : new boolean[]{false, true}) {
            var imported = TemplateImportService.decode("house.NBT", bytes(root, compressed));
            check(imported.x() == 2 && imported.blocks() == 1, "Vanilla dimensions/count");
            check(imported.nbt().getList("entities", Tag.TAG_COMPOUND).isEmpty(), "Entities must not be imported");
        }
        root.remove("palette"); ListTag palettes = new ListTag(); palettes.add(palette); root.put("palettes", palettes);
        check(TemplateImportService.decode("variant.nbt", bytes(root, true)).blocks() == 1, "Vanilla multiple palettes");
        blocks.getCompound(0).putInt("state", 9);
        reject(() -> TemplateImportService.decode("bad.nbt", bytes(root, true)));
    }

    private static void sponge() throws Exception {
        for (int version = 1; version <= 3; version++) {
            CompoundTag root = new CompoundTag(); root.putInt("Version", version); root.putInt("DataVersion", 3465);
            root.putShort("Width", (short) 2); root.putShort("Height", (short) 1); root.putShort("Length", (short) 1);
            CompoundTag palette = new CompoundTag(); palette.putInt("minecraft:oak_stairs[facing=east,half=top]", 130); palette.putInt("minecraft:chest", 0);
            CompoundTag container = version == 3 ? new CompoundTag() : root;
            container.put("Palette", palette); container.putByteArray(version == 3 ? "Data" : "BlockData", new byte[]{(byte) 130, 1, 0});
            CompoundTag tile = new CompoundTag(); tile.putIntArray("Pos", new int[]{1, 0, 0}); tile.putString("Id", "minecraft:chest");
            CompoundTag data = version == 3 ? new CompoundTag() : tile; data.putString("CustomName", "test chest");
            if (version == 3) tile.put("Data", data);
            ListTag tiles = new ListTag(); tiles.add(tile); container.put(version == 1 ? "TileEntities" : "BlockEntities", tiles);
            CompoundTag input = root;
            if (version == 3) { root.put("Blocks", container); input = new CompoundTag(); input.put("Schematic", root); }
            byte[] encoded = bytes(input, true);
            var result = TemplateImportService.decode("stairs.schem", encoded);
            check(result.blocks() == 2 && result.x() == 2, "Sponge v" + version + " dimensions");
            TemplateBlockCursor cursor = new TemplateBlockCursor(result.nbt(), false);
            check(cursor.next(), "Sponge first block");
            check(result.nbt().getList("palette", Tag.TAG_COMPOUND).getCompound(cursor.state()).getCompound("Properties").getString("facing").equals("east"), "Sponge block properties");
            check(cursor.next(), "Sponge second block");
            CompoundTag imported = cursor.data();
            check(imported.getString("id").equals("minecraft:chest") && imported.getString("CustomName").equals("test chest"), "Sponge block entity");
            container.putByteArray(version == 3 ? "Data" : "BlockData", new byte[]{(byte) 128});
            CompoundTag bad = input;
            reject(() -> TemplateImportService.decode("bad.schem", bytes(bad, true)));
        }
        CompoundTag truncated = new CompoundTag(); truncated.putInt("Version", 3);
        truncated.putShort("Width", (short) 30000); truncated.putShort("Height", (short) 30000); truncated.putShort("Length", (short) 1);
        CompoundTag container = new CompoundTag(), palette = new CompoundTag(); palette.putInt("minecraft:stone", 0);
        container.put("Palette", palette); truncated.put("Blocks", container);
        reject(() -> TemplateImportService.decode("truncated-large.schem", bytes(truncated, true)));
    }

    private static void litematic() throws Exception {
        CompoundTag root = new CompoundTag(); root.putInt("Version", 6); root.putInt("MinecraftDataVersion", 3465);
        CompoundTag regions = new CompoundTag(); root.put("Regions", regions);
        // 5-bit entries at indices 12 and 25 cross a long boundary.
        ListTag palette = new ListTag(); for (int i = 0; i < 17; i++) palette.add(state(i == 0 ? "minecraft:air" : "test:block_" + i));
        CompoundTag region = region(-1, 2, 3, -26, 1, 1); region.put("BlockStatePalette", palette);
        long[] packed = new long[(26 * 5 + 63) / 64];
        for (int i = 0; i < 26; i++) pack(packed, i, 5, i % 17);
        region.putLongArray("BlockStates", packed); regions.put("a", region);
        CompoundTag tile = new CompoundTag(); tile.putInt("x", 12); tile.putInt("y", 0); tile.putInt("z", 0); tile.putString("id", "test:tile");
        ListTag tiles = new ListTag(); tiles.add(tile); region.put("TileEntities", tiles);
        CompoundTag second = region(1, 2, 3, 1, 1, 1); second.put("BlockStatePalette", palette); second.putLongArray("BlockStates", new long[]{1}); regions.put("b", second);
        var result = TemplateImportService.decode("multi.litematic", bytes(root, true));
        check(result.x() == 28 && result.y() == 1 && result.z() == 1, "Litematic negative sizes and region offsets");
        TemplateBlockCursor cursor = new TemplateBlockCursor(result.nbt(), false);
        for (int i = 0; i < 26; i++) {
            check(cursor.next() && cursor.x() == i, "Negative region coordinate");
            String name = result.nbt().getList("palette", Tag.TAG_COMPOUND).getCompound(cursor.state()).getString("Name");
            check(name.equals(i % 17 == 0 ? "minecraft:air" : "test:block_" + i % 17), "Cross-long palette decoding at " + i);
            if (i == 12) check(cursor.data().getString("id").equals("test:tile"), "Litematic block entity coordinates");
        }
        check(cursor.next() && cursor.x() == 27 && !cursor.next(), "Separated region and gap");
        region.putLongArray("BlockStates", new long[1]);
        reject(() -> TemplateImportService.decode("bad.litematic", bytes(root, true)));
    }

    private static void legacy() throws Exception {
        CompoundTag root = new CompoundTag(); root.putString("Materials", "Alpha");
        root.putShort("Width", (short) 2); root.putShort("Height", (short) 1); root.putShort("Length", (short) 1);
        root.putByteArray("Blocks", new byte[]{1, 35}); root.putByteArray("Data", new byte[]{0, 14});
        var result = TemplateImportService.decode("old.schematic", bytes(root, true));
        check(result.blocks() == 2 && result.nbt().getInt("DataVersion") == 1343, "Legacy version");
        String wool = result.nbt().getList("palette", Tag.TAG_COMPOUND).getCompound(1).getString("Name");
        check(wool.equals("minecraft:red_wool"), "Legacy numeric metadata must preserve red wool");
        net.minecraft.SharedConstants.tryDetectVersion();
        CompoundTag fixed = net.minecraft.util.datafix.DataFixTypes.STRUCTURE.updateToCurrentVersion(
                net.minecraft.util.datafix.DataFixers.getDataFixer(), result.nbt(), 1343);
        check(fixed.getList(CompactTemplateBuilder.REGIONS, Tag.TAG_COMPOUND).size() == 1, "DataFixer must preserve compact regions");
        TemplateBlockCursor upgraded = new TemplateBlockCursor(fixed, false);
        check(upgraded.next() && upgraded.next()
                && fixed.getList("palette", Tag.TAG_COMPOUND).getCompound(upgraded.state()).getString("Name").equals("minecraft:red_wool"), "Upgraded palette indices must still address packed blocks");
        root.putByteArray("AddBlocks", new byte[]{(byte) 0x10});
        reject(() -> TemplateImportService.decode("modded.schematic", bytes(root, true)));
    }

    private static void upload() throws Exception {
        int size = TemplateLimits.CHUNK_BYTES + 17;
        var upload = new TemplateUploadBuffer(7, size, "x.nbt", "中文建筑");
        reject(upload::bytes);
        check(!upload.append(7, size, "x.nbt", "中文建筑", 0, new byte[TemplateLimits.CHUNK_BYTES]), "Incomplete upload");
        reject(() -> upload.append(7, size, "x.nbt", "中文建筑", 0, new byte[17]));
        reject(() -> upload.append(7, size, "x.nbt", "changed", 1, new byte[17]));
        check(upload.append(7, size, "x.nbt", "中文建筑", 1, new byte[17]), "Complete upload");
        check(upload.bytes().length == size, "Uploaded byte count");
        reject(() -> new TemplateUploadBuffer(1, -1, "x.nbt", "x"));
    }

    private static void largeVanilla() throws Exception {
        var result = TemplateImportService.decode("large.nbt", largeVanillaBytes());
        check(result.x() == 513 && result.blocks() == 131328, "Vanilla imports must exceed the former axis and explicit-block caps");
        check(result.nbt().getList("blocks", Tag.TAG_COMPOUND).size() == 131328, "Large vanilla import must keep every block");
        TemplateNbtBuilder sparse = new TemplateNbtBuilder(1024, 1024, 4096);
        sparse.block(0, 0, 0, state("minecraft:stone"), null);
        sparse.block(1023, 1023, 4095, state("minecraft:stone"), null);
        var sparseResult = TemplateImportService.decode("sparse.nbt", bytes(sparse.finish("nbt", 3465).nbt(), true));
        check(sparseResult.blocks() == 2 && sparseResult.z() == 4096, "Sparse vanilla bounding boxes may exceed int volume");
    }

    private static byte[] largeVanillaBytes() throws Exception {
        TemplateNbtBuilder out = new TemplateNbtBuilder(513, 16, 16);
        CompoundTag stone = state("minecraft:stone");
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 513; x++) out.block(x, y, z, stone, null);
        return bytes(out.finish("nbt", 3465).nbt(), true);
    }

    private static void largeSponge() throws Exception {
        CompoundTag root = new CompoundTag(); root.putInt("Version", 3); root.putInt("DataVersion", 3465);
        root.putShort("Width", (short) 513); root.putShort("Height", (short) 16); root.putShort("Length", (short) 16);
        CompoundTag container = new CompoundTag(), palette = new CompoundTag();
        palette.putInt("minecraft:stone", 0); container.put("Palette", palette);
        container.putByteArray("Data", new byte[131328]); root.put("Blocks", container);
        var result = TemplateImportService.decode("large.schem", bytes(root, true));
        check(result.x() == 513 && result.blocks() == 131328, "Sponge imports must exceed the former dimension/volume caps");
        TemplateBlockCursor cursor = last(result);
        check(cursor.x() == 512 && cursor.y() == 15 && cursor.z() == 15, "Large Sponge block ordering");
        check(result.nbt().getList("blocks", Tag.TAG_COMPOUND).isEmpty(), "Dense air/blocks must not expand into compound tags");
    }

    private static void largeLitematic() throws Exception {
        CompoundTag root = new CompoundTag(); root.putInt("Version", 6); root.putInt("MinecraftDataVersion", 3465);
        CompoundTag regions = new CompoundTag(); root.put("Regions", regions);
        ListTag palette = new ListTag(); palette.add(state("minecraft:stone"));
        CompoundTag a = region(0, 0, 0, 65537, 1, 1);
        a.put("BlockStatePalette", palette); a.putLongArray("BlockStates", new long[(65537 * 2 + 63) / 64]); regions.put("a", a);
        CompoundTag b = region(65538, 0, 0, 65536, 1, 1);
        b.put("BlockStatePalette", palette); b.putLongArray("BlockStates", new long[(65536 * 2 + 63) / 64]); regions.put("b", b);
        var result = TemplateImportService.decode("large-multi.litematic", bytes(root, true));
        check(result.x() == 131074 && result.blocks() == 131073, "Litematica must not cap axes, bounding volume, or cumulative region volume");
        check(last(result).x() == 131073, "Large separated regions must retain their gap");
    }

    private static void largeLegacy() throws Exception {
        CompoundTag root = new CompoundTag(); root.putString("Materials", "Alpha");
        root.putShort("Width", (short) 513); root.putShort("Height", (short) 16); root.putShort("Length", (short) 16);
        byte[] ids = new byte[131328]; Arrays.fill(ids, (byte) 1);
        root.putByteArray("Blocks", ids); root.putByteArray("Data", new byte[ids.length]);
        var result = TemplateImportService.decode("large.schematic", bytes(root, true));
        check(result.x() == 513 && result.blocks() == 131328, "Legacy imports must exceed the former dimension/volume caps");
    }

    private static void storage() throws Exception {
        var directory = Files.createTempDirectory("herobrine-template-check-");
        UUID owner = UUID.randomUUID();
        try {
            var repository = new TemplateRepository(directory, owner);
            String id = repository.id("a".repeat(64));
            var entry = new TemplateEntry(id, "中文城堡", "nbt", 1024, 1024, 4096, 2);
            CompoundTag root = new CompoundTag(); root.putString("sentinel", "persisted");
            repository.save(entry, root);
            check(new TemplateRepository(directory, owner).list().equals(List.of(entry)), "Catalog survives restart");
            check(new TemplateRepository(directory, UUID.randomUUID()).list().isEmpty(), "Player catalog isolation");
            check(NbtIo.readCompressed(directory.resolve("generated/herobrine_companion/structures/" + id.substring(id.indexOf(':') + 1) + ".nbt"), NbtAccounter.create(8L * 1024 * 1024)).getString("sentinel").equals("persisted"), "Vanilla generated structure path");
            repository.save(entry, root); check(repository.list().size() == 1, "Reimport is idempotent");
            reject(() -> repository.delete("herobrine_companion:../../outside"));
            reject(() -> new TemplateRepository(directory, UUID.randomUUID()).delete(id));
            repository.delete(id); check(new TemplateRepository(directory, owner).list().isEmpty(), "Deletion persists");
        } finally {
            // Only this test's freshly-created temporary directory is removed.
            try (var files = Files.walk(directory)) { for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }

    private static CompoundTag state(String name) { CompoundTag tag = new CompoundTag(); tag.putString("Name", name); return tag; }
    private static TemplateBlockCursor last(ImportedTemplate template) throws IOException {
        TemplateBlockCursor cursor = new TemplateBlockCursor(template.nbt(), false);
        while (cursor.next()) { }
        return cursor;
    }
    private static CompoundTag region(int x, int y, int z, int sx, int sy, int sz) {
        CompoundTag region = new CompoundTag(), pos = new CompoundTag(), size = new CompoundTag();
        pos.putInt("x", x); pos.putInt("y", y); pos.putInt("z", z);
        size.putInt("x", sx); size.putInt("y", sy); size.putInt("z", sz);
        region.put("Position", pos); region.put("Size", size); return region;
    }
    private static void pack(long[] data, int index, int bits, int value) {
        int bit = index * bits, word = bit / 64, shift = bit % 64;
        data[word] |= (long) value << shift;
        if (shift + bits > 64) data[word + 1] |= (long) value >>> (64 - shift);
    }
    private static byte[] bytes(CompoundTag root, boolean compressed) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (compressed) NbtIo.writeCompressed(root, bytes); else NbtIo.write(root, new DataOutputStream(bytes));
        return bytes.toByteArray();
    }
    private interface Failing { void run() throws Exception; }
    private static void reject(Failing action) throws Exception {
        try { action.run(); } catch (IOException expected) { checks++; return; }
        throw new AssertionError("Invalid template input was accepted");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
