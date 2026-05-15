package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

final class TickBudget {
    private int remaining;

    TickBudget(int remaining) {
        this.remaining = remaining;
    }

    boolean hasBudget() {
        return this.remaining > 0;
    }

    void consume() {
        if (this.remaining > 0) {
            this.remaining--;
        }
    }
}

