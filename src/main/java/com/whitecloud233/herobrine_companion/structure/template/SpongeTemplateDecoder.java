package com.whitecloud233.herobrine_companion.structure.template;

import net.minecraft.nbt.*;
import java.io.IOException;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/** Sponge .schem versions 1/2/3, with their different block containers and block-entity layouts. */
final class SpongeTemplateDecoder {
    static ImportedTemplate decode(CompoundTag input) throws IOException {
        CompoundTag root = input.contains("Schematic", Tag.TAG_COMPOUND) ? input.getCompound("Schematic") : input;
        int version = root.getInt("Version");
        if (version < 1 || version > 3) throw new IOException("Unsupported Sponge schematic version: " + version);
        int x = root.getShort("Width") & 65535, y = root.getShort("Height") & 65535, z = root.getShort("Length") & 65535;
        int volume = TemplateLimits.denseVolume(x, y, z);
        CompactTemplateBuilder out = new CompactTemplateBuilder(x, y, z);
        CompoundTag container = version == 3 ? root.getCompound("Blocks") : root;
        CompoundTag palette = container.getCompound("Palette");
        Int2IntOpenHashMap states = new Int2IntOpenHashMap(); states.defaultReturnValue(-1);
        ListTag local = new ListTag();
        for (String key : palette.getAllKeys()) {
            int index = palette.getInt(key);
            if (index < 0 || states.put(index, local.size()) != -1) throw new IOException("Invalid palette");
            local.add(state(key));
        }
        byte[] bytes = container.getByteArray(version == 3 ? "Data" : "BlockData");
        if (local.isEmpty() || bytes.length < volume) throw new IOException("Missing palette or truncated block data");
        int bits = CompactTemplateBuilder.bits(local.size());
        long[] packed = CompactTemplateBuilder.array(volume, bits);
        int[] mapping = out.region(0, 0, 0, x, y, z, local, bits, packed);
        ListTag entityList = container.getList(version == 1 ? "TileEntities" : "BlockEntities", Tag.TAG_COMPOUND);
        int cursor = 0;
        for (int i = 0; i < volume; i++) {
            int id = 0, shift = 0, next;
            do {
                if (cursor >= bytes.length || shift >= 35) throw new IOException("Truncated or oversized palette varint");
                next = bytes[cursor++] & 255;
                if (shift == 28 && (next & 0xF8) != 0) throw new IOException("Palette varint overflow");
                id |= (next & 127) << shift;
                shift += 7;
            } while ((next & 128) != 0);
            int state = states.get(id);
            if (state < 0) throw new IOException("Unknown palette index: " + id);
            CompactTemplateBuilder.pack(packed, i, bits, state);
        }
        if (cursor != bytes.length) throw new IOException("Unexpected trailing block data");
        for (Tag value : entityList) {
            CompoundTag entity = (CompoundTag) value;
            int[] pos = entity.getIntArray("Pos");
            if (pos.length != 3 || pos[0] < 0 || pos[0] >= x || pos[1] < 0 || pos[1] >= y || pos[2] < 0 || pos[2] >= z)
                throw new IOException("Invalid block entity position");
            CompoundTag data = version == 3 ? entity.getCompound("Data").copy() : entity.copy();
            data.putString("id", entity.getString("Id")); data.remove("Id");
            int index = pos[0] + x * (pos[2] + z * pos[1]);
            out.tile(pos[0], pos[1], pos[2], mapping[CompactTemplateBuilder.unpack(packed, index, bits)], data);
        }
        return out.finish("schem v" + version, root.contains("DataVersion") ? root.getInt("DataVersion") : 1631);
    }

    private static CompoundTag state(String text) throws IOException {
        CompoundTag state = new CompoundTag();
        int bracket = text.indexOf('[');
        state.putString("Name", bracket < 0 ? text : text.substring(0, bracket));
        if (bracket >= 0) {
            if (!text.endsWith("]")) throw new IOException("Invalid block state: " + text);
            CompoundTag properties = new CompoundTag();
            String body = text.substring(bracket + 1, text.length() - 1);
            if (!body.isEmpty()) for (String property : body.split(",")) {
                String[] pair = property.split("=", -1);
                if (pair.length != 2 || pair[0].isEmpty() || pair[1].isEmpty()) throw new IOException("Invalid block property");
                properties.putString(pair[0], pair[1]);
            }
            state.put("Properties", properties);
        }
        return state;
    }
}
