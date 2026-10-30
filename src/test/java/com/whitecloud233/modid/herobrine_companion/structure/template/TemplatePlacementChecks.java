package com.whitecloud233.modid.herobrine_companion.structure.template;

import net.minecraft.nbt.*;
import java.util.*;

public final class TemplatePlacementChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        overlapAndChunkOrder(); pump();
        System.out.println("TemplatePlacementChecks: " + checks + " checks passed");
    }
    private static void overlapAndChunkOrder() throws Exception {
        CompactTemplateBuilder out = new CompactTemplateBuilder(18, 2, 18);
        ListTag palette = new ListTag(); palette.add(state("minecraft:air")); palette.add(state("minecraft:stone")); palette.add(state("minecraft:chest"));
        long[] data = CompactTemplateBuilder.array(648, 2);
        for (int i = 0; i < 648; i++) CompactTemplateBuilder.pack(data, i, 2, i == 3 + 18 * 4 ? 2 : 1);
        int[] mapping = out.region(0, 0, 0, 18, 2, 18, palette, 2, data);
        CompoundTag chest = new CompoundTag(); chest.putString("id", "minecraft:chest");
        out.tile(3, 0, 4, mapping[2], chest);
        ListTag air = new ListTag(); air.add(state("minecraft:air"));
        out.region(3, 0, 4, 2, 1, 2, air, 2, new long[]{0});
        var result = out.finish("test", 3465);
        check(result.blocks() == 644, "Overlapping regions count only final non-air cells");
        check(result.nbt().getList("blocks", Tag.TAG_COMPOUND).isEmpty(), "Later air must remove an earlier block entity");
        Map<String, Integer> expected = collect(new TemplateBlockCursor(result.nbt(), false));
        for (int ax : new int[]{0, 5, 15}) for (int az : new int[]{0, 7, 15}) {
            Map<String, Integer> actual = collect(new TemplateBlockCursor(result.nbt(), true, ax, az));
            check(actual.equals(expected) && actual.size() == 648, "Chunk-aligned traversal must preserve every final cell");
        }
        // A single dense region must visit each destination chunk once, even at a non-aligned origin.
        CompactTemplateBuilder single = new CompactTemplateBuilder(33, 3, 35);
        single.region(0, 0, 0, 33, 3, 35, air, 2, CompactTemplateBuilder.array(33 * 3 * 35, 2));
        var cursor = new TemplateBlockCursor(single.finish("test", 3465).nbt(), true, 13, 11);
        Set<Long> closed = new HashSet<>(); long previous = Long.MIN_VALUE; int cells = 0;
        while (cursor.next()) {
            long chunk = ((long) ((cursor.x() + 13) >> 4) << 32) | (((cursor.z() + 11) >> 4) & 0xffffffffL);
            if (chunk != previous) { check(closed.add(chunk), "Destination chunk must not be revisited"); previous = chunk; }
            cells++;
        }
        check(cells == 33 * 3 * 35 && closed.size() == 9, "Chunk order must cover all cells and destination chunks");
    }
    private static Map<String, Integer> collect(TemplateBlockCursor cursor) throws Exception {
        Map<String, Integer> result = new HashMap<>();
        while (cursor.next()) {
            String key = cursor.x() + "," + cursor.y() + "," + cursor.z();
            check(result.put(key, cursor.state()) == null, "No duplicate final positions");
        }
        return result;
    }
    private static void pump() throws Exception {
        TemplateNbtBuilder out = new TemplateNbtBuilder(3, 1, 1);
        for (int x = 0; x < 3; x++) out.block(x, 0, 0, state("minecraft:stone"), null);
        TemplatePlacementPump pump = new TemplatePlacementPump(new TemplateBlockCursor(out.finish("test", 3465).nbt(), true));
        List<Integer> seen = new ArrayList<>();
        check(!pump.tick(2, Long.MAX_VALUE, block -> false) && seen.isEmpty(), "Unavailable chunk yields before mutation");
        check(!pump.tick(2, Long.MAX_VALUE, block -> { seen.add(block.x()); return true; }) && seen.equals(List.of(0, 1)), "Tick cell budget");
        check(!pump.tick(2, 0, block -> { throw new AssertionError("Expired deadline must not mutate"); }), "Expired time budget");
        check(pump.tick(2, Long.MAX_VALUE, block -> { seen.add(block.x()); return true; }) && seen.equals(List.of(0, 1, 2)), "Resume pending block without loss or replay");
        check(pump.tick(2, Long.MAX_VALUE, block -> { throw new AssertionError("Completed job must not replay"); }), "Completion is stable");
    }
    private static CompoundTag state(String name) { CompoundTag state = new CompoundTag(); state.putString("Name", name); return state; }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
}
