package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

import java.util.ArrayDeque;
import java.util.Deque;

/** Owned by one task; no new logical stage runs until all its submitted changes have drained. */
final class TerrainMutationQueue {
    private final Deque<TerrainMutationBatch> pending = new ArrayDeque<>();

    void add(TerrainMutationBatch batch) { this.pending.addLast(batch); }

    boolean isEmpty() { return this.pending.isEmpty(); }

    boolean drain(TickBudget budget) {
        while (!this.pending.isEmpty()) {
            if (!this.pending.getFirst().drain(budget)) return false;
            this.pending.removeFirst();
        }
        return true;
    }
}
