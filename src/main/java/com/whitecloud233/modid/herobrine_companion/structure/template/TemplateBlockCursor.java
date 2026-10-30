package com.whitecloud233.modid.herobrine_companion.structure.template;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.*;
import java.io.IOException;

/** Allocation-free block traversal. Chunk order lets the placement scheduler load one chunk at a time. */
public final class TemplateBlockCursor {
    private final ListTag blocks, regions;
    private final boolean chunkOrder;
    private final int sx, sz;
    private final int alignX, alignZ;
    private final int[] sparseOrder;
    private final Long2ObjectOpenHashMap<CompoundTag> tiles = new Long2ObjectOpenHashMap<>();
    private int regionIndex = -1, sparseIndex, index, volume, bits, rx, ry, rz, width, height, length;
    private int cx, cz, lx, ly, lz, minX, maxX, minZ, maxZ;
    private int[] mapping;
    private long[] packed;
    private int x, y, z, state;
    private CompoundTag data;
    private long visited;

    public TemplateBlockCursor(CompoundTag root, boolean chunkOrder) throws IOException {
        this(root, chunkOrder, 0, 0);
    }

    public TemplateBlockCursor(CompoundTag root, boolean chunkOrder, int alignX, int alignZ) throws IOException {
        int[] size = TemplateNbtBuilder.position(root.getList("size", Tag.TAG_INT));
        TemplateLimits.volume(size[0], size[1], size[2]); sx = size[0]; sz = size[2];
        this.chunkOrder = chunkOrder;
        this.alignX = alignX & 15; this.alignZ = alignZ & 15;
        blocks = root.getList("blocks", Tag.TAG_COMPOUND); regions = root.getList(CompactTemplateBuilder.REGIONS, Tag.TAG_COMPOUND);
        if (regions.isEmpty() && chunkOrder) {
            sparseOrder = java.util.stream.IntStream.range(0, blocks.size()).toArray();
            it.unimi.dsi.fastutil.ints.IntArrays.quickSort(sparseOrder, (a, b) -> {
                ListTag ap = blocks.getCompound(a).getList("pos", Tag.TAG_INT), bp = blocks.getCompound(b).getList("pos", Tag.TAG_INT);
                int result = Integer.compare((ap.getInt(0) + this.alignX) >> 4, (bp.getInt(0) + this.alignX) >> 4);
                if (result == 0) result = Integer.compare((ap.getInt(2) + this.alignZ) >> 4, (bp.getInt(2) + this.alignZ) >> 4);
                return result == 0 ? Integer.compare(a, b) : result;
            });
        } else sparseOrder = null;
        if (!regions.isEmpty()) for (Tag tag : blocks) {
            CompoundTag block = (CompoundTag) tag;
            int[] pos = TemplateNbtBuilder.position(block.getList("pos", Tag.TAG_INT));
            tiles.put(key(pos[0], pos[1], pos[2]), block.getCompound("nbt"));
        }
    }

    public boolean next() throws IOException {
        if (regions.isEmpty()) {
            if (sparseIndex == blocks.size()) return false;
            CompoundTag block = blocks.getCompound(sparseOrder == null ? sparseIndex++ : sparseOrder[sparseIndex++]);
            int[] pos = TemplateNbtBuilder.position(block.getList("pos", Tag.TAG_INT));
            x = pos[0]; y = pos[1]; z = pos[2]; state = block.getInt("state");
            data = block.contains("nbt", Tag.TAG_COMPOUND) ? block.getCompound("nbt") : null; visited++;
            return true;
        }
        while (true) {
            if (regionIndex < 0 || index == volume) {
                if (++regionIndex == regions.size()) return false;
                CompoundTag region = regions.getCompound(regionIndex);
                int[] origin = region.getIntArray("origin"), size = region.getIntArray("size");
                if (origin.length != 3 || size.length != 3) throw new IOException("Invalid region coordinates");
                rx = origin[0]; ry = origin[1]; rz = origin[2]; width = size[0]; height = size[1]; length = size[2];
                volume = TemplateLimits.denseVolume(width, height, length); index = 0;
                bits = region.getInt("bits"); mapping = region.getIntArray("palette"); packed = region.getLongArray("data");
                if (bits < 2 || bits > 31 || packed.length != ((long) volume * bits + 63) / 64) throw new IOException("Invalid region bit array");
                cx = (rx + this.alignX) >> 4; cz = (rz + this.alignZ) >> 4; startChunk();
            }
            int blockIndex;
            if (chunkOrder) {
                x = lx; y = ry + ly; z = lz;
                blockIndex = (x - rx) + width * ((z - rz) + length * ly);
                if (++lx == maxX) {
                    lx = minX;
                    if (++lz == maxZ) {
                        lz = minZ;
                        if (++ly == height) {
                            if (++cx > ((rx + width - 1 + alignX) >> 4)) { cx = (rx + alignX) >> 4; cz++; }
                            if (index + 1 < volume) startChunk();
                        }
                    }
                }
            } else { blockIndex = index; x = rx + index % width; y = ry + index / (width * length); z = rz + index / width % length; }
            index++; visited++;
            long bit = (long) blockIndex * bits; int word = (int) (bit >>> 6), shift = (int) (bit & 63);
            long value = packed[word] >>> shift;
            if (shift + bits > 64) value |= packed[word + 1] << (64 - shift);
            int local = (int) (value & ((1L << bits) - 1));
            if (local >= mapping.length) throw new IOException("Invalid region palette index");
            state = mapping[local];
            boolean covered = false;
            for (int i = regionIndex + 1; i < regions.size(); i++) {
                if (contains(regions.getCompound(i), x, y, z)) { covered = true; break; }
            }
            if (!covered) return true;
        }
    }

    private void startChunk() {
        minX = Math.max(rx, (cx << 4) - alignX); maxX = Math.min(rx + width, (cx << 4) + 16 - alignX);
        minZ = Math.max(rz, (cz << 4) - alignZ); maxZ = Math.min(rz + length, (cz << 4) + 16 - alignZ);
        lx = minX; lz = minZ; ly = 0;
    }
    private long key(int x, int y, int z) { return x + (long) sx * (z + (long) sz * y); }
    static boolean contains(CompoundTag region, int x, int y, int z) {
        int[] origin = region.getIntArray("origin"), size = region.getIntArray("size");
        return origin.length == 3 && size.length == 3 && x >= origin[0] && y >= origin[1] && z >= origin[2]
                && (long) x < (long) origin[0] + size[0] && (long) y < (long) origin[1] + size[1] && (long) z < (long) origin[2] + size[2];
    }
    static boolean empty(String name) { return name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air") || name.equals("minecraft:structure_void"); }
    public int x() { return x; } public int y() { return y; } public int z() { return z; } public int state() { return state; }
    public long visited() { return visited; }

    /** 模板登记的方块条目数（进度分母；region 格式按体积累计）。 */
    public long totalBlocks() {
        return regions.isEmpty() ? blocks.size() : (long) volume * Math.max(1, regions.size());
    }

    public CompoundTag data() { return regions.isEmpty() ? data : tiles.get(key(x, y, z)); }
}
