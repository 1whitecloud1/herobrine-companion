package com.whitecloud233.herobrine_companion.structure.template;

import net.minecraft.nbt.*;
import net.minecraft.util.datafix.fixes.BlockStateData;
import java.io.IOException;
import java.util.Arrays;

/** MCEdit/WorldEdit Alpha .schematic. Vanilla's legacy table preserves block metadata. */
final class LegacyTemplateDecoder {
    static ImportedTemplate decode(CompoundTag root) throws IOException {
        if (!root.getString("Materials").equals("Alpha")) throw new IOException("Only Alpha schematic materials are supported");
        int x = root.getShort("Width") & 65535, y = root.getShort("Height") & 65535, z = root.getShort("Length") & 65535;
        int volume = TemplateLimits.denseVolume(x, y, z);
        byte[] ids = root.getByteArray("Blocks"), meta = root.getByteArray("Data"), high = root.getByteArray("AddBlocks");
        if (ids.length != volume || meta.length != volume || (high.length != 0 && high.length != (volume + 1L) / 2))
            throw new IOException("Invalid legacy block array length");
        CompactTemplateBuilder out = new CompactTemplateBuilder(x, y, z);
        int[] indices = new int[65536]; Arrays.fill(indices, -1);
        ListTag palette = new ListTag();
        for (int i = 0; i < volume; i++) {
            int id = (ids[i] & 255) | (high.length == 0 ? 0 : ((high[i / 2] >>> ((i & 1) * 4)) & 15) << 8);
            int code = id << 4 | meta[i] & 15;
            if (indices[code] < 0) {
                var legacy = BlockStateData.getTag(code);
                if (legacy == null) throw new IOException("Unknown legacy block: " + id + ":" + (meta[i] & 15));
                Tag tag = (Tag) legacy.convert(NbtOps.INSTANCE).getValue();
                if (!(tag instanceof CompoundTag compound)) throw new IOException("Invalid legacy block state");
                CompoundTag state = compound;
                // Unknown numeric mod blocks must never silently become air.
                if (id != 0 && state.getString("Name").equals("minecraft:air")) throw new IOException("Unknown legacy block: " + id);
                indices[code] = palette.size(); palette.add(state);
            }
        }
        int bits = CompactTemplateBuilder.bits(palette.size());
        long[] packed = CompactTemplateBuilder.array(volume, bits);
        for (int i = 0; i < volume; i++) CompactTemplateBuilder.pack(packed, i, bits, indices[code(ids, meta, high, i)]);
        int[] mapping = out.region(0, 0, 0, x, y, z, palette, bits, packed);
        for (Tag value : root.getList("TileEntities", Tag.TAG_COMPOUND)) {
            CompoundTag data = (CompoundTag) value;
            int bx = data.getInt("x"), by = data.getInt("y"), bz = data.getInt("z");
            if (bx < 0 || bx >= x || by < 0 || by >= y || bz < 0 || bz >= z) throw new IOException("Invalid legacy block entity");
            out.tile(bx, by, bz, mapping[indices[code(ids, meta, high, bx + x * (bz + z * by))]], data);
        }
        return out.finish("schematic", 1343);
    }
    private static int code(byte[] ids, byte[] meta, byte[] high, int i) {
        int id = (ids[i] & 255) | (high.length == 0 ? 0 : ((high[i / 2] >>> ((i & 1) * 4)) & 15) << 8);
        return id << 4 | meta[i] & 15;
    }
}
