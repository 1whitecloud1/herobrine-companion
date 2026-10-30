package com.whitecloud233.herobrine_companion.event;

import net.minecraft.world.item.ItemStack;

public final class HeroContentLimits {
    public static final int MAX_REWARDS = 256;
    public static final int MAX_ITEMS_PER_REWARD = 64;
    public static final int MAX_TRADES = 256;
    public static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    private HeroContentLimits() { }

    public static int rewardId(int value) {
        if (value < 0) throw new IllegalArgumentException("Reward ID must be non-negative");
        return value;
    }
    public static int trust(int value) {
        if (value < 0) throw new IllegalArgumentException("Required trust must be non-negative");
        return value;
    }
    public static ItemStack item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Expected a valid non-empty item");
        if (stack.getCount() > 64) throw new IllegalArgumentException("Reward item count exceeds 64");
        return stack.copy();
    }
    public static ItemStack tradeItem(ItemStack stack) {
        if (stack.getCount() > stack.getMaxStackSize()) throw new IllegalArgumentException("Trade item exceeds its stack size");
        return stack;
    }
    public static void trade(int maxUses, int xp, float multiplier) {
        if (maxUses < 1 || xp < 0 || !Float.isFinite(multiplier) || multiplier < 0) {
            throw new IllegalArgumentException("Trade requires maxUses >= 1, xp >= 0 and a finite non-negative price multiplier");
        }
    }
}
