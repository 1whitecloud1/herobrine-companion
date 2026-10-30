package com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope;

import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingRules;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.IngredientMatcher;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;

/** Shared ingredient allocation, repeat limits and inventory views for station planners. */
final class KitchenRecipes {
    private KitchenRecipes() {
    }

    interface RecipeCandidate {
        ResourceLocation recipeId();

        int maxRepeatCount();
    }

    record IngredientPlan(@Nullable ItemStack nextIngredient, int matchedCount, int missingCount) {
    }

    record PotPlan(ResourceLocation recipeId, ItemStack result, IngredientPlan ingredientPlan,
                           int maxRepeatCount) implements RecipeCandidate {
    }

    record StockpotPlan(ResourceLocation recipeId, ItemStack result, @Nullable ItemStack soupBaseStack,
                                IngredientPlan ingredientPlan, int maxRepeatCount) implements RecipeCandidate {
    }

    record TeapotPlan(ResourceLocation recipeId, ItemStack result, @Nullable ItemStack teaFluidStack,
                              @Nullable ItemStack ingredientStack, int maxRepeatCount) implements RecipeCandidate {
    }

    record ChoppingBoardPlan(ResourceLocation recipeId, ItemStack result, @Nullable ItemStack ingredientStack,
                                     int maxRepeatCount) implements RecipeCandidate {
    }

    record SteamerPlan(ResourceLocation recipeId, ItemStack result, @Nullable ItemStack ingredientStack,
                               int maxRepeatCount) implements RecipeCandidate {
    }

    record BackendCookOption(ResourceLocation recipeId, ItemStack result, int maxRepeatCount) implements RecipeCandidate {
    }

    interface StackAction {
        boolean apply(ItemStack stack);
    }

    /** 通用「哪条配方更贴合现状」比较：匹配数多 &gt; 缺失数少 &gt; 可做次数多 &gt; id 字典序小。 */
    static boolean isBetterIngredientChoice(RecipeCandidate candidate, IngredientPlan candidatePlan,
                                                    RecipeCandidate currentBest, IngredientPlan bestPlan) {
        if (candidatePlan.matchedCount() != bestPlan.matchedCount()) {
            return candidatePlan.matchedCount() > bestPlan.matchedCount();
        }
        if (candidatePlan.missingCount() != bestPlan.missingCount()) {
            return candidatePlan.missingCount() < bestPlan.missingCount();
        }
        if (candidate.maxRepeatCount() != currentBest.maxRepeatCount()) {
            return candidate.maxRepeatCount() > currentBest.maxRepeatCount();
        }
        return candidate.recipeId().toString().compareTo(currentBest.recipeId().toString()) < 0;
    }

    static int computeIngredientCraftCount(List<Ingredient> rawIngredients, List<ItemStack> currentInputs,
                                                  List<ItemStack> availableStacks) {
        return IngredientMatcher.computeCraftCount(rawIngredients, currentInputs, availableStacks);
    }

    static boolean consumeIngredientGroup(List<Ingredient> ingredients, List<ItemStack> availableStacks, int[] counts) {
        return IngredientMatcher.consumeIngredientGroup(ingredients, availableStacks, counts);
    }

    static int[] copyStackCounts(List<ItemStack> availableStacks) {
        return IngredientMatcher.copyStackCounts(availableStacks);
    }

    static int countMatchingItems(Ingredient ingredient, List<ItemStack> availableStacks) {
        return IngredientMatcher.countMatchingItems(ingredient, availableStacks);
    }

    static List<Ingredient> normalizeIngredients(List<Ingredient> rawIngredients) {
        return IngredientMatcher.normalize(rawIngredients);
    }

    @Nullable
    static IngredientPlan buildIngredientPlan(List<Ingredient> rawIngredients, List<ItemStack> currentInputs,
                                                      List<ItemStack> availableStacks) {
        IngredientMatcher.IngredientPlanResult plan =
                IngredientMatcher.planIngredient(rawIngredients, currentInputs, availableStacks);
        if (plan == null) {
            return null;
        }
        return new IngredientPlan(plan.nextIngredient(), plan.matchedCount(), plan.missingCount());
    }

    static boolean containsFluid(ItemStack stack, ResourceLocation fluidId) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).map(handler -> {
            net.minecraftforge.fluids.FluidStack contained = handler.getFluidInTank(0);
            if (contained.isEmpty() || contained.getAmount() < 1000) {
                return false;
            }
            ResourceLocation containedFluidId = ForgeRegistries.FLUIDS.getKey(contained.getFluid());
            return fluidId.equals(containedFluidId);
        }).orElse(false);
    }

    static ItemStack findMatchingStack(Ingredient ingredient, int minCount, List<ItemStack> availableStacks) {
        return IngredientMatcher.findMatchingStack(ingredient, minCount, availableStacks);
    }

    static List<ItemStack> collectNonEmptyStacks(List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                result.add(stack);
            }
        }
        return result;
    }

    static List<ItemStack> collectAccessibleStacks(ServerPlayer owner) {
        return IngredientMatcher.collectAccessibleStacks(owner);
    }

    static ItemStack getFirstIngredientExample(Ingredient ingredient) {
        ItemStack[] examples = ingredient.getItems();
        if (examples.length <= 0) {
            return ItemStack.EMPTY;
        }
        return examples[0].copyWithCount(1);
    }

    static boolean tryAccessibleStacks(ServerPlayer owner, StackAction action) {
        for (ItemStack stack : owner.getInventory().items) {
            if (!stack.isEmpty() && action.apply(stack)) {
                owner.getInventory().setChanged();
                return true;
            }
        }
        for (ItemStack stack : owner.getInventory().offhand) {
            if (!stack.isEmpty() && action.apply(stack)) {
                owner.getInventory().setChanged();
                return true;
            }
        }
        return false;
    }
}
