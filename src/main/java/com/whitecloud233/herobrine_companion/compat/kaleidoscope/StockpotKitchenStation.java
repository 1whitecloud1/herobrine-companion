package com.whitecloud233.herobrine_companion.compat.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot;
import com.github.ysbbbbbb.kaleidoscopecookery.api.recipe.soupbase.ISoupBase;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.soupbase.SoupBaseManager;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import com.whitecloud233.herobrine_companion.compat.kaleidoscope.KitchenRecipes.BackendCookOption;
import com.whitecloud233.herobrine_companion.compat.kaleidoscope.KitchenRecipes.IngredientPlan;
import com.whitecloud233.herobrine_companion.compat.kaleidoscope.KitchenRecipes.StockpotPlan;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Plans soup bases and ingredients and executes stockpot interactions. */
final class StockpotKitchenStation {
    private StockpotKitchenStation() {
    }

    static List<BackendCookOption> mapStockpotOptions(List<StockpotPlan> plans) {
        List<BackendCookOption> options = new ArrayList<>(plans.size());
        for (StockpotPlan plan : plans) {
            options.add(new BackendCookOption(plan.recipeId(), plan.result().copy(), plan.maxRepeatCount()));
        }
        return options;
    }

    /**
     * 挑选汤锅配方。候选列表在「放汤底之后」的阶段已按锅内汤底过滤、在 PUT_SOUP_BASE 阶段
     * 必定带一份背包里确实有的汤底，所以列表里每一条都与现状一致、可执行。
     * 玩家选中项漂移失效时不能再直接放弃——放弃等于汤锅永久停摆（与炒锅同一根因），
     * 改为选与锅内现有材料最贴合的那条。
     */
    static int resolveStockpotPlanIndex(HeroEntity hero,
                                                StockpotBlockEntity stockpot,
                                                List<StockpotPlan> plans) {
        if (plans.isEmpty()) {
            return -1;
        }

        int selectedIndex = KitchenSession.resolveSelectedRecipeIndex(hero, stockpot.getBlockPos(), plans);
        if (selectedIndex >= 0) {
            return selectedIndex;
        }

        KitchenDiagnostics.LOGGER.debug("[CookDebug] stockpot 选中项失效，改选最贴合方案 plans={}", plans.size());
        int bestIndex = 0;
        for (int i = 1; i < plans.size(); i++) {
            if (KitchenRecipes.isBetterIngredientChoice(plans.get(i), plans.get(i).ingredientPlan(),
                    plans.get(bestIndex), plans.get(bestIndex).ingredientPlan())) {
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    static boolean tickStockpot(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                        StockpotBlockEntity stockpot) {
        int status = stockpot.getStatus();
        if (status == IStockpot.FINISHED) {
            if (removeStockpotLid(level, owner, stockpot)) {
                return true;
            }
            return tryTakeOutStockpot(hero, level, owner, stockpot);
        }

        List<StockpotPlan> plans = collectStockpotPlans(level, owner, stockpot);
        int selectedIndex = resolveStockpotPlanIndex(hero, stockpot, plans);
        if (KitchenDiagnostics.LOGGER.isDebugEnabled() && level.getGameTime() % 20L == 0L) {
            KitchenDiagnostics.LOGGER.debug("[CookDebug] stockpot status={} inputs={} plans={} sel={}", status,
                    KitchenRecipes.collectNonEmptyStacks(stockpot.getInputs()).size(), plans.size(), selectedIndex);
        }
        if (selectedIndex < 0) {
            return false;
        }

        StockpotPlan plan = plans.get(selectedIndex);
        if (status == IStockpot.PUT_SOUP_BASE) {
            ItemStack soupBaseStack = plan.soupBaseStack();
            if (soupBaseStack != null && !soupBaseStack.isEmpty() && stockpot.addSoupBase(level, hero, soupBaseStack)) {
                owner.getInventory().setChanged();
                return true;
            }
            return false;
        }

        if (status == IStockpot.PUT_INGREDIENT) {
            ItemStack nextIngredient = plan.ingredientPlan().nextIngredient();
            if (nextIngredient != null && !nextIngredient.isEmpty()) {
                if (stockpot.addIngredient(level, hero, nextIngredient)) {
                    owner.getInventory().setChanged();
                    return true;
                }
                return false;
            }

            if (!stockpot.hasLid() && KitchenRecipes.tryAccessibleStacks(owner, stack -> stockpot.onLitClick(level, hero, stack))) {
                return true;
            }
        } else if (status == IStockpot.COOKING) {
            if (!stockpot.hasLid() && KitchenRecipes.tryAccessibleStacks(owner, stack -> stockpot.onLitClick(level, hero, stack))) {
                return true;
            }
        }

        return false;
    }

    static boolean tryTakeOutStockpot(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                              IStockpot stockpot) {
        if (stockpot.takeOutProduct(level, hero, ItemStack.EMPTY)) {
            owner.getInventory().setChanged();
            if (shouldConsumeStockpotRepeat(stockpot) && stockpot instanceof BlockEntity blockEntity) {
                KitchenSession.consumeCookRepeat(hero, blockEntity.getBlockPos());
            }
            return true;
        }
        boolean success = KitchenRecipes.tryAccessibleStacks(owner, stack -> stockpot.takeOutProduct(level, hero, stack));
        if (success && shouldConsumeStockpotRepeat(stockpot) && stockpot instanceof BlockEntity blockEntity) {
            KitchenSession.consumeCookRepeat(hero, blockEntity.getBlockPos());
        }
        return success;
    }

    static boolean shouldConsumeStockpotRepeat(IStockpot stockpot) {
        if (stockpot instanceof StockpotBlockEntity stockpotBlockEntity) {
            return stockpotBlockEntity.getTakeoutCount() <= 0
                    || stockpotBlockEntity.getStatus() != IStockpot.FINISHED;
        }
        return true;
    }

    static boolean removeStockpotLid(ServerLevel level, ServerPlayer owner,
                                             StockpotBlockEntity stockpot) {
        if (!stockpot.hasLid()) {
            return false;
        }

        BlockPos pos = stockpot.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(StockpotBlock.HAS_LID)) {
            return false;
        }

        ItemStack lidItem = stockpot.getLidItem().isEmpty()
                ? ModItems.STOCKPOT_LID.get().getDefaultInstance()
                : stockpot.getLidItem().copy();
        stockpot.setLidItem(ItemStack.EMPTY);
        stockpot.setChanged();
        level.setBlockAndUpdate(pos, state.setValue(
                StockpotBlock.HAS_LID, false));
        if (!owner.getInventory().add(lidItem)) {
            owner.drop(lidItem, false);
        }
        owner.getInventory().setChanged();
        return true;
    }

    static int countSoupBaseItems(ResourceLocation soupBaseId, List<ItemStack> availableStacks) {
        ISoupBase soupBase =
                SoupBaseManager.getSoupBase(soupBaseId);
        if (soupBase == null) {
            return 0;
        }

        int total = 0;
        for (ItemStack stack : availableStacks) {
            if (!stack.isEmpty() && soupBase.isSoupBase(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    static List<StockpotPlan> collectStockpotPlans(ServerLevel level, ServerPlayer owner,
                                                           StockpotBlockEntity stockpot) {
        List<ItemStack> currentInputs = KitchenRecipes.collectNonEmptyStacks(stockpot.getInputs());
        List<ItemStack> availableStacks = KitchenRecipes.collectAccessibleStacks(owner);
        ResourceLocation selectedSoupBase = stockpot.getStatus() == IStockpot.PUT_SOUP_BASE
                ? null
                : stockpot.getSoupBaseId();
        List<StockpotPlan> plans = new ArrayList<>();

        for (RecipeHolder<StockpotRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.STOCKPOT_RECIPE)) {
            StockpotRecipe recipe = holder.value();
            if (selectedSoupBase != null && !recipe.soupBase().equals(selectedSoupBase)) {
                continue;
            }

            ItemStack soupBaseStack = ItemStack.EMPTY;
            if (selectedSoupBase == null) {
                soupBaseStack = findSoupBaseStack(recipe.soupBase(), availableStacks);
                if (soupBaseStack.isEmpty()) {
                    continue;
                }
            }

            IngredientPlan ingredientPlan = KitchenRecipes.buildIngredientPlan(recipe.ingredients(), currentInputs, availableStacks);
            int ingredientRepeatCount = KitchenRecipes.computeIngredientCraftCount(recipe.ingredients(), currentInputs, availableStacks);
            int soupBaseRepeatLimit = selectedSoupBase == null
                    ? countSoupBaseItems(recipe.soupBase(), availableStacks)
                    : countSoupBaseItems(recipe.soupBase(), availableStacks) + 1;
            int maxRepeatCount = Math.min(ingredientRepeatCount, soupBaseRepeatLimit);
            if (ingredientPlan != null && maxRepeatCount > 0) {
                plans.add(new StockpotPlan(holder.id(), recipe.getResultItem(level.registryAccess()).copy(),
                        soupBaseStack.isEmpty() ? null : soupBaseStack, ingredientPlan, maxRepeatCount));
            }
        }

        plans.sort(Comparator.comparing(plan -> plan.recipeId().toString()));
        return plans;
    }

    static ItemStack findSoupBaseStack(ResourceLocation soupBaseId, List<ItemStack> availableStacks) {
        ISoupBase soupBase =
                SoupBaseManager.getSoupBase(soupBaseId);
        if (soupBase == null) {
            return ItemStack.EMPTY;
        }

        for (ItemStack stack : availableStacks) {
            if (!stack.isEmpty() && soupBase.isSoupBase(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
