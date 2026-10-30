package com.whitecloud233.herobrine_companion.structure.template;

import net.minecraft.nbt.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Converts format-specific palettes/positions into one vanilla structure representation. */
final class TemplateNbtBuilder {
    private final ListTag palette = new ListTag();
    private final ListTag blocks = new ListTag();
    private final Map<CompoundTag, Integer> states = new HashMap<>();
    private final Map<Long, CompoundTag> positions = new java.util.LinkedHashMap<>();
    private final int x, y, z;

    TemplateNbtBuilder(int x, int y, int z) throws IOException {
        TemplateLimits.volume(x, y, z);
        this.x = x; this.y = y; this.z = z;
    }

    void block(int bx, int by, int bz, CompoundTag state, CompoundTag data) throws IOException {
        if (bx < 0 || by < 0 || bz < 0 || bx >= x || by >= y || bz >= z || state.getString("Name").isBlank()) {
            throw new IOException("Invalid block position or palette entry");
        }
        int index = states.computeIfAbsent(state, key -> { palette.add(key.copy()); return palette.size() - 1; });
        long key = bx + (long) x * (bz + (long) z * by);
        CompoundTag block = new CompoundTag();
        block.put("pos", ints(bx, by, bz));
        block.putInt("state", index);
        if (data != null) {
            CompoundTag copy = data.copy();
            copy.remove("x"); copy.remove("y"); copy.remove("z"); copy.remove("Pos");
            block.put("nbt", copy);
        }
        positions.put(key, block);
    }

    ImportedTemplate finish(String format, int dataVersion) throws IOException {
        blocks.addAll(positions.values());
        if (blocks.isEmpty()) throw new IOException("Template contains no blocks");
        CompoundTag root = new CompoundTag();
        root.put("size", ints(x, y, z));
        root.put("palette", palette);
        root.put("blocks", blocks);
        root.put("entities", new ListTag());
        root.putInt("DataVersion", dataVersion);
        int count = 0;
        for (Tag entry : blocks) {
            String name = palette.getCompound(((CompoundTag) entry).getInt("state")).getString("Name");
            if (!name.equals("minecraft:air") && !name.equals("minecraft:cave_air")
                    && !name.equals("minecraft:void_air") && !name.equals("minecraft:structure_void")) count++;
        }
        return new ImportedTemplate(root, format, x, y, z, count);
    }

    static ListTag ints(int... values) {
        ListTag list = new ListTag();
        for (int value : values) list.add(IntTag.valueOf(value));
        return list;
    }

    static int[] position(ListTag list) throws IOException {
        if (list.size() != 3) throw new IOException("Expected a three-coordinate position");
        return new int[]{list.getInt(0), list.getInt(1), list.getInt(2)};
    }
}
