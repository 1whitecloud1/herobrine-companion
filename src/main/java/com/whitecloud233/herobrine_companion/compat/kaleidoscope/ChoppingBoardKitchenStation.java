package com.whitecloud233.herobrine_companion.compat.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import com.github.ysbbbbbb.kaleidoscopecookery.init.tag.TagMod;
import com.whitecloud233.herobrine_companion.compat.kaleidoscope.KitchenRecipes.BackendCookOption;
import com.whitecloud233.herobrine_companion.compat.kaleidoscope.KitchenRecipes.ChoppingBoardPlan;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.cooking.CookingRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Plans chopping recipes, advances cuts and delivers freshly produced drops. */
final class ChoppingBoardKitchenStation {
    private ChoppingBoardKitchenStation() {
    }

    static List<BackendCookOption> mapChoppingBoardOptions(List<ChoppingBoardPlan> plans) {
        List<BackendCookOption> options = new ArrayList<>(plans.size());
        for (ChoppingBoardPlan plan : plans) {
            options.add(new BackendCookOption(plan.recipeId(), plan.result().copy(), plan.maxRepeatCount()));
        }
        return options;
    }

    static boolean tickChoppingBoard(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                             ChoppingBoardBlockEntity choppingBoard) {
        List<ChoppingBoardPlan> plans = collectChoppingBoardPlans(level, owner, choppingBoard);
        int selectedIndex = resolveChoppingBoardPlanIndex(hero, choppingBoard, plans);
        KitchenDiagnostics.cookDiag("board.state", "plans=" + plans.size() + " sel=" + selectedIndex
                + " cutEmpty=" + choppingBoard.getCurrentCutStack().isEmpty()
                + " cut=" + choppingBoard.getCurrentCutCount() + "/" + choppingBoard.getMaxCutCount()
                + " knifeOk=" + getVirtualKitchenKnife().is(TagMod.KITCHEN_KNIFE)
                + " selRecipe=" + KitchenSession.getCookSelectionRecipeId(hero, choppingBoard.getBlockPos()));
        if (KitchenDiagnostics.LOGGER.isDebugEnabled() && level.getGameTime() % 20L == 0L) {
            KitchenDiagnostics.LOGGER.debug("[CookDebug] board plans={} sel={} cutStack空={} cut={}/{}",
                    plans.size(), selectedIndex, choppingBoard.getCurrentCutStack().isEmpty(),
                    choppingBoard.getCurrentCutCount(), choppingBoard.getMaxCutCount());
        }
        // 板上有料 → 立刻开切。这一步必须排在「找方案」之前：
        // 旧实现先要求 selectedIndex >= 0，而 collectChoppingBoardPlans 在板上有料时用
        // 「modelId 非空 + cutCount 相等 + modelId 相等 + ingredient 匹配」四重条件过滤，
        // 只要有一条对不上（板上的料是玩家自己放上去的、modelId 是旧版本存下来的…）候选就是空的，
        // 于是 selectedIndex < 0 → 整个函数直接 return false，
        // Hero 举着刀对着放了料的菜板一刀都不切 —— 就是玩家看到的「切菜板不会切菜」。
        if (!choppingBoard.getCurrentCutStack().isEmpty()) {
            ChoppingBoardPlan currentPlan = selectedIndex >= 0 ? plans.get(selectedIndex) : null;
            if (currentPlan == null) {
                currentPlan = resolveChoppingBoardPlanForCurrent(level, choppingBoard);
            }
            return cutChoppingBoard(hero, level, owner, choppingBoard, currentPlan);
        }

        if (selectedIndex < 0) {
            return false;
        }

        ChoppingBoardPlan plan = plans.get(selectedIndex);
        ItemStack ingredientStack = plan.ingredientStack();
        if (ingredientStack != null && !ingredientStack.isEmpty() && choppingBoard.onPutItem(level, hero, ingredientStack)) {
            KitchenDiagnostics.cookDiag("board.put", "OK " + ingredientStack.getItem());
            owner.getInventory().setChanged();
            return true;
        }
        KitchenDiagnostics.cookDiag("board.put", "FAIL " + (ingredientStack == null ? "null" : ingredientStack.getItem()));
        KitchenDiagnostics.LOGGER.debug("[CookDebug] board onPutItem 失败: {}", ingredientStack);
        return false;
    }

    /**
     * 直接用菜板当前的材料反查配方。模组自己的 {@code onPutItem} 也是这么干的
     * （{@code getRecipeFor(CHOPPING_BOARD_RECIPE, [板上的料])}），所以这里一定能拿到与
     * ModelId 一致的同一条配方，产物列表也才是对的。
     */
    @Nullable
    static ChoppingBoardPlan resolveChoppingBoardPlanForCurrent(
            ServerLevel level,
            ChoppingBoardBlockEntity choppingBoard) {
        ItemStack cutStack = choppingBoard.getCurrentCutStack();
        if (cutStack.isEmpty()) {
            return null;
        }
        SingleRecipeInput container =
                new SingleRecipeInput(cutStack.copyWithCount(1));
        return level.getRecipeManager()
                .getRecipeFor(ModRecipes.CHOPPING_BOARD_RECIPE, container, level)
                .map(holder -> new ChoppingBoardPlan(holder.id(), holder.value().getResult().copy(), null, 1))
                .orElse(null);
    }

    /** 切菜：板上已有材料，10 tick 切一刀；切满最后一刀时模组会自己把产物吐出来。 */
    static boolean cutChoppingBoard(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                            ChoppingBoardBlockEntity choppingBoard,
                                            @Nullable ChoppingBoardPlan plan) {
        if (!KitchenActionCooldown.tryCookAction(hero, level, choppingBoard.getBlockPos(), 10)) {
            return false;
        }

        boolean completesRecipe = choppingBoard.getCurrentCutCount() >= choppingBoard.getMaxCutCount();
        // 菜板配方从 1.6.0 起由单产物改成多产物（如「生小块肉」会同时掉出肉 + 骨头），
        // 只按首个产物收货会把第二种产物留在地上。这里一次性取全部产物。
        List<ItemStack> expectedResults = completesRecipe && plan != null
                ? resolveChoppingBoardResults(level, plan.recipeId())
                : List.of();
        Set<UUID> existingDropIds = completesRecipe
                ? snapshotChoppingBoardDropIds(level, choppingBoard.getBlockPos(), expectedResults)
                : Set.of();
        if (choppingBoard.onCutItem(level, hero, getVirtualKitchenKnife())) {
            KitchenDiagnostics.cookDiag("board.cut", "OK cut=" + choppingBoard.getCurrentCutCount() + "/" + choppingBoard.getMaxCutCount());
            if (completesRecipe) {
                deliverChoppingBoardResult(level, owner, choppingBoard.getBlockPos(), expectedResults, existingDropIds);
                KitchenSession.consumeCookRepeat(hero, choppingBoard.getBlockPos());
            }
            return true;
        }

        KitchenDiagnostics.cookDiag("board.cut", "FAIL cut=" + choppingBoard.getCurrentCutCount() + "/" + choppingBoard.getMaxCutCount()
                + " knifeOk=" + getVirtualKitchenKnife()
                        .is(TagMod.KITCHEN_KNIFE));
        KitchenDiagnostics.LOGGER.debug("[CookDebug] board onCutItem 失败 cut={}/{}", choppingBoard.getCurrentCutCount(),
                choppingBoard.getMaxCutCount());
        return false;
    }

    /**
     * 挑选菜板配方。与炒锅同理：候选列表每 tick 重算，玩家选中项可能漂移消失。
     * 菜板上的候选已经按「板上材料 / 板面模型 / 刀数」过滤过，因此只要列表非空，
     * 取第一条一定与板上现状一致；直接放弃会让 Hero 永远不上料、不切菜。
     */
    static int resolveChoppingBoardPlanIndex(HeroEntity hero,
                                                     ChoppingBoardBlockEntity choppingBoard,
                                                     List<ChoppingBoardPlan> plans) {
        if (plans.isEmpty()) {
            return -1;
        }

        int selectedIndex = KitchenSession.resolveSelectedRecipeIndex(hero, choppingBoard.getBlockPos(), plans);
        return selectedIndex < 0 ? 0 : selectedIndex;
    }

    static ItemStack getVirtualKitchenKnife() {
        return ModItems.IRON_KITCHEN_KNIFE.get().getDefaultInstance();
    }

    /**
     * 取菜板配方的全部产物。1.6.0 起 {@code ChoppingBoardRecipe} 由单 {@code result} 改为
     * {@code List<ItemStack> results}（例如「生小块肉」= 肉 + 骨头），而仓库编译依赖是 1.4.1，
     * 只有 {@code getResult()}。因此优先反射取 {@code getResults()}，取不到再退回单产物，
     * 两个版本都能拿到完整产物列表。
     */
    static List<ItemStack> resolveChoppingBoardResults(ServerLevel level, ResourceLocation recipeId) {
        ChoppingBoardRecipe recipe =
                findChoppingBoardRecipe(level, recipeId);
        return recipe == null ? List.of() : resolveChoppingBoardResults(recipe);
    }

    static Set<UUID> snapshotChoppingBoardDropIds(ServerLevel level, BlockPos pos, List<ItemStack> expectedResults) {
        Set<UUID> dropIds = new HashSet<>();
        if (expectedResults.isEmpty()) {
            return dropIds;
        }

        for (ItemEntity itemEntity : getChoppingBoardResultEntities(level, pos, expectedResults, 1.0D)) {
            dropIds.add(itemEntity.getUUID());
        }
        return dropIds;
    }

    static void deliverChoppingBoardResult(ServerLevel level, ServerPlayer owner, BlockPos pos,
                                                   List<ItemStack> expectedResults, Set<UUID> existingDropIds) {
        if (expectedResults.isEmpty()) {
            return;
        }

        Vec3 boardCenter = Vec3.atCenterOf(pos);
        boolean changed = false;
        // 逐个产物结算：多产物配方里每种产物各自有独立堆叠数，合并处理会算错数量。
        for (ItemStack expectedResult : expectedResults) {
            if (expectedResult.isEmpty()) {
                continue;
            }

            int remaining = expectedResult.getCount();
            List<ItemEntity> newDrops = new ArrayList<>();
            for (ItemEntity itemEntity : getChoppingBoardResultEntities(level, pos, List.of(expectedResult), 1.0D)) {
                if (!existingDropIds.contains(itemEntity.getUUID())) {
                    newDrops.add(itemEntity);
                }
            }
            newDrops.sort(Comparator.comparingDouble(itemEntity -> itemEntity.distanceToSqr(boardCenter)));

            for (ItemEntity itemEntity : newDrops) {
                if (remaining <= 0) {
                    break;
                }

                ItemStack stack = itemEntity.getItem();
                int movedCount = Math.min(remaining, stack.getCount());
                ItemHandlerHelper.giveItemToPlayer(owner, stack.copyWithCount(movedCount));
                remaining -= movedCount;
                changed = true;
                if (movedCount >= stack.getCount()) {
                    itemEntity.discard();
                } else {
                    stack.shrink(movedCount);
                    itemEntity.setItem(stack);
                }
            }

            if (remaining > 0) {
                // 产物没被模组弹出（或弹出后被合并/拾取）时补交，保证 Hero 一定拿齐这一份。
                ItemHandlerHelper.giveItemToPlayer(owner, expectedResult.copyWithCount(remaining));
                changed = true;
            }
        }

        if (changed) {
            owner.getInventory().setChanged();
        }
    }

    static List<ItemEntity> getChoppingBoardResultEntities(ServerLevel level, BlockPos pos,
                                                                   List<ItemStack> expectedResults, double radius) {
        AABB searchBox = new AABB(pos).inflate(radius);
        return level.getEntitiesOfClass(ItemEntity.class, searchBox, itemEntity -> {
            if (itemEntity.tickCount > 2) {
                return false;
            }
            for (ItemStack expectedResult : expectedResults) {
                if (ItemStack.isSameItemSameComponents(itemEntity.getItem(), expectedResult)) {
                    return true;
                }
            }
            return false;
        });
    }

    static List<ChoppingBoardPlan> collectChoppingBoardPlans(ServerLevel level, ServerPlayer owner,
                                                                     ChoppingBoardBlockEntity choppingBoard) {
        List<ItemStack> availableStacks = KitchenRecipes.collectAccessibleStacks(owner);
        List<ChoppingBoardPlan> plans = new ArrayList<>();
        ItemStack currentCutStack = choppingBoard.getCurrentCutStack();
        boolean hasCurrentIngredient = !currentCutStack.isEmpty();
        ResourceLocation currentModelId = choppingBoard.getModelId();
        int currentMaxCutCount = choppingBoard.getMaxCutCount();

        for (RecipeHolder<ChoppingBoardRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.CHOPPING_BOARD_RECIPE)) {
            ChoppingBoardRecipe recipe = holder.value();
            int maxRepeatCount;
            ItemStack ingredientStack = ItemStack.EMPTY;
            if (hasCurrentIngredient) {
                if (currentModelId == null
                        || currentMaxCutCount != recipe.getCutCount()
                        || !recipe.getModelId().equals(currentModelId)
                        || !recipe.getIngredient().test(currentCutStack)) {
                    continue;
                }
                maxRepeatCount = Math.min(CookingRules.MAX_COOK_REPEAT_COUNT, KitchenRecipes.countMatchingItems(recipe.getIngredient(), availableStacks) + 1);
            } else {
                ingredientStack = KitchenRecipes.findMatchingStack(recipe.getIngredient(), 1, availableStacks);
                if (ingredientStack.isEmpty() || !isPlaceableChoppingBoardRecipe(level, ingredientStack, holder.id())) {
                    continue;
                }
                maxRepeatCount = Math.min(CookingRules.MAX_COOK_REPEAT_COUNT, KitchenRecipes.countMatchingItems(recipe.getIngredient(), availableStacks));
            }

            if (maxRepeatCount <= 0) {
                continue;
            }

            plans.add(new ChoppingBoardPlan(holder.id(), recipe.getResult().copy(),
                    ingredientStack.isEmpty() ? null : ingredientStack, maxRepeatCount));
        }

        plans.sort(Comparator.comparing(plan -> plan.recipeId().toString()));
        return plans;
    }

    static boolean isPlaceableChoppingBoardRecipe(ServerLevel level, ItemStack ingredientStack, ResourceLocation recipeId) {
        SingleRecipeInput container = new SingleRecipeInput(ingredientStack.copyWithCount(1));
        return level.getRecipeManager()
                .getRecipeFor(ModRecipes.CHOPPING_BOARD_RECIPE, container, level)
                .map(holder -> holder.id().equals(recipeId))
                .orElse(false);
    }

    @Nullable
    static ChoppingBoardRecipe findChoppingBoardRecipe(ServerLevel level,
                                                                                                                        ResourceLocation recipeId) {
        for (RecipeHolder<ChoppingBoardRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.CHOPPING_BOARD_RECIPE)) {
            if (holder.id().equals(recipeId)) {
                return holder.value();
            }
        }
        return null;
    }

    static List<ItemStack> resolveChoppingBoardResults(
            ChoppingBoardRecipe recipe) {
        Object rawResults = KitchenReflection.invokeGetter(recipe, "getResults");
        if (rawResults instanceof List<?> list) {
            List<ItemStack> results = new ArrayList<>();
            for (Object element : list) {
                if (element instanceof ItemStack stack && !stack.isEmpty()) {
                    results.add(stack.copy());
                }
            }
            if (!results.isEmpty()) {
                return results;
            }
        }

        ItemStack single = recipe.getResult();
        return single.isEmpty() ? List.of() : List.of(single.copy());
    }
}
