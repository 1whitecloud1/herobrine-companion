package com.whitecloud233.modid.herobrine_companion.entity.cooking;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record CookOption(ResourceLocation recipeId, ItemStack result, int maxRepeatCount) {
    public CookOption {
        result = result == null ? ItemStack.EMPTY : result.copy();
        maxRepeatCount = CookingRules.clampRepeatCount(maxRepeatCount);
    }

    @Override
    public ItemStack result() { return result.copy(); }
}
