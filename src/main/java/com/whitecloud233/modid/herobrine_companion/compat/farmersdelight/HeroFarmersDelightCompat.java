package com.whitecloud233.modid.herobrine_companion.compat.farmersdelight;

import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingRules;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookSessionFlow;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookSessionState;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.IngredientMatcher;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookOption;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class HeroFarmersDelightCompat {
    /** 会话状态（选中项/重复份数/区块票据）。前缀固定为农夫乐事专属，与森罗隔离。 */
    private static final CookSessionState STATE =
            new CookSessionState(CookingRules.FD_STATE_PREFIX);

    private static final String MOD_ID = "farmersdelight";

    private HeroFarmersDelightCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isCookwareStation(Level level, BlockPos pos) {
        if (!isLoaded() || level == null || pos == null) {
            return false;
        }
        return SafeInvoker.isCookwareStation(level, pos);
    }

    public static boolean tickCookware(HeroEntity hero, BlockPos pos) {
        if (!isLoaded() || hero == null || pos == null || hero.level().isClientSide) {
            return false;
        }
        beginCookChunkTicket(hero, pos);
        return SafeInvoker.tickCookware(hero, pos);
    }

    public static ItemStack getCookMainHandDisplay(HeroEntity hero) {
        if (!isLoaded() || hero == null || hero.getInvitedAction() != CookingRules.INVITED_ACTION_COOK
                || hero.getInvitedPos() == null) {
            return ItemStack.EMPTY;
        }
        return SafeInvoker.getCookMainHandDisplay(hero);
    }

    public static ItemStack getCookOffhandDisplay(HeroEntity hero) {
        if (!isLoaded() || hero == null || hero.getInvitedAction() != CookingRules.INVITED_ACTION_COOK
                || hero.getInvitedPos() == null) {
            return ItemStack.EMPTY;
        }
        return SafeInvoker.getCookOffhandDisplay(hero);
    }

    public static void resetCookSelection(HeroEntity hero, BlockPos pos) {
        STATE.resetSelection(hero, pos);
    }

    public static List<CookOption> getCookOptions(HeroEntity hero, BlockPos pos) {
        if (!isLoaded() || hero == null || pos == null || hero.level().isClientSide) {
            return List.of();
        }
        return SafeInvoker.getCookOptions(hero, pos);
    }

    public static List<ItemStack> getAutonomousCookResults(Level level, BlockPos pos) {
        if (!isLoaded() || level == null || pos == null || level.isClientSide) {
            return List.of();
        }
        return SafeInvoker.getAutonomousCookResults(level, pos);
    }

    public static boolean selectCookOption(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
        if (!isLoaded() || hero == null || pos == null || recipeId == null || hero.level().isClientSide) {
            return false;
        }
        return SafeInvoker.selectCookOption(hero, pos, recipeId);
    }

    public static int getCookOptionMaxRepeat(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
        if (!isLoaded() || hero == null || pos == null || recipeId == null || hero.level().isClientSide) {
            return 0;
        }
        return SafeInvoker.getCookOptionMaxRepeat(hero, pos, recipeId);
    }

    public static Component cycleCookSelection(HeroEntity hero, BlockPos pos) {
        if (!isLoaded() || hero == null || pos == null || hero.level().isClientSide) {
            return Component.translatable("message.herobrine_companion.invite_cook_none");
        }
        return SafeInvoker.cycleCookSelection(hero, pos);
    }

    public static void setCookRepeatCount(HeroEntity hero, BlockPos pos, int count) {
        STATE.setRepeatCount(hero, pos, count);
    }

    public static void beginCookChunkTicket(HeroEntity hero, BlockPos pos) {
        STATE.beginChunkTicket(hero, pos);
    }

    public static void endCookChunkTicket(HeroEntity hero) {
        STATE.endChunkTicket(hero);
    }

    private static int getCookRepeatCount(HeroEntity hero, BlockPos pos) {
        return STATE.repeatCount(hero, pos);
    }

    private static boolean consumeCookRepeat(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return false;
        }
        if (STATE.consumeRepeat(hero, pos)) {
            stopCooking(hero, pos);
            return true;
        }
        return true;
    }

    private static void stopCooking(HeroEntity hero, BlockPos pos) {
        endCookChunkTicket(hero);
        hero.setInvitedPos(null);
        hero.setInvitedAction(CookingRules.ACTION_NONE);
        setCookRepeatCount(hero, pos, CookingRules.MIN_COOK_REPEAT_COUNT);
    }

    @Nullable
    private static ResourceLocation getCookSelectionRecipeId(HeroEntity hero, BlockPos pos) {
        return STATE.selectedRecipe(hero, pos);
    }

    private static int getCookSelectionIndex(HeroEntity hero, BlockPos pos, int optionCount) {
        return STATE.selectionIndex(hero, pos, optionCount);
    }

    private static void setCookSelection(HeroEntity hero, BlockPos pos, int index, @Nullable ResourceLocation recipeId) {
        STATE.setSelection(hero, pos, index, recipeId);
    }

    private static <T extends RecipeCandidate> int resolveSelectedRecipeIndex(HeroEntity hero, BlockPos pos, List<T> options) {
        return STATE.resolveSelectedIndex(hero, pos, options, RecipeCandidate::recipeId);
    }

    @Nullable
    private static <T extends RecipeCandidate> T resolveSelectedOption(HeroEntity hero, BlockPos pos, List<T> options) {
        int selectedIndex = resolveSelectedRecipeIndex(hero, pos, options);
        if (selectedIndex < 0 || selectedIndex >= options.size()) {
            return null;
        }
        T option = options.get(selectedIndex);
        setCookSelection(hero, pos, selectedIndex, option.recipeId());
        return option;
    }

    private interface RecipeCandidate {
        ResourceLocation recipeId();

        int maxRepeatCount();
    }

    private record IngredientPlan(@Nullable ItemStack nextIngredient, int matchedCount, int missingCount) {
    }

    private record CookingPotPlan(ResourceLocation recipeId, ItemStack result, @Nullable ItemStack nextIngredientStack,
                                  int missingCount, int maxRepeatCount) implements RecipeCandidate {
    }

    private record CuttingBoardPlan(ResourceLocation recipeId, ItemStack result, @Nullable ItemStack ingredientStack,
                                    int maxRepeatCount) implements RecipeCandidate {
    }

    private record BackendCookOption(ResourceLocation recipeId, ItemStack result, int maxRepeatCount) implements RecipeCandidate {
    }

    private static final class SafeInvoker {
        private SafeInvoker() {
        }

        static boolean isCookwareStation(Level level, BlockPos pos) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            return blockEntity instanceof CookingPotBlockEntity || blockEntity instanceof CuttingBoardBlockEntity;
        }

        static ItemStack getCookMainHandDisplay(HeroEntity hero) {
            if (hero.getInvitedPos() == null || !(hero.level() instanceof ServerLevel serverLevel)) {
                return ItemStack.EMPTY;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(hero.getInvitedPos());
            if (blockEntity instanceof CookingPotBlockEntity) {
                return new ItemStack(Items.WOODEN_SHOVEL);
            }
            if (blockEntity instanceof CuttingBoardBlockEntity cuttingBoard) {
                ServerPlayer owner = resolveOwner(hero);
                if (owner == null) {
                    return ItemStack.EMPTY;
                }

                List<CuttingBoardPlan> plans = collectCuttingBoardPlans(serverLevel, owner, cuttingBoard);
                CuttingBoardPlan selectedPlan = resolveSelectedOption(hero, hero.getInvitedPos(), plans);
                if (selectedPlan == null) {
                    return ItemStack.EMPTY;
                }

                CuttingBoardRecipe recipe = findCuttingBoardRecipe(serverLevel, selectedPlan.recipeId());
                if (recipe == null) {
                    return ItemStack.EMPTY;
                }

                ItemStack toolStack = findAccessibleStack(owner, recipe.getTool()::test);
                return toolStack.isEmpty() ? ItemStack.EMPTY : toolStack.copyWithCount(1);
            }
            return ItemStack.EMPTY;
        }

        static ItemStack getCookOffhandDisplay(HeroEntity hero) {
            if (hero.getInvitedPos() == null || !(hero.level() instanceof ServerLevel serverLevel)) {
                return ItemStack.EMPTY;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(hero.getInvitedPos());
            if (blockEntity instanceof CookingPotBlockEntity cookingPot) {
                ItemStack container = cookingPot.getContainer();
                if (!cookingPot.getMeal().isEmpty() && !container.isEmpty()) {
                    return container.copyWithCount(1);
                }
            }
            return ItemStack.EMPTY;
        }

        static boolean tickCookware(HeroEntity hero, BlockPos pos) {
            if (!(hero.level() instanceof ServerLevel serverLevel)) {
                return false;
            }

            ServerPlayer owner = resolveOwner(hero);
            if (owner == null) {
                return false;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
            if (blockEntity instanceof CookingPotBlockEntity cookingPot) {
                return tickCookingPot(hero, serverLevel, owner, cookingPot);
            }
            if (blockEntity instanceof CuttingBoardBlockEntity cuttingBoard) {
                return tickCuttingBoard(hero, serverLevel, owner, cuttingBoard);
            }
            return false;
        }

        /** 会话状态入口：农夫乐事侧走本后端持有的 CookSessionState 实例（键前缀 HeroFd*）。 */
        private static final CookSessionFlow.SessionAccess SESSION = new CookSessionFlow.SessionAccess() {
            @Override
            public void reset(HeroEntity hero, BlockPos pos) {
                HeroFarmersDelightCompat.resetCookSelection(hero, pos);
            }

            @Override
            public void select(HeroEntity hero, BlockPos pos, int index, ResourceLocation recipeId) {
                setCookSelection(hero, pos, index, recipeId);
            }

            @Override
            public int selectedIndex(HeroEntity hero, BlockPos pos, int optionCount) {
                return getCookSelectionIndex(hero, pos, optionCount);
            }
        };

        /** 轮换选中项：通用流程见 {@link CookSessionFlow}；农夫乐事不要求主人在同一维度。 */
        static Component cycleCookSelection(HeroEntity hero, BlockPos pos) {
            return CookSessionFlow.cycle(hero, pos, SESSION, resolveOwner(hero), false,
                    (level, owner, blockEntity) -> mapOptions(getCookOptions(level, owner, blockEntity)));
        }

        /** 后端选项 → 通用选项的包装（两侧各自的结构性适配）。 */
        private static List<CookOption> mapOptions(List<BackendCookOption> options) {
            if (options.isEmpty()) {
                return List.of();
            }
            List<CookOption> result = new ArrayList<>(options.size());
            for (BackendCookOption option : options) {
                result.add(new CookOption(option.recipeId(), option.result().copy(), option.maxRepeatCount()));
            }
            return result;
        }

        static List<CookOption> getCookOptions(HeroEntity hero, BlockPos pos) {
            if (!(hero.level() instanceof ServerLevel serverLevel)) {
                return List.of();
            }

            ServerPlayer owner = resolveOwner(hero);
            if (owner == null) {
                return List.of();
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
            List<BackendCookOption> options = getCookOptions(serverLevel, owner, blockEntity);
            if (options.isEmpty()) {
                return List.of();
            }

            List<CookOption> result = new ArrayList<>(options.size());
            for (BackendCookOption option : options) {
                result.add(new CookOption(option.recipeId(), option.result().copy(), option.maxRepeatCount()));
            }
            return result;
        }

        static List<ItemStack> getAutonomousCookResults(Level level, BlockPos pos) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return List.of();
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof CookingPotBlockEntity) {
                List<ItemStack> results = new ArrayList<>();
                for (CookingPotRecipe recipe : serverLevel.getRecipeManager().getAllRecipesFor(ModRecipeTypes.COOKING.get())) {
                    addUniqueResult(results, recipe.getResultItem(serverLevel.registryAccess()).copy());
                }
                return results;
            }

            if (blockEntity instanceof CuttingBoardBlockEntity cuttingBoard && !cuttingBoard.isItemCarvingBoard()) {
                List<ItemStack> results = new ArrayList<>();
                for (CuttingBoardRecipe recipe : serverLevel.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CUTTING.get())) {
                    addUniqueResult(results, getCuttingBoardPreview(recipe, serverLevel));
                }
                return results;
            }

            return List.of();
        }

        static boolean selectCookOption(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
            if (!(hero.level() instanceof ServerLevel serverLevel)) {
                return false;
            }

            ServerPlayer owner = resolveOwner(hero);
            if (owner == null) {
                return false;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
            List<BackendCookOption> options = getCookOptions(serverLevel, owner, blockEntity);
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).recipeId().equals(recipeId)) {
                    setCookSelection(hero, pos, i, recipeId);
                    return true;
                }
            }
            return false;
        }

        static int getCookOptionMaxRepeat(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
            if (!(hero.level() instanceof ServerLevel serverLevel)) {
                return 0;
            }

            ServerPlayer owner = resolveOwner(hero);
            if (owner == null) {
                return 0;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
            List<BackendCookOption> options = getCookOptions(serverLevel, owner, blockEntity);
            for (BackendCookOption option : options) {
                if (option.recipeId().equals(recipeId)) {
                    return option.maxRepeatCount();
                }
            }
            return 0;
        }

        @Nullable
        private static ServerPlayer resolveOwner(HeroEntity hero) {
            if (hero.getOwnerUUID() == null || !(hero.level() instanceof ServerLevel serverLevel)) {
                return null;
            }
            return serverLevel.getServer().getPlayerList().getPlayer(hero.getOwnerUUID());
        }

        private static List<BackendCookOption> getCookOptions(ServerLevel level, ServerPlayer owner, @Nullable BlockEntity blockEntity) {
            if (blockEntity instanceof CookingPotBlockEntity cookingPot) {
                return mapCookingPotOptions(collectCookingPotPlans(level, owner, cookingPot));
            }
            if (blockEntity instanceof CuttingBoardBlockEntity cuttingBoard) {
                return mapCuttingBoardOptions(collectCuttingBoardPlans(level, owner, cuttingBoard));
            }
            return List.of();
        }

        private static List<BackendCookOption> mapCookingPotOptions(List<CookingPotPlan> plans) {
            List<BackendCookOption> options = new ArrayList<>(plans.size());
            for (CookingPotPlan plan : plans) {
                options.add(new BackendCookOption(plan.recipeId(), plan.result().copy(), plan.maxRepeatCount()));
            }
            return options;
        }

        private static List<BackendCookOption> mapCuttingBoardOptions(List<CuttingBoardPlan> plans) {
            List<BackendCookOption> options = new ArrayList<>(plans.size());
            for (CuttingBoardPlan plan : plans) {
                options.add(new BackendCookOption(plan.recipeId(), plan.result().copy(), plan.maxRepeatCount()));
            }
            return options;
        }

        private static boolean tickCookingPot(HeroEntity hero, ServerLevel level, ServerPlayer owner, CookingPotBlockEntity cookingPot) {
            BlockPos pos = cookingPot.getBlockPos();
            List<CookingPotPlan> plans = collectCookingPotPlans(level, owner, cookingPot);
            CookingPotPlan selectedPlan = resolveSelectedOption(hero, pos, plans);
            if (selectedPlan == null) {
                stopCooking(hero, pos);
                return false;
            }

            CookingPotRecipe recipe = findCookingPotRecipe(level, selectedPlan.recipeId());
            if (recipe == null) {
                stopCooking(hero, pos);
                return false;
            }

            ItemStackHandler inventory = cookingPot.getInventory();
            ItemStack outputStack = inventory.getStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT);
            if (!outputStack.isEmpty()) {
                giveToOwner(owner, outputStack.copy());
                inventory.setStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
                return consumeCookRepeat(hero, pos);
            }

            ItemStack meal = cookingPot.getMeal();
            if (!meal.isEmpty()) {
                if (!cookingPot.getContainer().isEmpty()) {
                    if (!serveCookingPotMeal(owner, cookingPot)) {
                        stopCooking(hero, pos);
                        return false;
                    }
                    if (cookingPot.getMeal().isEmpty()) {
                        return consumeCookRepeat(hero, pos);
                    }
                    return true;
                }
                return true;
            }

            List<ItemStack> currentInputs = collectCookingPotInputStacks(cookingPot);
            List<ItemStack> availableStacks = collectAccessibleStacks(owner);
            IngredientPlan ingredientPlan = buildIngredientPlan(recipe.getIngredients(), currentInputs, availableStacks);
            if (ingredientPlan == null) {
                stopCooking(hero, pos);
                return false;
            }

            if (ingredientPlan.nextIngredient() != null) {
                if (!tryPlaceCookingPotIngredient(owner, cookingPot, ingredientPlan.nextIngredient())) {
                    stopCooking(hero, pos);
                    return false;
                }
                return true;
            }

            if (computeCookingPotRepeatCount(recipe, currentInputs, availableStacks) <= 0) {
                stopCooking(hero, pos);
                return false;
            }

            return true;
        }

        private static boolean tickCuttingBoard(HeroEntity hero, ServerLevel level, ServerPlayer owner, CuttingBoardBlockEntity cuttingBoard) {
            BlockPos pos = cuttingBoard.getBlockPos();
            List<CuttingBoardPlan> plans = collectCuttingBoardPlans(level, owner, cuttingBoard);
            CuttingBoardPlan selectedPlan = resolveSelectedOption(hero, pos, plans);
            if (selectedPlan == null) {
                stopCooking(hero, pos);
                return false;
            }

            CuttingBoardRecipe recipe = findCuttingBoardRecipe(level, selectedPlan.recipeId());
            if (recipe == null) {
                stopCooking(hero, pos);
                return false;
            }

            ItemStack storedItem = cuttingBoard.getStoredItem();
            if (!storedItem.isEmpty()) {
                ItemStack toolStack = findAccessibleStack(owner, recipe.getTool()::test);
                if (toolStack.isEmpty()) {
                    stopCooking(hero, pos);
                    return false;
                }

                Set<UUID> existingItemIds = collectNearbyItemIds(level, pos);
                if (!cuttingBoard.processStoredItemUsingTool(toolStack, owner)) {
                    stopCooking(hero, pos);
                    return false;
                }

                collectFreshCuttingBoardDrops(owner, level, pos, existingItemIds);
                return consumeCookRepeat(hero, pos);
            }

            if (cuttingBoard.isItemCarvingBoard()) {
                stopCooking(hero, pos);
                return false;
            }

            ItemStack ingredientStack = selectedPlan.ingredientStack();
            if (ingredientStack == null || ingredientStack.isEmpty()) {
                stopCooking(hero, pos);
                return false;
            }

            if (!tryPlaceCuttingBoardIngredient(owner, cuttingBoard, ingredientStack)) {
                stopCooking(hero, pos);
                return false;
            }

            return true;
        }

        private static boolean serveCookingPotMeal(ServerPlayer owner, CookingPotBlockEntity cookingPot) {
            ItemStack containerStack = findAccessibleStack(owner, cookingPot::isContainerValid);
            if (containerStack.isEmpty()) {
                return false;
            }

            ItemStack serving = cookingPot.useHeldItemOnMeal(containerStack);
            if (serving.isEmpty()) {
                return false;
            }

            giveToOwner(owner, serving);
            return true;
        }

        private static boolean tryPlaceCookingPotIngredient(ServerPlayer owner, CookingPotBlockEntity cookingPot, ItemStack ingredientTemplate) {
            ItemStack consumed = consumeMatchingStack(owner, ingredientTemplate, 1);
            if (consumed.isEmpty()) {
                return false;
            }

            ItemStackHandler inventory = cookingPot.getInventory();
            for (int slot = 0; slot < CookingPotRecipe.INPUT_SLOTS; slot++) {
                if (inventory.getStackInSlot(slot).isEmpty()) {
                    inventory.setStackInSlot(slot, consumed);
                    return true;
                }
            }

            giveToOwner(owner, consumed);
            return false;
        }

        private static boolean tryPlaceCuttingBoardIngredient(ServerPlayer owner, CuttingBoardBlockEntity cuttingBoard, ItemStack ingredientTemplate) {
            ItemStack consumed = consumeMatchingStack(owner, ingredientTemplate, 1);
            if (consumed.isEmpty()) {
                return false;
            }

            ItemStack requested = consumed.copy();
            ItemStack remainder = tryAddCuttingBoardIngredient(cuttingBoard, consumed);
            if (remainder.getCount() >= requested.getCount() && ItemStack.isSameItemSameTags(remainder, requested)) {
                giveToOwner(owner, remainder);
                return false;
            }
            if (!remainder.isEmpty()) {
                giveToOwner(owner, remainder);
            }
            return true;
        }

        private static ItemStack tryAddCuttingBoardIngredient(CuttingBoardBlockEntity cuttingBoard, ItemStack stack) {
            try {
                return cuttingBoard.addItem(stack);
            } catch (NoSuchMethodError ignored) {
                return tryInsertCuttingBoardIngredientViaHandler(cuttingBoard, stack);
            }
        }

        private static ItemStack tryInsertCuttingBoardIngredientViaHandler(CuttingBoardBlockEntity cuttingBoard, ItemStack stack) {
            IItemHandler inventory;
            try {
                inventory = cuttingBoard.getInventory();
            } catch (NoSuchMethodError ignored) {
                return stack;
            }

            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < inventory.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = inventory.insertItem(slot, remaining, false);
            }
            return remaining;
        }

        private static List<CookingPotPlan> collectCookingPotPlans(ServerLevel level, ServerPlayer owner, CookingPotBlockEntity cookingPot) {
            List<ItemStack> currentInputs = collectCookingPotInputStacks(cookingPot);
            List<ItemStack> availableStacks = collectAccessibleStacks(owner);
            ItemStack meal = cookingPot.getMeal();
            ItemStack output = cookingPot.getInventory().getStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT);
            List<CookingPotPlan> plans = new ArrayList<>();

            for (CookingPotRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.COOKING.get())) {
                ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
                if (result.isEmpty()) {
                    continue;
                }

                boolean hasReadyMeal = !meal.isEmpty() && ItemStack.isSameItemSameTags(meal, result);
                boolean hasReadyOutput = !output.isEmpty() && ItemStack.isSameItemSameTags(output, result);

                IngredientPlan ingredientPlan = null;
                int ingredientRepeatCount;
                int currentBatchBonus = 0;

                if (hasReadyMeal || hasReadyOutput) {
                    currentBatchBonus = 1;
                    ingredientRepeatCount = computeCookingPotRepeatCount(recipe, List.of(), availableStacks);
                } else {
                    ingredientPlan = buildIngredientPlan(recipe.getIngredients(), currentInputs, availableStacks);
                    ingredientRepeatCount = computeCookingPotRepeatCount(recipe, currentInputs, availableStacks);
                    if (ingredientPlan == null || ingredientRepeatCount <= 0) {
                        continue;
                    }
                }

                int containerRepeatLimit = computeCookingPotContainerRepeatLimit(recipe, cookingPot, hasReadyMeal, hasReadyOutput, availableStacks, result);
                int maxRepeatCount = Math.min(CookingRules.MAX_COOK_REPEAT_COUNT, Math.min(currentBatchBonus + ingredientRepeatCount, containerRepeatLimit));
                if (maxRepeatCount <= 0) {
                    continue;
                }

                plans.add(new CookingPotPlan(recipe.getId(), result,
                        ingredientPlan == null ? null : ingredientPlan.nextIngredient(),
                        ingredientPlan == null ? 0 : ingredientPlan.missingCount(),
                        maxRepeatCount));
            }

            plans.sort(Comparator.comparing(plan -> plan.recipeId().toString()));
            return plans;
        }

        private static int computeCookingPotContainerRepeatLimit(CookingPotRecipe recipe, CookingPotBlockEntity cookingPot,
                                                                 boolean hasReadyMeal, boolean hasReadyOutput,
                                                                 List<ItemStack> availableStacks, ItemStack recipeResult) {
            ItemStack requiredContainer = recipe.getOutputContainer();
            if (requiredContainer.isEmpty()) {
                return CookingRules.MAX_COOK_REPEAT_COUNT;
            }

            int availableContainerCount = countSameItem(requiredContainer, availableStacks);
            int servingsPerBatch = Math.max(1, recipeResult.getCount());

            if (hasReadyMeal) {
                int remainingServings = cookingPot.getMeal().getCount();
                if (availableContainerCount < remainingServings) {
                    return 0;
                }
                availableContainerCount -= remainingServings;
                return 1 + availableContainerCount / servingsPerBatch;
            }

            if (hasReadyOutput) {
                return 1 + availableContainerCount / servingsPerBatch;
            }

            return availableContainerCount / servingsPerBatch;
        }

        private static int computeCookingPotRepeatCount(CookingPotRecipe recipe, List<ItemStack> currentInputs, List<ItemStack> availableStacks) {
            return computeIngredientCraftCount(recipe.getIngredients(), currentInputs, availableStacks);
        }

        private static List<CuttingBoardPlan> collectCuttingBoardPlans(ServerLevel level, ServerPlayer owner, CuttingBoardBlockEntity cuttingBoard) {
            if (cuttingBoard.isItemCarvingBoard()) {
                return List.of();
            }

            List<ItemStack> availableStacks = collectAccessibleStacks(owner);
            ItemStack currentItem = cuttingBoard.getStoredItem();
            boolean hasCurrentItem = !currentItem.isEmpty();
            List<CuttingBoardPlan> plans = new ArrayList<>();

            for (CuttingBoardRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CUTTING.get())) {
                ItemStack toolStack = findMatchingStack(recipe.getTool(), 1, availableStacks);
                if (toolStack.isEmpty()) {
                    continue;
                }

                ItemStack preview = getCuttingBoardPreview(recipe, level);
                if (preview.isEmpty()) {
                    continue;
                }

                int maxRepeatCount;
                ItemStack ingredientStack = ItemStack.EMPTY;
                Ingredient ingredient = recipe.getIngredients().isEmpty() ? Ingredient.EMPTY : recipe.getIngredients().get(0);
                if (ingredient.isEmpty()) {
                    continue;
                }

                if (hasCurrentItem) {
                    if (!recipe.matches(createSingleSlotWrapper(currentItem), level)) {
                        continue;
                    }
                    maxRepeatCount = Math.min(CookingRules.MAX_COOK_REPEAT_COUNT, countMatchingItems(ingredient, availableStacks) + 1);
                } else {
                    ingredientStack = findMatchingStack(ingredient, 1, availableStacks);
                    if (ingredientStack.isEmpty()) {
                        continue;
                    }
                    maxRepeatCount = Math.min(CookingRules.MAX_COOK_REPEAT_COUNT, countMatchingItems(ingredient, availableStacks));
                }

                if (maxRepeatCount <= 0) {
                    continue;
                }

                plans.add(new CuttingBoardPlan(recipe.getId(), preview, ingredientStack.isEmpty() ? null : ingredientStack, maxRepeatCount));
            }

            plans.sort(Comparator.comparing(plan -> plan.recipeId().toString()));
            return plans;
        }

        private static ItemStack getCuttingBoardPreview(CuttingBoardRecipe recipe, ServerLevel level) {
            ItemStack preview = recipe.getResultItem(level.registryAccess()).copy();
            if (!preview.isEmpty()) {
                return preview;
            }

            List<ItemStack> results = recipe.getResults();
            return results.isEmpty() ? ItemStack.EMPTY : results.get(0).copy();
        }

        @Nullable
        private static CookingPotRecipe findCookingPotRecipe(ServerLevel level, ResourceLocation recipeId) {
            for (CookingPotRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.COOKING.get())) {
                if (recipe.getId().equals(recipeId)) {
                    return recipe;
                }
            }
            return null;
        }

        @Nullable
        private static CuttingBoardRecipe findCuttingBoardRecipe(ServerLevel level, ResourceLocation recipeId) {
            for (CuttingBoardRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CUTTING.get())) {
                if (recipe.getId().equals(recipeId)) {
                    return recipe;
                }
            }
            return null;
        }

        private static RecipeWrapper createSingleSlotWrapper(ItemStack stack) {
            ItemStackHandler handler = new ItemStackHandler(1);
            handler.setStackInSlot(0, stack.copyWithCount(1));
            return new RecipeWrapper(handler);
        }

        private static List<ItemStack> collectCookingPotInputStacks(CookingPotBlockEntity cookingPot) {
            List<ItemStack> inputs = new ArrayList<>();
            ItemStackHandler inventory = cookingPot.getInventory();
            for (int slot = 0; slot < CookingPotRecipe.INPUT_SLOTS; slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    inputs.add(stack);
                }
            }
            return inputs;
        }

        private static Set<UUID> collectNearbyItemIds(ServerLevel level, BlockPos pos) {
            Set<UUID> ids = new HashSet<>();
            for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5D))) {
                ids.add(itemEntity.getUUID());
            }
            return ids;
        }

        private static void collectFreshCuttingBoardDrops(ServerPlayer owner, ServerLevel level, BlockPos pos, Set<UUID> existingItemIds) {
            for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5D))) {
                if (existingItemIds.contains(itemEntity.getUUID()) || itemEntity.getItem().isEmpty()) {
                    continue;
                }
                giveToOwner(owner, itemEntity.getItem().copy());
                itemEntity.discard();
            }
        }

        private static void giveToOwner(ServerPlayer owner, ItemStack stack) {
            if (stack.isEmpty()) {
                return;
            }

            ItemStack remainder = stack.copy();
            if (!owner.getInventory().add(remainder) && !remainder.isEmpty()) {
                owner.drop(remainder, false);
            }
            owner.getInventory().setChanged();
        }

        private static void addUniqueResult(List<ItemStack> results, ItemStack candidate) {
            IngredientMatcher.addUniqueResult(results, candidate);
        }
        private static ItemStack consumeMatchingStack(ServerPlayer owner, ItemStack template, int amount) {
            ItemStack stack = findAccessibleStack(owner, candidate -> ItemStack.isSameItemSameTags(candidate, template) && candidate.getCount() >= amount);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            ItemStack taken = stack.copyWithCount(amount);
            stack.shrink(amount);
            owner.getInventory().setChanged();
            return taken;
        }

        private static ItemStack findAccessibleStack(ServerPlayer owner, Predicate<ItemStack> predicate) {
            for (ItemStack stack : owner.getInventory().items) {
                if (!stack.isEmpty() && predicate.test(stack)) {
                    return stack;
                }
            }
            for (ItemStack stack : owner.getInventory().offhand) {
                if (!stack.isEmpty() && predicate.test(stack)) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        static int computeIngredientCraftCount(List<Ingredient> rawIngredients, List<ItemStack> currentInputs,
                                                      List<ItemStack> availableStacks) {
            return IngredientMatcher.computeCraftCount(rawIngredients, currentInputs, availableStacks);
        }

        static boolean consumeIngredientGroup(List<Ingredient> ingredients, List<ItemStack> availableStacks, int[] counts) {
            return IngredientMatcher.consumeIngredientGroup(ingredients, availableStacks, counts);
        }

        private static int[] copyStackCounts(List<ItemStack> availableStacks) {
            return IngredientMatcher.copyStackCounts(availableStacks);
        }
        private static int countMatchingItems(Ingredient ingredient, List<ItemStack> availableStacks) {
            return IngredientMatcher.countMatchingItems(ingredient, availableStacks);
        }
        private static int countSameItem(ItemStack target, List<ItemStack> availableStacks) {
            return IngredientMatcher.countSameItem(target, availableStacks);
        }
        private static List<Ingredient> normalizeIngredients(List<Ingredient> rawIngredients) {
            return IngredientMatcher.normalize(rawIngredients);
        }
        @Nullable
        private static IngredientPlan buildIngredientPlan(List<Ingredient> rawIngredients, List<ItemStack> currentInputs,
                                                          List<ItemStack> availableStacks) {
            IngredientMatcher.IngredientPlanResult plan =
                    IngredientMatcher.planIngredient(rawIngredients, currentInputs, availableStacks);
            if (plan == null) {
                return null;
            }
            return new IngredientPlan(plan.nextIngredient(), plan.matchedCount(), plan.missingCount());
        }


        private static ItemStack findMatchingStack(Ingredient ingredient, int minCount, List<ItemStack> availableStacks) {
            return IngredientMatcher.findMatchingStack(ingredient, minCount, availableStacks);
        }
        private static List<ItemStack> collectAccessibleStacks(ServerPlayer owner) {
            return IngredientMatcher.collectAccessibleStacks(owner);
        }    }
}
