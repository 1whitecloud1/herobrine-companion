package com.whitecloud233.modid.herobrine_companion.combat.poem;

/** The same authored timing drives Hero's native pose, impact and uninterrupted follow-up. */
public final class PoemHeroCombo {
    private static final int[] GROUND_MOVES = {0, 1, 2, 3, 6, 8, 10};
    private static final int[] AIR_MOVES = {5, 7, 9, 11};
    private PoemHeroCombo() { }
    public static int step(int serial) { return Math.floorMod(serial - 1, 4); }
    public static int selectMove(int serial, boolean airborne, boolean dash) {
        if (airborne) return AIR_MOVES[Math.floorMod(serial - 1, AIR_MOVES.length)];
        if (dash) return PoemMotionLibrary.DASH;
        return GROUND_MOVES[Math.floorMod(serial - 1, GROUND_MOVES.length)];
    }
    public static int resolveMove(int move, int serial) {
        return PoemMotionLibrary.validMove(move) ? move : step(serial);
    }
    public static int durationForMove(int mode, int move) {
        return ticks(PoemMotionLibrary.get().timing(mode, move).duration());
    }
    public static int contactTick(PoemMotionLibrary.Contact contact) {
        return Math.max(1, ticks((contact.start() + contact.end()) / 2));
    }
    public static int chainForMove(int mode, int move) {
        var timing = PoemMotionLibrary.get().timing(mode, move);
        return Math.max(contactTick(timing.contacts().get(timing.contacts().size() - 1)) + 1,
                ticks(timing.recovery()));
    }
    public static PoemMotionLibrary.Timing timing(int mode, int serial) {
        return PoemMotionLibrary.get().timing(mode, step(serial));
    }
    public static int durationTicks(int mode, int serial) { return ticks(timing(mode, serial).duration()); }
    public static int hitTick(int mode, int serial) {
        var contact = timing(mode, serial).contacts().get(0);
        return Math.max(1, ticks((contact.start() + contact.end()) / 2));
    }
    public static int chainTick(int mode, int serial) {
        return Math.max(hitTick(mode, serial) + 1, ticks(timing(mode, serial).recovery()));
    }
    private static int ticks(float seconds) { return (int) Math.ceil(seconds * 20.0 - .00001); }
}
