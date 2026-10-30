package com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope;

import net.minecraft.nbt.CompoundTag;

public final class KitchenActionCooldownChecks {
    private static int checks;

    public static void main(String[] args) {
        for (int interval : new int[]{10, 20}) {
            for (int start = 0; start < 20; start++) {
                for (int cadence : new int[]{1, 2, 3, 7, 20, 23}) {
                    CompoundTag data = new CompoundTag();
                    int actions = 0;
                    long previousAction = -interval;
                    int actualInterval = ((interval + cadence - 1) / cadence) * cadence;
                    for (long tick = start; tick <= start + 200; tick += cadence) {
                        if (acquire(data, "overworld", 1, tick, interval)) {
                            check(tick - previousAction >= interval, "Action ran before its cooldown");
                            previousAction = tick;
                            actions++;
                            check(!acquire(data, "overworld", 1, tick, interval), "Duplicate action in same tick");
                        }
                    }
                    check(actions == 1 + 200 / actualInterval,
                            "Actions stalled: interval=" + interval + " start=" + start + " cadence=" + cadence);
                }
            }
        }

        CompoundTag data = new CompoundTag();
        data.putString("Unrelated", "keep");
        check(acquire(data, "overworld", 1, 101, 20), "First action must run immediately");
        check(!acquire(data, "overworld", 1, 119, 20), "Cooldown expired early");
        check(acquire(data, "overworld", 1, 123, 20), "Missed deadline must run on next update");
        check(acquire(data, "overworld", 1, 1001, 20), "Paused AI must resume immediately");
        check(!acquire(data, "overworld", 1, 1001, 20), "Resume must not burst actions");
        check(acquire(data, "overworld", 2, 1001, 20), "Switching station must reset timing");
        check(acquire(data, "nether", 2, 1001, 20), "Switching dimension must reset timing");
        check(acquire(data, "nether", 2, 1, 20), "Clock rollback must not stall actions");
        check(!acquire(data.copy(), "nether", 2, 2, 20), "Saved cooldown must survive reload");
        check(acquire(new CompoundTag(), "nether", 2, 2, 20), "Different heroes must act independently");
        KitchenActionCooldown.clear(data);
        check(acquire(data, "nether", 2, 2, 20), "New invitation must reset timing");
        check(data.getString("Unrelated").equals("keep"), "Cooldown must preserve other entity data");
        KitchenActionCooldown.clear(data);
        check(data.getAllKeys().size() == 1, "Cleanup must remove cooldown state");
        try {
            acquire(data, "overworld", 1, 1, 0);
            throw new AssertionError("Zero interval accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        System.out.println("KitchenActionCooldownChecks: " + checks + " checks passed");
    }

    private static boolean acquire(CompoundTag data, String dimension, long pos, long tick, int interval) {
        return KitchenActionCooldown.tryAcquire(data, dimension, pos, tick, interval);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
