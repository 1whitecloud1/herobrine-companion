package com.whitecloud233.modid.herobrine_companion.combat.poem;

/** One buffered attack intent, shared by local prediction and authoritative blade collision. */
public final class PoemComboClock {
    public static final double BUFFER_TICKS = 8;
    public static final double RESET_TICKS = 9;
    private int mode = -1, step = -1, serial, bufferedMove;
    private double start, bufferedUntil = Double.NEGATIVE_INFINITY;
    private double lastRequest = Double.NEGATIVE_INFINITY;

    public boolean request(int requestedMode, double tick) { return request(requestedMode, 0, tick); }

    /** Zero requests the next ground cut; special intents never choose an arbitrary combo stage. */
    public boolean request(int requestedMode, int move, double tick) {
        if (requestedMode < 0 || requestedMode >= 4 || !Double.isFinite(tick)
                || !(move == 0 || move >= PoemMotionLibrary.DASH && PoemMotionLibrary.validMove(move))) return false;
        if (tick - lastRequest < 1) return false;
        lastRequest = tick;
        if (requestedMode != mode) { mode = requestedMode; step = -1; bufferedUntil = Double.NEGATIVE_INFINITY; }
        if (step < 0 || tick >= start + timing().duration() * 20 + RESET_TICKS) {
            step = -1; return advance(tick, move);
        }
        if (tick + 1e-5 >= start + timing().recovery() * 20) return advance(tick, move);
        bufferedMove = move;
        bufferedUntil = tick + BUFFER_TICKS;
        return false;
    }

    public boolean tick(double tick) {
        if (step >= 0 && tick <= bufferedUntil && tick + 1e-5 >= start + timing().recovery() * 20)
            return advance(tick, bufferedMove);
        if (tick > bufferedUntil) bufferedUntil = Double.NEGATIVE_INFINITY;
        return false;
    }

    private boolean advance(double tick, int move) {
        step = move == 0 ? (PoemMotionLibrary.combo(step) ? (step + 1) % 4 : 0) : move;
        start = tick; serial++;
        bufferedUntil = Double.NEGATIVE_INFINITY;
        return true;
    }

    public void reset() { mode = step = -1; bufferedUntil = lastRequest = Double.NEGATIVE_INFINITY; }
    public void synchronize(int mode, int step, double start) {
        if (mode < 0 || mode > 3 || !PoemMotionLibrary.validMove(step) || !Double.isFinite(start)) return;
        this.mode = mode; this.step = step; this.start = start;
    }
    public boolean active(double tick) { return step >= 0 && tick < start + timing().duration() * 20 + 3; }
    public PoemMotionLibrary.Timing timing() { return PoemMotionLibrary.get().timing(mode, step); }
    public int mode() { return mode; }
    public int step() { return step; }
    public int serial() { return serial; }
    public double start() { return start; }
}
