package com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ISteamer;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.SteamerRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope.KitchenRecipes.BackendCookOption;
import com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope.KitchenRecipes.SteamerPlan;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Plans and executes steamer interactions. */
final class SteamerKitchenStation {
    private SteamerKitchenStation() {
    }

    static List<BackendCookOption> mapSteamerOptions(List<SteamerPlan> plans) {
        List<BackendCookOption> options = new ArrayList<>(plans.size());
        for (SteamerPlan plan : plans) {
            options.add(new BackendCookOption(plan.recipeId(), plan.result().copy(), plan.maxRepeatCount()));
        }
        return options;
    }

    static boolean tickSteamer(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                       ISteamer steamer) {
        if (steamer instanceof SteamerBlockEntity steamerBlockEntity
                && hasReadySteamerOutput(steamerBlockEntity)) {
            if (steamer.takeFood(level, hero)) {
                owner.getInventory().setChanged();
                KitchenSession.consumeCookRepeat(hero, steamerBlockEntity.getBlockPos());
                return true;
            }
            return false;
        }

        List<SteamerPlan> plans = collectSteamerPlans(level, owner, steamer);
        BlockPos blockPos = steamer instanceof BlockEntity be ? be.getBlockPos() : hero.blockPosition();
        int selectedIndex = KitchenSession.resolveSelectedRecipeIndex(hero, blockPos, plans);
        if (selectedIndex < 0) {
            if (plans.isEmpty()) {
                return false;
            }
            // 选中项漂移：候选已按「背包里确实有可蒸食材」过滤，取首条继续，不要永久放弃。
            KitchenDiagnostics.LOGGER.debug("[CookDebug] steamer 选中项失效，改用首条方案 plans={}", plans.size());
            selectedIndex = 0;
        }

        SteamerPlan plan = plans.get(selectedIndex);
        ItemStack ingredientStack = plan.ingredientStack();
        if (ingredientStack != null && !ingredientStack.isEmpty() && steamer.placeFood(level, hero, ingredientStack)) {
            owner.getInventory().setChanged();
            if (steamer instanceof SteamerBlockEntity steamerBlockEntity) {
                setSteamerLid(level, steamerBlockEntity, true);
            }
            return true;
        }
        return false;
    }

    static boolean hasReadySteamerOutput(SteamerBlockEntity steamer) {
        List<ItemStack> items = steamer.getItems();
        int[] cookingTime = steamer.getCookingTime();
        for (int i = 0; i < items.size() && i < cookingTime.length; i++) {
            if (!items.get(i).isEmpty() && cookingTime[i] == -1) {
                return true;
            }
        }
        return false;
    }

    static void setSteamerLid(ServerLevel level,
                                      SteamerBlockEntity steamer,
                                      boolean hasLid) {
        BlockPos pos = steamer.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(SteamerBlock.HAS_LID)) {
            return;
        }
        if (state.getValue(SteamerBlock.HAS_LID) == hasLid) {
            return;
        }
        level.setBlock(pos, state.setValue(
                SteamerBlock.HAS_LID, hasLid), 3);
    }

    static List<SteamerPlan> collectSteamerPlans(ServerLevel level, ServerPlayer owner,
                                                         ISteamer steamer) {
        List<ItemStack> availableStacks = KitchenRecipes.collectAccessibleStacks(owner);
        List<SteamerPlan> plans = new ArrayList<>();

        for (SteamerRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.STEAMER_RECIPE)) {
            ItemStack ingredientStack = KitchenRecipes.findMatchingStack(recipe.getIngredient(), 1, availableStacks);
            int maxRepeatCount = Math.min(CookingRules.MAX_COOK_REPEAT_COUNT, KitchenRecipes.countMatchingItems(recipe.getIngredient(), availableStacks));
            if (!ingredientStack.isEmpty() && maxRepeatCount > 0) {
                plans.add(new SteamerPlan(recipe.getId(), recipe.getResult().copy(), ingredientStack, maxRepeatCount));
            }
        }

        plans.sort(Comparator.comparing(plan -> plan.recipeId().toString()));
        return plans;
    }
}
