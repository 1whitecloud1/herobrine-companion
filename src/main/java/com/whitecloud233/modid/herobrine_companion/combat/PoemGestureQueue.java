package com.whitecloud233.modid.herobrine_companion.combat;

/** One short server-side input buffer; a request never skips action recovery. */
public final class PoemGestureQueue {
    public static final int BUFFER_TICKS = 10;
    private int pending = -1;
    private int slot;
    private int mode;
    private long expiresAt;
    private long readyAt;
    private long lastRequest = Long.MIN_VALUE / 2;

    public boolean offer(int gesture, long tick, int selectedSlot, int selectedMode) {
        if (gesture < 0 || gesture >= 3 || selectedSlot < 0 || selectedSlot >= 9
                || selectedMode < 0 || selectedMode >= 4 || tick - lastRequest < 2) return false;
        lastRequest = tick;
        if (pending >= 0 && tick > expiresAt) cancel();
        if (pending >= 0 || tick + BUFFER_TICKS < readyAt) return false;
        pending = gesture;
        slot = selectedSlot;
        mode = selectedMode;
        expiresAt = tick + BUFFER_TICKS;
        return true;
    }

    public int poll(long tick, int selectedSlot, int selectedMode, boolean canAttack) {
        if (pending < 0) return -1;
        if (tick > expiresAt || slot != selectedSlot || mode != selectedMode) {
            cancel();
            return -1;
        }
        if (!canAttack || tick < readyAt) return -1;
        int result = pending;
        cancel();
        // Guard against two packets arriving before the animator updates state.
        readyAt = tick + 2;
        return result;
    }

    public void started(long tick, int recoveryTicks) {
        readyAt = tick + Math.max(2, recoveryTicks);
    }

    public void cancel() { pending = -1; }
}
