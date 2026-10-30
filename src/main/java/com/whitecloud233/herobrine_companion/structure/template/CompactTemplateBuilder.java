package com.whitecloud233.herobrine_companion.structure.template;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.nbt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Keeps dense input bit-packed. Only palettes and actual block entities become compound tags. */
final class CompactTemplateBuilder {
    static final String REGIONS = "HCRegions";
    private final CompoundTag root = new CompoundTag();
    private final ListTag palette = new ListTag(), regions = new ListTag();
    private final Map<CompoundTag, Integer> states = new HashMap<>();
    private final Long2ObjectLinkedOpenHashMap<CompoundTag> tiles = new Long2ObjectLinkedOpenHashMap<>();
    private final int sx, sy, sz;

    CompactTemplateBuilder(int x, int y, int z) throws IOException {
        TemplateLimits.volume(x, y, z); sx = x; sy = y; sz = z;
        root.putInt("HCVersion", 1); root.put("size", TemplateNbtBuilder.ints(x, y, z));
        root.put("palette", palette); root.put(REGIONS, regions); root.put("entities", new ListTag());
    }

    int[] region(int x, int y, int z, int width, int height, int length, ListTag local, int bits, long[] data) throws IOException {
        int volume = TemplateLimits.denseVolume(width, height, length);
        if (local.isEmpty() || bits < 2 || bits > 31 || data.length != ((long) volume * bits + 63) / 64
                || x < 0 || y < 0 || z < 0 || (long) x + width > sx || (long) y + height > sy || (long) z + length > sz)
            throw new IOException("Invalid compact region");
        int[] mapping = new int[local.size()];
        for (int i = 0; i < local.size(); i++) {
            CompoundTag state = local.getCompound(i);
            if (state.getString("Name").isBlank()) throw new IOException("Invalid palette entry");
            mapping[i] = states.computeIfAbsent(state, key -> { palette.add(key); return palette.size() - 1; });
        }
        CompoundTag region = new CompoundTag();
        region.putIntArray("origin", new int[]{x, y, z}); region.putIntArray("size", new int[]{width, height, length});
        region.putIntArray("palette", mapping); region.putInt("bits", bits); region.putLongArray("data", data);
        regions.add(region);
        return mapping;
    }

    void tile(int x, int y, int z, int state, CompoundTag data) throws IOException {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) throw new IOException("Invalid block entity position");
        CompoundTag tile = new CompoundTag(), nbt = data.copy();
        nbt.remove("x"); nbt.remove("y"); nbt.remove("z"); nbt.remove("Pos");
        tile.put("pos", TemplateNbtBuilder.ints(x, y, z)); tile.putInt("state", state); tile.put("nbt", nbt);
        tile.putInt("HCRegion", regions.size() - 1);
        tiles.put(x + (long) sx * (z + (long) sz * y), tile);
    }

    ImportedTemplate finish(String format, int version) throws IOException {
        ListTag blocks = new ListTag();
        for (CompoundTag tile : tiles.values()) {
            int[] pos = TemplateNbtBuilder.position(tile.getList("pos", Tag.TAG_INT));
            boolean covered = false;
            for (int i = tile.getInt("HCRegion") + 1; i < regions.size(); i++) {
                if (TemplateBlockCursor.contains(regions.getCompound(i), pos[0], pos[1], pos[2])) { covered = true; break; }
            }
            if (!covered) { tile.remove("HCRegion"); blocks.add(tile); }
        }
        root.put("blocks", blocks); root.putInt("DataVersion", version);
        boolean[] solid = new boolean[palette.size()];
        for (int i = 0; i < solid.length; i++) solid[i] = !TemplateBlockCursor.empty(palette.getCompound(i).getString("Name"));
        TemplateBlockCursor cursor = new TemplateBlockCursor(root, false);
        int count = 0;
        while (cursor.next()) if (solid[cursor.state()]) count = Math.incrementExact(count);
        return new ImportedTemplate(root, format, sx, sy, sz, count);
    }

    static int bits(int paletteSize) { return Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteSize - 1)); }
    static long[] array(int volume, int bits) throws IOException {
        long length = ((long) volume * bits + 63) / 64;
        if (length > Integer.MAX_VALUE) throw new IOException("Packed array cannot be represented");
        return new long[(int) length];
    }
    static void pack(long[] data, int index, int bits, int value) {
        long bit = (long) index * bits; int word = (int) (bit >>> 6), shift = (int) (bit & 63);
        data[word] |= (long) value << shift;
        if (shift + bits > 64) data[word + 1] |= (long) value >>> (64 - shift);
    }
    static int unpack(long[] data, int index, int bits) {
        long bit = (long) index * bits; int word = (int) (bit >>> 6), shift = (int) (bit & 63);
        long value = data[word] >>> shift;
        if (shift + bits > 64) value |= data[word + 1] << (64 - shift);
        return (int) (value & ((1L << bits) - 1));
    }
}
