package com.whitecloud233.modid.herobrine_companion.combat.poem;

/** A one-input buffer shared by the visual combo and the server's blade collision clock. */
public final class PoemComboClock {
    public static final double BUFFER_TICKS = 8;
    public static final double RESET_TICKS = 9;
    private int mode = -1, step = -1, serial;
    private double start, bufferedUntil = Double.NEGATIVE_INFINITY;
    private double lastRequest = Double.NEGATIVE_INFINITY;

    public boolean request(int requestedMode, double tick) {
        if (requestedMode < 0 || requestedMode >= 4 || !Double.isFinite(tick)) return false;
        if (tick - lastRequest < 1) return false;
        lastRequest = tick;
        if (requestedMode != mode) { mode = requestedMode; step = -1; bufferedUntil = Double.NEGATIVE_INFINITY; }
        if (step < 0 || tick >= start + timing().duration() * 20 + RESET_TICKS) {
            step = -1; return advance(tick);
        }
        if (tick + 1e-5 >= start + timing().recovery() * 20) return advance(tick);
        bufferedUntil = tick + BUFFER_TICKS;
        return false;
    }

    public boolean tick(double tick) {
        if (step >= 0 && tick <= bufferedUntil && tick + 1e-5 >= start + timing().recovery() * 20) return advance(tick);
        if (tick > bufferedUntil) bufferedUntil = Double.NEGATIVE_INFINITY;
        return false;
    }

    private boolean advance(double tick) {
        step = (step + 1) % 4; start = tick; serial++;
        bufferedUntil = Double.NEGATIVE_INFINITY;
        return true;
    }

    public void reset() { mode = step = -1; bufferedUntil = lastRequest = Double.NEGATIVE_INFINITY; }
    public void synchronize(int mode, int step, double start) {
        if (mode < 0 || mode > 3 || step < 0 || step > 3 || !Double.isFinite(start)) return;
        this.mode = mode; this.step = step; this.start = start;
    }
    public boolean active(double tick) { return step >= 0 && tick < start + timing().duration() * 20 + 3; }
    public PoemMotionLibrary.Timing timing() { return PoemMotionLibrary.get().timing(mode, step); }
    public int mode() { return mode; }
    public int step() { return step; }
    public int serial() { return serial; }
    public double start() { return start; }
}
