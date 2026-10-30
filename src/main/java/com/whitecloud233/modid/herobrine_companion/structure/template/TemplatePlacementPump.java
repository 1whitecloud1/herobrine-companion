package com.whitecloud233.modid.herobrine_companion.structure.template;

/** Tick-sized traversal. Waiting for a chunk retains the pending block without replaying or losing it. */
public final class TemplatePlacementPump {
    @FunctionalInterface public interface Sink { boolean apply(TemplateBlockCursor block) throws Exception; }
    private final TemplateBlockCursor cursor;
    private boolean pending, complete;
    public TemplatePlacementPump(TemplateBlockCursor cursor) { this.cursor = cursor; }

    public boolean tick(int maxCells, long deadline, Sink sink) throws Exception {
        for (int steps = 0; !complete && steps < maxCells && System.nanoTime() < deadline; steps++) {
            if (!pending) {
                if (!cursor.next()) { complete = true; break; }
                pending = true;
            }
            if (!sink.apply(cursor)) break;
            pending = false;
        }
        return complete;
    }

    /** 已遍历的方块数（用于进度播报；含被跳过的空气 / structure_void）。 */
    public long visited() {
        return cursor.visited();
    }

    /** 模板登记的总方块数（palette 里 blocks 表的条目数，用于算百分比）。 */
    public long totalBlocks() {
        return cursor.totalBlocks();
    }
}
