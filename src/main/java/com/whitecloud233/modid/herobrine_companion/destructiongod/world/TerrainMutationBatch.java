package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

import java.util.function.IntPredicate;

/** A stable batch cursor. The predicate returns true only after a real block-state change. */
final class TerrainMutationBatch {
    private final int size;
    private final IntPredicate change;
    private final Runnable flush;
    private int cursor;

    TerrainMutationBatch(int size, IntPredicate change, Runnable flush) {
        this.size = size;
        this.change = change;
        this.flush = flush;
    }

    boolean drain(TickBudget budget) {
        try {
            while (this.cursor < this.size && budget.tryVisit()) {
                if (this.change.test(this.cursor)) budget.consume();
                this.cursor++;
            }
            return this.cursor == this.size;
        } finally {
            // Every partially applied slice must update heightmaps, light and clients too.
            this.flush.run();
        }
    }
}
