package com.whitecloud233.herobrine_companion.compat.farmersdelight;

import com.whitecloud233.herobrine_companion.compat.cooking.HeroCookingCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipeInput;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

public final class HeroFarmersDelightCompat {
    private static final String MOD_ID = "farmersdelight";
    private static final String COOK_SELECTION_POS_KEY = "HeroFdCookSelectionPos";
    private static final String COOK_SELECTION_INDEX_KEY = "HeroFdCookSelectionIndex";
    private static final String COOK_SELECTION_RECIPE_KEY = "HeroFdCookSelectionRecipe";
    private static final String COOK_REPEAT_POS_KEY = "HeroFdCookRepeatPos";
    private static final String COOK_REPEAT_REMAINING_KEY = "HeroFdCookRepeatRemaining";
    private static final String COOK_TICKET_LEVEL_KEY = "HeroFdCookTicketLevel";
    private static final String COOK_TICKET_CHUNK_KEY = "HeroFdCookTicketChunk";
    private static final int COOK_TICKET_DISTANCE = 1;

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
        if (!isLoaded() || hero == null || hero.getInvitedAction() != HeroCookingCompat.INVITED_ACTION_COOK
                || hero.getInvitedPos() == null) {
            return ItemStack.EMPTY;
        }
        return SafeInvoker.getCookMainHandDisplay(hero);
    }

    public static ItemStack getCookOffhandDisplay(HeroEntity hero) {
        if (!isLoaded() || hero == null || hero.getInvitedAction() != HeroCookingCompat.INVITED_ACTION_COOK
                || hero.getInvitedPos() == null) {
            return ItemStack.EMPTY;
        }
        return SafeInvoker.getCookOffhandDisplay(hero);
    }

    public static void resetCookSelection(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return;
        }
        hero.getPersistentData().putLong(COOK_SELECTION_POS_KEY, pos.asLong());
        hero.getPersistentData().putInt(COOK_SELECTION_INDEX_KEY, 0);
        hero.getPersistentData().remove(COOK_SELECTION_RECIPE_KEY);
        hero.getPersistentData().putLong(COOK_REPEAT_POS_KEY, pos.asLong());
        hero.getPersistentData().putInt(COOK_REPEAT_REMAINING_KEY, HeroCookingCompat.MIN_COOK_REPEAT_COUNT);
    }

    public static List<HeroCookingCompat.CookOptionView> getCookOptions(HeroEntity hero, BlockPos pos) {
        if (!isLoaded() || hero == null || pos == null || hero.level().isClientSide) {
            return List.of();
        }
        return SafeInvoker.getCookOptions(hero, pos);
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
        if (hero == null || pos == null) {
            return;
        }
        hero.getPersistentData().putLong(COOK_REPEAT_POS_KEY, pos.asLong());
        hero.getPersistentData().putInt(COOK_REPEAT_REMAINING_KEY, HeroCookingCompat.clampCookRepeatCount(count));
    }

    public static void beginCookChunkTicket(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null || hero.level().isClientSide || !(hero.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkPos targetChunk = new ChunkPos(pos);
        String targetLevelId = serverLevel.dimension().location().toString();
        String currentLevelId = hero.getPersistentData().getString(COOK_TICKET_LEVEL_KEY);
        long currentChunkLong = hero.getPersistentData().getLong(COOK_TICKET_CHUNK_KEY);
        if (targetLevelId.equals(currentLevelId) && currentChunkLong == targetChunk.toLong()) {
            return;
        }

        endCookChunkTicket(hero);
        serverLevel.getChunkSource().addRegionTicket(TicketType.UNKNOWN, targetChunk, COOK_TICKET_DISTANCE, targetChunk);
        hero.getPersistentData().putString(COOK_TICKET_LEVEL_KEY, targetLevelId);
        hero.getPersistentData().putLong(COOK_TICKET_CHUNK_KEY, targetChunk.toLong());
    }

    public static void endCookChunkTicket(HeroEntity hero) {
        if (hero == null || hero.level().isClientSide) {
            return;
        }

        String levelId = hero.getPersistentData().getString(COOK_TICKET_LEVEL_KEY);
        if (!levelId.isBlank() && hero.getServer() != null) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(levelId);
            if (dimensionId != null) {
                ServerLevel ticketLevel = hero.getServer().getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, dimensionId));
                long chunkLong = hero.getPersistentData().getLong(COOK_TICKET_CHUNK_KEY);
                if (ticketLevel != null) {
                    ChunkPos chunkPos = new ChunkPos(chunkLong);
                    ticketLevel.getChunkSource().removeRegionTicket(TicketType.UNKNOWN, chunkPos, COOK_TICKET_DISTANCE, chunkPos);
                }
            }
        }

        hero.getPersistentData().remove(COOK_TICKET_LEVEL_KEY);
        hero.getPersistentData().remove(COOK_TICKET_CHUNK_KEY);
    }

    private static int getCookRepeatCount(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return HeroCookingCompat.MIN_COOK_REPEAT_COUNT;
        }

        long storedPos = hero.getPersistentData().getLong(COOK_REPEAT_POS_KEY);
        if (storedPos != pos.asLong()) {
            setCookRepeatCount(hero, pos, HeroCookingCompat.MIN_COOK_REPEAT_COUNT);
            return HeroCookingCompat.MIN_COOK_REPEAT_COUNT;
        }

        int remaining = HeroCookingCompat.clampCookRepeatCount(hero.getPersistentData().getInt(COOK_REPEAT_REMAINING_KEY));
        hero.getPersistentData().putInt(COOK_REPEAT_REMAINING_KEY, remaining);
        return remaining;
    }

    private static boolean consumeCookRepeat(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return false;
        }

        int remaining = getCookRepeatCount(hero, pos);
        if (remaining <= HeroCookingCompat.MIN_COOK_REPEAT_COUNT) {
            stopCooking(hero, pos);
            return true;
        }

        setCookRepeatCount(hero, pos, remaining - 1);
        return true;
    }

    private static void stopCooking(HeroEntity hero, BlockPos pos) {
        endCookChunkTicket(hero);
        hero.setInvitedPos(null);
        hero.setInvitedAction(0);
        setCookRepeatCount(hero, pos, HeroCookingCompat.MIN_COOK_REPEAT_COUNT);
    }

    @Nullable
    private static ResourceLocation getCookSelectionRecipeId(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return null;
        }

        long storedPos = hero.getPersistentData().getLong(COOK_SELECTION_POS_KEY);
        if (storedPos != pos.asLong()) {
            return null;
        }

        String recipeId = hero.getPersistentData().getString(COOK_SELECTION_RECIPE_KEY);
        return recipeId.isBlank() ? null : ResourceLocation.tryParse(recipeId);
    }

    private static int getCookSelectionIndex(HeroEntity hero, BlockPos pos, int optionCount) {
        if (hero == null || pos == null || optionCount <= 0) {
            return 0;
        }

        long storedPos = hero.getPersistentData().getLong(COOK_SELECTION_POS_KEY);
        if (storedPos != pos.asLong()) {
            resetCookSelection(hero, pos);
            return 0;
        }

        int index = hero.getPersistentData().getInt(COOK_SELECTION_INDEX_KEY);
        if (index < 0 || index >= optionCount) {
            index = Math.floorMod(index, optionCount);
            hero.getPersistentData().putInt(COOK_SELECTION_INDEX_KEY, index);
        }
        return index;
    }

    private static void setCookSelection(HeroEntity hero, BlockPos pos, int index, @Nullable ResourceLocation recipeId) {
        if (hero == null || pos == null) {
            return;
        }
        hero.getPersistentData().putLong(COOK_SELECTION_POS_KEY, pos.asLong());
        hero.getPersistentData().putInt(COOK_SELECTION_INDEX_KEY, Math.max(0, index));
        if (recipeId == null) {
            hero.getPersistentData().remove(COOK_SELECTION_RECIPE_KEY);
        } else {
            hero.getPersistentData().putString(COOK_SELECTION_RECIPE_KEY, recipeId.toString());
        }
    }

    private static <T extends RecipeCandidate> int resolveSelectedRecipeIndex(HeroEntity hero, BlockPos pos, List<T> options) {
        if (options.isEmpty()) {
            return -1;
        }

        ResourceLocation selectedRecipeId = getCookSelectionRecipeId(hero, pos);
        if (selectedRecipeId != null) {
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).recipeId().equals(selectedRecipeId)) {
                    return i;
                }
            }
            return -1;
        }

        return getCookSelectionIndex(hero, pos, options.size());
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

    private record CookOption(ResourceLocation recipeId, ItemStack result, int maxRepeatCount) implements RecipeCandidate {
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

        static Component cycleCookSelection(HeroEntity hero, BlockPos pos) {
            if (!(hero.level() instanceof ServerLevel serverLevel)) {
                return Component.translatable("message.herobrine_companion.invite_cook_none");
            }

            ServerPlayer owner = resolveOwner(hero);
            if (owner == null) {
                return Component.translatable("message.herobrine_companion.invite_cook_none");
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
            List<CookOption> options = getCookOptions(serverLevel, owner, blockEntity);
            if (options.isEmpty()) {
                resetCookSelection(hero, pos);
                return Component.translatable("message.herobrine_companion.invite_cook_none");
            }

            if (options.size() == 1) {
                setCookSelection(hero, pos, 0, options.get(0).recipeId());
                return Component.translatable("message.herobrine_companion.invite_cook_single", options.get(0).result().getHoverName());
            }

            int nextIndex = (getCookSelectionIndex(hero, pos, options.size()) + 1) % options.size();
            setCookSelection(hero, pos, nextIndex, options.get(nextIndex).recipeId());
            return Component.translatable("message.herobrine_companion.invite_cook_selected", options.get(nextIndex).result().getHoverName());
        }

        static List<HeroCookingCompat.CookOptionView> getCookOptions(HeroEntity hero, BlockPos pos) {
            if (!(hero.level() instanceof ServerLevel serverLevel)) {
                return List.of();
            }

            ServerPlayer owner = resolveOwner(hero);
            if (owner == null) {
                return List.of();
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
            List<CookOption> options = getCookOptions(serverLevel, owner, blockEntity);
            if (options.isEmpty()) {
                return List.of();
            }

            List<HeroCookingCompat.CookOptionView> result = new ArrayList<>(options.size());
            for (CookOption option : options) {
                result.add(new HeroCookingCompat.CookOptionView(option.recipeId(), option.result().copy(), option.maxRepeatCount()));
            }
            return result;
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
            List<CookOption> options = getCookOptions(serverLevel, owner, blockEntity);
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
            List<CookOption> options = getCookOptions(serverLevel, owner, blockEntity);
            for (CookOption option : options) {
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

        private static List<CookOption> getCookOptions(ServerLevel level, ServerPlayer owner, @Nullable BlockEntity blockEntity) {
            if (blockEntity instanceof CookingPotBlockEntity cookingPot) {
                return mapCookingPotOptions(collectCookingPotPlans(level, owner, cookingPot));
            }
            if (blockEntity instanceof CuttingBoardBlockEntity cuttingBoard) {
                return mapCuttingBoardOptions(collectCuttingBoardPlans(level, owner, cuttingBoard));
            }
            return List.of();
        }

        private static List<CookOption> mapCookingPotOptions(List<CookingPotPlan> plans) {
            List<CookOption> options = new ArrayList<>(plans.size());
            for (CookingPotPlan plan : plans) {
                options.add(new CookOption(plan.recipeId(), plan.result().copy(), plan.maxRepeatCount()));
            }
            return options;
        }

        private static List<CookOption> mapCuttingBoardOptions(List<CuttingBoardPlan> plans) {
            List<CookOption> options = new ArrayList<>(plans.size());
            for (CuttingBoardPlan plan : plans) {
                options.add(new CookOption(plan.recipeId(), plan.result().copy(), plan.maxRepeatCount()));
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
            if (remainder.getCount() >= requested.getCount() && ItemStack.isSameItemSameComponents(remainder, requested)) {
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

            for (RecipeHolder<CookingPotRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.COOKING.get())) {
                CookingPotRecipe recipe = holder.value();
                ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
                if (result.isEmpty()) {
                    continue;
                }

                boolean hasReadyMeal = !meal.isEmpty() && ItemStack.isSameItemSameComponents(meal, result);
                boolean hasReadyOutput = !output.isEmpty() && ItemStack.isSameItemSameComponents(output, result);

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
                int maxRepeatCount = Math.min(HeroCookingCompat.MAX_COOK_REPEAT_COUNT, Math.min(currentBatchBonus + ingredientRepeatCount, containerRepeatLimit));
                if (maxRepeatCount <= 0) {
                    continue;
                }

                plans.add(new CookingPotPlan(holder.id(), result,
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
                return HeroCookingCompat.MAX_COOK_REPEAT_COUNT;
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

            for (RecipeHolder<CuttingBoardRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CUTTING.get())) {
                CuttingBoardRecipe recipe = holder.value();
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
                    if (!recipe.matches(createCuttingBoardInput(currentItem, toolStack), level)) {
                        continue;
                    }
                    maxRepeatCount = Math.min(HeroCookingCompat.MAX_COOK_REPEAT_COUNT, countMatchingItems(ingredient, availableStacks) + 1);
                } else {
                    ingredientStack = findMatchingStack(ingredient, 1, availableStacks);
                    if (ingredientStack.isEmpty()) {
                        continue;
                    }
                    maxRepeatCount = Math.min(HeroCookingCompat.MAX_COOK_REPEAT_COUNT, countMatchingItems(ingredient, availableStacks));
                }

                if (maxRepeatCount <= 0) {
                    continue;
                }

                plans.add(new CuttingBoardPlan(holder.id(), preview, ingredientStack.isEmpty() ? null : ingredientStack, maxRepeatCount));
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
            for (RecipeHolder<CookingPotRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.COOKING.get())) {
                if (holder.id().equals(recipeId)) {
                    return holder.value();
                }
            }
            return null;
        }

        @Nullable
        private static CuttingBoardRecipe findCuttingBoardRecipe(ServerLevel level, ResourceLocation recipeId) {
            for (RecipeHolder<CuttingBoardRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CUTTING.get())) {
                if (holder.id().equals(recipeId)) {
                    return holder.value();
                }
            }
            return null;
        }

        private static CuttingBoardRecipeInput createCuttingBoardInput(ItemStack stack, ItemStack toolStack) {
            return new CuttingBoardRecipeInput(stack.copyWithCount(1), toolStack.copyWithCount(1));
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

        private static ItemStack consumeMatchingStack(ServerPlayer owner, ItemStack template, int amount) {
            ItemStack stack = findAccessibleStack(owner, candidate -> ItemStack.isSameItemSameComponents(candidate, template) && candidate.getCount() >= amount);
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

        private static int computeIngredientCraftCount(List<Ingredient> rawIngredients, List<ItemStack> currentInputs,
                                                       List<ItemStack> availableStacks) {
            List<Ingredient> ingredients = normalizeIngredients(rawIngredients);
            if (ingredients.isEmpty()) {
                return HeroCookingCompat.MAX_COOK_REPEAT_COUNT;
            }

            boolean[] usedByCurrent = new boolean[ingredients.size()];
            if (!matchExistingInputs(currentInputs, ingredients, usedByCurrent, 0)) {
                return 0;
            }

            List<Ingredient> firstCycleRemaining = new ArrayList<>();
            for (int i = 0; i < ingredients.size(); i++) {
                if (!usedByCurrent[i]) {
                    firstCycleRemaining.add(ingredients.get(i));
                }
            }

            int[] counts = copyStackCounts(availableStacks);
            if (!firstCycleRemaining.isEmpty() && !consumeIngredientGroup(firstCycleRemaining, availableStacks, counts)) {
                return 0;
            }

            int craftCount = 1;
            while (craftCount < HeroCookingCompat.MAX_COOK_REPEAT_COUNT && consumeIngredientGroup(ingredients, availableStacks, counts)) {
                craftCount++;
            }
            return craftCount;
        }

        private static boolean consumeIngredientGroup(List<Ingredient> ingredients, List<ItemStack> availableStacks, int[] counts) {
            return ingredients.isEmpty() || matchRemainingIngredients(ingredients, availableStacks, counts, new ArrayList<>(), 0);
        }

        private static int[] copyStackCounts(List<ItemStack> availableStacks) {
            int[] counts = new int[availableStacks.size()];
            for (int i = 0; i < availableStacks.size(); i++) {
                counts[i] = availableStacks.get(i).getCount();
            }
            return counts;
        }

        private static int countMatchingItems(Ingredient ingredient, List<ItemStack> availableStacks) {
            int total = 0;
            for (ItemStack stack : availableStacks) {
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        private static int countSameItem(ItemStack target, List<ItemStack> availableStacks) {
            int total = 0;
            for (ItemStack stack : availableStacks) {
                if (!stack.isEmpty() && ItemStack.isSameItem(target, stack)) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        private static List<Ingredient> normalizeIngredients(List<Ingredient> rawIngredients) {
            List<Ingredient> ingredients = new ArrayList<>();
            for (Ingredient ingredient : rawIngredients) {
                if (ingredient != null && !ingredient.isEmpty()) {
                    ingredients.add(ingredient);
                }
            }
            return ingredients;
        }

        @Nullable
        private static IngredientPlan buildIngredientPlan(List<Ingredient> rawIngredients, List<ItemStack> currentInputs,
                                                          List<ItemStack> availableStacks) {
            List<Ingredient> ingredients = normalizeIngredients(rawIngredients);

            boolean[] usedByCurrent = new boolean[ingredients.size()];
            if (!matchExistingInputs(currentInputs, ingredients, usedByCurrent, 0)) {
                return null;
            }

            List<Ingredient> remainingIngredients = new ArrayList<>();
            for (int i = 0; i < ingredients.size(); i++) {
                if (!usedByCurrent[i]) {
                    remainingIngredients.add(ingredients.get(i));
                }
            }

            if (remainingIngredients.isEmpty()) {
                return new IngredientPlan(null, currentInputs.size(), 0);
            }

            int[] counts = copyStackCounts(availableStacks);
            List<ItemStack> chosenStacks = new ArrayList<>();
            if (!matchRemainingIngredients(remainingIngredients, availableStacks, counts, chosenStacks, 0)) {
                return null;
            }

            ItemStack nextIngredient = chosenStacks.isEmpty() ? null : chosenStacks.get(0);
            return new IngredientPlan(nextIngredient, currentInputs.size(), remainingIngredients.size());
        }

        private static boolean matchExistingInputs(List<ItemStack> currentInputs, List<Ingredient> ingredients, boolean[] used, int index) {
            if (index >= currentInputs.size()) {
                return true;
            }

            ItemStack input = currentInputs.get(index);
            for (int i = 0; i < ingredients.size(); i++) {
                if (used[i]) {
                    continue;
                }

                Ingredient ingredient = ingredients.get(i);
                if (!ingredient.test(input)) {
                    continue;
                }

                used[i] = true;
                if (matchExistingInputs(currentInputs, ingredients, used, index + 1)) {
                    return true;
                }
                used[i] = false;
            }
            return false;
        }

        private static boolean matchRemainingIngredients(List<Ingredient> remainingIngredients, List<ItemStack> availableStacks,
                                                         int[] counts, List<ItemStack> chosenStacks, int index) {
            if (index >= remainingIngredients.size()) {
                return true;
            }

            Ingredient ingredient = remainingIngredients.get(index);
            for (int i = 0; i < availableStacks.size(); i++) {
                if (counts[i] <= 0) {
                    continue;
                }

                ItemStack candidate = availableStacks.get(i);
                if (!ingredient.test(candidate)) {
                    continue;
                }

                counts[i]--;
                chosenStacks.add(candidate);
                if (matchRemainingIngredients(remainingIngredients, availableStacks, counts, chosenStacks, index + 1)) {
                    return true;
                }
                chosenStacks.remove(chosenStacks.size() - 1);
                counts[i]++;
            }
            return false;
        }

        private static ItemStack findMatchingStack(Ingredient ingredient, int minCount, List<ItemStack> availableStacks) {
            for (ItemStack stack : availableStacks) {
                if (!stack.isEmpty() && stack.getCount() >= minCount && ingredient.test(stack)) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        private static List<ItemStack> collectAccessibleStacks(ServerPlayer owner) {
            List<ItemStack> stacks = new ArrayList<>(owner.getInventory().items.size() + owner.getInventory().offhand.size());
            for (ItemStack stack : owner.getInventory().items) {
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
            for (ItemStack stack : owner.getInventory().offhand) {
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
            return stacks;
        }
    }
}
