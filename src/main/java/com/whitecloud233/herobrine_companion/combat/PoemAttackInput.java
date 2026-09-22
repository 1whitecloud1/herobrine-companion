package com.whitecloud233.herobrine_companion.combat;

/** Resolve a physical attack press into one tap, held heavy, or modifier chord. */
public final class PoemAttackInput {
    public static final long HEAVY_HOLD_MILLIS = 350;
    public static final int NONE = -1;
    public static final int HEAVY = 2;
    public static final int LIGHT = 3;
    public static final int CLEAR_ENGINE_INPUT = 1;
    public static final int REPLAY_LIGHT_ATTACK = 2;
    private boolean attackHeld;
    private boolean attackIssued;
    private long pressedAt;
    private int engineControl;
    private boolean ownedEngineInput;
    private boolean useHeld;
    private boolean useConsumed;

    /** A plain press waits for release or the hold threshold; chords fire now. */
    public int attackPressed(int gesture, long now) {
        if (attackHeld) return NONE;
        attackHeld = true;
        pressedAt = now;
        attackIssued = gesture >= 0 && gesture < 3;
        ownedEngineInput = true;
        engineControl = CLEAR_ENGINE_INPUT;
        if (attackIssued) {
            consumeUse();
            return gesture;
        }
        return NONE;
    }

    public int attackHeld(long now) {
        if (!attackHeld || attackIssued || now - pressedAt < HEAVY_HOLD_MILLIS) return NONE;
        attackIssued = true;
        engineControl = CLEAR_ENGINE_INPUT;
        consumeUse();
        return HEAVY;
    }

    /** Also handles a release just past the threshold between client ticks. */
    public int attackReleased(long now) {
        if (!attackHeld) return NONE;
        int result = attackIssued ? NONE : now - pressedAt >= HEAVY_HOLD_MILLIS ? HEAVY : LIGHT;
        if (result == LIGHT) engineControl = CLEAR_ENGINE_INPUT | REPLAY_LIGHT_ATTACK;
        else if (result == HEAVY) {
            engineControl = CLEAR_ENGINE_INPUT;
            consumeUse();
        }
        attackHeld = false;
        attackIssued = false;
        return result;
    }

    /** Replayed through EF's own basic-attack slot and reservation machinery. */
    public int takeEngineControl() {
        int result = engineControl;
        engineControl = 0;
        return result;
    }

    public void usePressed() {
        if (!useHeld) {
            useHeld = true;
            useConsumed = false;
        }
    }

    /** A shared use/modifier tap is replayed only if it never formed a chord. */
    public boolean useReleased() {
        boolean replay = useHeld && !useConsumed;
        useHeld = false;
        useConsumed = false;
        return replay;
    }

    public void consumeUse() { if (useHeld) useConsumed = true; }
    public boolean consumesAttack() { return attackHeld; }
    public boolean hasEngineControl() { return engineControl != 0; }
    public boolean holdsUse() { return useHeld; }

    public void reset() {
        // A context change cancels both a pending tap and EF's reserved replay.
        engineControl = ownedEngineInput ? CLEAR_ENGINE_INPUT : engineControl & CLEAR_ENGINE_INPUT;
        ownedEngineInput = false;
        attackHeld = false;
        attackIssued = false;
        useHeld = false;
        useConsumed = false;
    }
}
