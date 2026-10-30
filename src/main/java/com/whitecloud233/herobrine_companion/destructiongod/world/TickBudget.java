package com.whitecloud233.herobrine_companion.destructiongod.world;

final class TickBudget {
    private int remaining;
    private int remainingVisits;

    TickBudget(int remaining) {
        this(remaining, (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, remaining) * 4));
    }

    TickBudget(int remaining, int visits) {
        this.remaining = Math.max(0, remaining);
        this.remainingVisits = Math.max(0, visits);
    }

    boolean hasBudget() {
        return this.remaining > 0 && this.remainingVisits > 0;
    }

    boolean tryVisit() {
        if (!this.hasBudget()) return false;
        this.remainingVisits--;
        return true;
    }

    void consume() {
        if (this.remaining > 0) {
            this.remaining--;
        }
    }
}

