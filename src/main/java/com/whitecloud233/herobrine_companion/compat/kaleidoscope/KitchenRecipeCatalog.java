package com.whitecloud233.herobrine_companion.compat.kaleidoscope;

import com.whitecloud233.herobrine_companion.entity.cooking.IngredientMatcher;

import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ISteamer;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.PotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.SteamerRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import com.whitecloud233.herobrine_companion.compat.kaleidoscope.HeroKaleidoscopeCompat.AutonomousCookOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Enumerates autonomous recipe choices and deduplicates their preview results. */
final class KitchenRecipeCatalog {
    private KitchenRecipeCatalog() {
    }

    static List<ItemStack> getAutonomousCookResults(Level level, BlockPos pos) {
        List<ItemStack> results = new ArrayList<>();
        for (AutonomousCookOption option : getAutonomousCookOptions(level, pos)) {
            addUniqueResult(results, option.result().copy());
        }
        return results;
    }

    static List<AutonomousCookOption> getAutonomousCookOptions(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return List.of();
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<AutonomousCookOption> results = new ArrayList<>();

        if (blockEntity instanceof PotBlockEntity) {
            for (RecipeHolder<PotRecipe> holder : serverLevel.getRecipeManager().getAllRecipesFor(ModRecipes.POT_RECIPE)) {
                PotRecipe recipe = holder.value();
                addAutonomousOption(results, holder.id(), recipe.getResultItem(serverLevel.registryAccess()).copy());
            }
            return results;
        }
        if (blockEntity instanceof StockpotBlockEntity) {
            for (RecipeHolder<StockpotRecipe> holder : serverLevel.getRecipeManager().getAllRecipesFor(ModRecipes.STOCKPOT_RECIPE)) {
                StockpotRecipe recipe = holder.value();
                addAutonomousOption(results, holder.id(), recipe.getResultItem(serverLevel.registryAccess()).copy());
            }
            return results;
        }
        if (blockEntity instanceof ChoppingBoardBlockEntity) {
            for (RecipeHolder<ChoppingBoardRecipe> holder : serverLevel.getRecipeManager().getAllRecipesFor(ModRecipes.CHOPPING_BOARD_RECIPE)) {
                ChoppingBoardRecipe recipe = holder.value();
                addAutonomousOption(results, holder.id(), recipe.getResult().copy());
            }
            return results;
        }
        if (blockEntity instanceof ISteamer) {
            for (RecipeHolder<SteamerRecipe> holder : serverLevel.getRecipeManager().getAllRecipesFor(ModRecipes.STEAMER_RECIPE)) {
                SteamerRecipe recipe = holder.value();
                addAutonomousOption(results, holder.id(), recipe.getResult().copy());
            }
            return results;
        }
        if (TeapotKitchenStation.isTeapotBlockEntity(blockEntity)) {
            return TeapotKitchenStation.getAutonomousCookOptions(serverLevel);
        }

        return List.of();
    }

    static void addAutonomousOption(List<AutonomousCookOption> results, ResourceLocation recipeId, ItemStack candidate) {
        if (recipeId == null || candidate.isEmpty()) {
            return;
        }
        results.add(new AutonomousCookOption(recipeId, candidate.copy()));
    }

    static void addUniqueResult(List<ItemStack> results, ItemStack candidate) {
        IngredientMatcher.addUniqueResult(results, candidate);
    }
}
