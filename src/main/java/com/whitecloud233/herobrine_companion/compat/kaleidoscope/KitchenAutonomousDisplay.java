package com.whitecloud233.herobrine_companion.compat.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IPot;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Captures, applies and restores temporary cookware visuals for autonomous cooking. */
final class KitchenAutonomousDisplay {
    private KitchenAutonomousDisplay() {
    }

    static CompoundTag captureAutonomousCookwareSnapshot(Level level, BlockPos pos) {
        CompoundTag snapshot = new CompoundTag();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return snapshot;
        }

        snapshot.put("BlockEntityData", blockEntity.saveWithoutMetadata(level.registryAccess()));
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(StockpotBlock.HAS_LID)) {
            snapshot.putBoolean("StockpotHasLid", state.getValue(StockpotBlock.HAS_LID));
        }
        if (state.hasProperty(SteamerBlock.HAS_LID)) {
            snapshot.putBoolean("SteamerHasLid", state.getValue(SteamerBlock.HAS_LID));
        }
        return snapshot;
    }

    static boolean applyAutonomousCookwareDisplay(Level level, BlockPos pos, ResourceLocation recipeId, ItemStack result) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        if (blockEntity instanceof PotBlockEntity pot) {
            return applyPotAutonomousDisplay(serverLevel, pot, result);
        }
        if (blockEntity instanceof StockpotBlockEntity stockpot) {
            return applyStockpotAutonomousDisplay(serverLevel, stockpot, recipeId, result);
        }
        if (blockEntity instanceof ChoppingBoardBlockEntity choppingBoard) {
            return applyChoppingBoardAutonomousDisplay(serverLevel, choppingBoard, recipeId);
        }
        if (blockEntity instanceof SteamerBlockEntity steamer) {
            return applySteamerAutonomousDisplay(serverLevel, steamer, result);
        }
        if (TeapotKitchenStation.isTeapotBlockEntity(blockEntity)) {
            return TeapotKitchenStation.applyAutonomousDisplay(serverLevel, blockEntity, recipeId, result);
        }
        return false;
    }

    static void restoreAutonomousCookwareSnapshot(Level level, BlockPos pos, CompoundTag snapshot) {
        if (snapshot.isEmpty()) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (snapshot.contains("StockpotHasLid") && state.hasProperty(StockpotBlock.HAS_LID)) {
            level.setBlock(pos, state.setValue(
                    StockpotBlock.HAS_LID,
                    snapshot.getBoolean("StockpotHasLid")), 3);
            state = level.getBlockState(pos);
        }
        if (snapshot.contains("SteamerHasLid") && state.hasProperty(SteamerBlock.HAS_LID)) {
            level.setBlock(pos, state.setValue(
                    SteamerBlock.HAS_LID,
                    snapshot.getBoolean("SteamerHasLid")), 3);
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !snapshot.contains("BlockEntityData")) {
            return;
        }

        blockEntity.loadWithComponents(snapshot.getCompound("BlockEntityData"), level.registryAccess());
        blockEntity.setChanged();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }

    static boolean applyPotAutonomousDisplay(ServerLevel level,
                                                     PotBlockEntity pot,
                                                     ItemStack result) {
        boolean updated = KitchenReflection.setFieldValue(pot, "result", result.copy())
                | KitchenReflection.setFieldValue(pot, "status", IPot.FINISHED)
                | KitchenReflection.setFieldValue(pot, "currentTick", 0);
        return markCookwareUpdated(level, pot.getBlockPos(), pot, updated);
    }

    static boolean applyStockpotAutonomousDisplay(ServerLevel level,
                                                          StockpotBlockEntity stockpot,
                                                          ResourceLocation recipeId, ItemStack result) {
        RecipeHolder<StockpotRecipe> recipeHolder = findStockpotRecipe(level, recipeId);
        boolean updated = false;
        updated |= KitchenReflection.setFieldValue(stockpot, "recipeId", recipeId);
        if (recipeHolder != null) {
            // cookery 1.3.x 有公开字段 recipe，1.4.x 移除（改为按 recipeId 解析），反射写入以同时兼容两版
            updated |= KitchenReflection.setFieldValue(stockpot, "recipe", recipeHolder);
            updated |= KitchenReflection.setFieldValue(stockpot, "soupBaseId", recipeHolder.value().soupBase());
            Object visuals = KitchenReflection.invokeGetter(recipeHolder.value(), "visuals");
            if (visuals != null) {
                updated |= KitchenReflection.setFieldValue(stockpot, "visuals", visuals);
            }
        }
        updated |= KitchenReflection.setFieldValue(stockpot, "result", result.copy());
        updated |= KitchenReflection.setFieldValue(stockpot, "status", IStockpot.FINISHED);
        updated |= KitchenReflection.setFieldValue(stockpot, "currentTick", 0);
        updated |= KitchenReflection.setFieldValue(stockpot, "takeoutCount", Math.max(1, result.getCount()));
        updated |= KitchenReflection.setFieldValue(stockpot, "lidItem", ItemStack.EMPTY);

        BlockState state = level.getBlockState(stockpot.getBlockPos());
        if (state.hasProperty(StockpotBlock.HAS_LID)
                && state.getValue(StockpotBlock.HAS_LID)) {
            level.setBlock(stockpot.getBlockPos(), state.setValue(
                    StockpotBlock.HAS_LID, false), 3);
            updated = true;
        }
        return markCookwareUpdated(level, stockpot.getBlockPos(), stockpot, updated);
    }

    static boolean applyChoppingBoardAutonomousDisplay(ServerLevel level,
                                                               ChoppingBoardBlockEntity choppingBoard,
                                                               ResourceLocation recipeId) {
        ChoppingBoardRecipe recipe = ChoppingBoardKitchenStation.findChoppingBoardRecipe(level, recipeId);
        if (recipe == null) {
            return false;
        }

        ItemStack displayStack = KitchenRecipes.getFirstIngredientExample(recipe.getIngredient());
        boolean updated = KitchenReflection.setFieldValue(choppingBoard, "modelId", recipe.getModelId())
                | KitchenReflection.setFieldValue(choppingBoard, "maxCutCount", recipe.getCutCount())
                | KitchenReflection.setFieldValue(choppingBoard, "currentCutCount", Math.max(0, recipe.getCutCount() - 1))
                | KitchenReflection.setFieldValue(choppingBoard, "currentCutStack", displayStack);
        // 1.6.0 起该字段由 result(ItemStack) 改成 results(List<ItemStack>)：
        // 先试旧名，字段不存在（返回 false）时再试新名，两个版本都能正确回填展示。
        updated |= KitchenReflection.setFieldValue(choppingBoard, "result", recipe.getResult().copy())
                || KitchenReflection.setFieldValue(choppingBoard, "results", ChoppingBoardKitchenStation.resolveChoppingBoardResults(recipe));
        return markCookwareUpdated(level, choppingBoard.getBlockPos(), choppingBoard, updated);
    }

    static boolean applySteamerAutonomousDisplay(ServerLevel level,
                                                         SteamerBlockEntity steamer,
                                                         ItemStack result) {
        boolean updated = false;
        List<ItemStack> items = steamer.getItems();
        for (int i = 0; i < items.size(); i++) {
            ItemStack next = i == 0 ? result.copyWithCount(1) : ItemStack.EMPTY;
            if (!ItemStack.matches(items.get(i), next)) {
                items.set(i, next);
                updated = true;
            }
        }

        int[] cookingProgress = steamer.getCookingProgress();
        int[] cookingTime = steamer.getCookingTime();
        if (cookingProgress.length > 0 && cookingProgress[0] != 0) {
            cookingProgress[0] = 0;
            updated = true;
        }
        if (cookingTime.length > 0 && cookingTime[0] != -1) {
            cookingTime[0] = -1;
            updated = true;
        }
        for (int i = 1; i < cookingProgress.length; i++) {
            if (cookingProgress[i] != 0) {
                cookingProgress[i] = 0;
                updated = true;
            }
        }
        for (int i = 1; i < cookingTime.length; i++) {
            if (cookingTime[i] != 0) {
                cookingTime[i] = 0;
                updated = true;
            }
        }

        return markCookwareUpdated(level, steamer.getBlockPos(), steamer, updated);
    }

    @Nullable
    static RecipeHolder<StockpotRecipe> findStockpotRecipe(ServerLevel level,
                                                                                                              ResourceLocation recipeId) {
        for (RecipeHolder<StockpotRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.STOCKPOT_RECIPE)) {
            if (holder.id().equals(recipeId)) {
                return holder;
            }
        }
        return null;
    }

    static boolean markCookwareUpdated(Level level, BlockPos pos, BlockEntity blockEntity, boolean updated) {
        if (!updated) {
            return false;
        }
        blockEntity.setChanged();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
        return true;
    }
}
