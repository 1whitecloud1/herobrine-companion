package com.whitecloud233.modid.herobrine_companion.combat.poem;

import net.minecraft.world.phys.Vec3;

/** One small horizontal impulse per accepted combo stage, just as its first cut begins. */
public final class PoemAttackStep {
    public static final double MAX_HORIZONTAL_SPEED = .36;
    private int serial = -1;
    private boolean consumed;

    public boolean poll(PoemComboClock clock, double tick) {
        if (clock.step() < 0 || !Double.isFinite(tick)) return false;
        if (serial != clock.serial()) { serial = clock.serial(); consumed = false; }
        if (consumed) return false;
        PoemMotionLibrary.Contact contact = clock.timing().contacts().get(0);
        double age = (tick - clock.start()) / 20;
        if (age < Math.max(0, contact.start() - .05f)) return false;
        consumed = true;
        // Do not deliver a catch-up shove after the cut, or another shove on landing.
        return age <= contact.end();
    }

    public static Vec3 velocity(Vec3 current, float yaw, int step) {
        if (step < 0 || step > 3 || !Float.isFinite(yaw)
                || !Double.isFinite(current.x) || !Double.isFinite(current.y) || !Double.isFinite(current.z)) return current;
        double speed = current.horizontalDistance();
        double impulse = Math.min(.16 + .015 * step, Math.max(0, MAX_HORIZONTAL_SPEED - speed));
        if (impulse <= .00001) return current;
        double radians = Math.toRadians(yaw);
        // Leave gravity, jumps, sideways input and existing fast movement to vanilla.
        return current.add(-Math.sin(radians) * impulse, 0, Math.cos(radians) * impulse);
    }
}
