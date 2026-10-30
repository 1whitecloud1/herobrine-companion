package com.whitecloud233.herobrine_companion.structure.template;

import net.minecraft.nbt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Litematica's dense, cross-long bit packing; negative region sizes describe the minimum corner. */
final class LitematicTemplateDecoder {
    private record Region(CompoundTag nbt, int x, int y, int z, int sx, int sy, int sz) { }

    static ImportedTemplate decode(CompoundTag root) throws IOException {
        int version = root.getInt("Version");
        if (version < 4 || version > 7) throw new IOException("Unsupported Litematica version: " + version);
        CompoundTag source = root.getCompound("Regions");
        List<Region> regions = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = minX, minZ = minX;
        int maxX = Integer.MIN_VALUE, maxY = maxX, maxZ = maxX;
        for (String name : source.getAllKeys().stream().sorted(Comparator.naturalOrder()).toList()) {
            CompoundTag region = source.getCompound(name), size = region.getCompound("Size"), pos = region.getCompound("Position");
            int sx = Math.abs(size.getInt("x")), sy = Math.abs(size.getInt("y")), sz = Math.abs(size.getInt("z"));
            TemplateLimits.denseVolume(sx, sy, sz);
            int x = corner(pos.getInt("x"), size.getInt("x"));
            int y = corner(pos.getInt("y"), size.getInt("y"));
            int z = corner(pos.getInt("z"), size.getInt("z"));
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, Math.addExact(x, sx)); maxY = Math.max(maxY, Math.addExact(y, sy)); maxZ = Math.max(maxZ, Math.addExact(z, sz));
            regions.add(new Region(region, x, y, z, sx, sy, sz));
        }
        if (regions.isEmpty()) throw new IOException("No Litematica regions");
        CompactTemplateBuilder out = new CompactTemplateBuilder(Math.subtractExact(maxX, minX), Math.subtractExact(maxY, minY), Math.subtractExact(maxZ, minZ));
        for (Region r : regions) {
            ListTag palette = r.nbt.getList("BlockStatePalette", Tag.TAG_COMPOUND);
            if (palette.isEmpty()) throw new IOException("Missing region palette");
            int bits = CompactTemplateBuilder.bits(palette.size());
            long[] packed = r.nbt.getLongArray("BlockStates");
            int volume = TemplateLimits.denseVolume(r.sx, r.sy, r.sz);
            if (packed.length != ((long) volume * bits + 63) / 64) throw new IOException("Invalid packed block array length");
            int[] mapping = out.region(r.x - minX, r.y - minY, r.z - minZ, r.sx, r.sy, r.sz, palette, bits, packed);
            for (Tag tag : r.nbt.getList("TileEntities", Tag.TAG_COMPOUND)) {
                CompoundTag entity = (CompoundTag) tag;
                int x = entity.getInt("x"), y = entity.getInt("y"), z = entity.getInt("z");
                if (x < 0 || x >= r.sx || y < 0 || y >= r.sy || z < 0 || z >= r.sz) throw new IOException("Invalid region block entity");
                int index = x + r.sx * (z + r.sz * y);
                long bit = (long) index * bits;
                int word = (int) (bit >>> 6), shift = (int) (bit & 63);
                long value = packed[word] >>> shift;
                if (shift + bits > 64) value |= packed[word + 1] << (64 - shift);
                int state = (int) (value & ((1L << bits) - 1));
                if (state >= palette.size()) throw new IOException("Invalid region palette index");
                out.tile(r.x - minX + x, r.y - minY + y, r.z - minZ + z, mapping[state], entity);
            }
        }
        return out.finish("litematic", root.contains("MinecraftDataVersion") ? root.getInt("MinecraftDataVersion") : 1343);
    }

    private static int corner(int position, int size) { return size < 0 ? Math.addExact(position, size + 1) : position; }
}
