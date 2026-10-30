package com.whitecloud233.herobrine_companion.entity.cooking;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import org.jetbrains.annotations.Nullable;

/**
 * 石磨可选的一个研磨方案：手里有哪种材料、能磨出什么、最多磨几份。
 *
 * <p>和 {@link CookOption} 的区别在于石磨吃的是「玩家背包里的原料」，所以要额外带上
 * 实际可用的输入样例与可磨份数；{@code result} 只是展示用的预期产物。
 */
public record MillstoneOption(ResourceLocation recipeId, ItemStack result, ItemStack inputExample,
                              int maxCount) {
    public MillstoneOption {
        result = result == null ? ItemStack.EMPTY : result.copy();
        inputExample = inputExample == null ? ItemStack.EMPTY : inputExample.copyWithCount(1);
        maxCount = Math.max(0, Math.min(CookingRules.MILLSTONE_MAX_INPUT_COUNT, maxCount));
    }

    @Override
    public ItemStack result() { return result.copy(); }

    @Override
    public ItemStack inputExample() { return inputExample.copy(); }

    public boolean isEmpty() { return maxCount <= 0 || result.isEmpty() || inputExample.isEmpty(); }
}
