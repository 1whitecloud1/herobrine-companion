package com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.lang.reflect.Field;

public final class HeroKaleidoscopeCompat {
    public static final int INVITED_ACTION_COOK = 4;

    private static final String MOD_ID = "kaleidoscope_cookery";
    private static final String COOK_SELECTION_POS_KEY = "HeroCookSelectionPos";
    private static final String COOK_SELECTION_INDEX_KEY = "HeroCookSelectionIndex";
    private static final String COOK_SELECTION_RECIPE_KEY = "HeroCookSelectionRecipe";
    private static final String COOK_REPEAT_POS_KEY = "HeroCookRepeatPos";
    private static final String COOK_REPEAT_REMAINING_KEY = "HeroCookRepeatRemaining";
    private static final String COOK_TICKET_LEVEL_KEY = "HeroCookTicketLevel";
    private static final String COOK_TICKET_CHUNK_KEY = "HeroCookTicketChunk";
    private static final int COOK_TICKET_DISTANCE = 1;
    public static final int MIN_COOK_REPEAT_COUNT = 1;
    public static final int MAX_COOK_REPEAT_COUNT = 64;

    private HeroKaleidoscopeCompat() {
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
        if (!isLoaded() || hero == null || hero.getInvitedAction() != INVITED_ACTION_COOK || hero.getInvitedPos() == null) {
            return ItemStack.EMPTY;
        }
        return SafeInvoker.getCookMainHandDisplay(hero.level(), hero.getInvitedPos());
    }

    public static ItemStack getCookOffhandDisplay(HeroEntity hero) {
        if (!isLoaded() || hero == null || hero.getInvitedAction() != INVITED_ACTION_COOK || hero.getInvitedPos() == null) {
            return ItemStack.EMPTY;
        }
        return SafeInvoker.getCookOffhandDisplay(hero.level(), hero.getInvitedPos());
    }

    public static void resetCookSelection(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return;
        }
        hero.getPersistentData().putLong(COOK_SELECTION_POS_KEY, pos.asLong());
        hero.getPersistentData().putInt(COOK_SELECTION_INDEX_KEY, 0);
        hero.getPersistentData().remove(COOK_SELECTION_RECIPE_KEY);
        hero.getPersistentData().putLong(COOK_REPEAT_POS_KEY, pos.asLong());
        hero.getPersistentData().putInt(COOK_REPEAT_REMAINING_KEY, MIN_COOK_REPEAT_COUNT);
    }

    public static List<CookOptionView> getCookOptions(HeroEntity hero, BlockPos pos) {
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

    public static List<AutonomousCookOption> getAutonomousCookOptions(Level level, BlockPos pos) {
        if (!isLoaded() || level == null || pos == null || level.isClientSide) {
            return List.of();
        }
        return SafeInvoker.getAutonomousCookOptions(level, pos);
    }

    public static CompoundTag captureAutonomousCookwareSnapshot(Level level, BlockPos pos) {
        if (!isLoaded() || level == null || pos == null) {
            return new CompoundTag();
        }
        return SafeInvoker.captureAutonomousCookwareSnapshot(level, pos);
    }

    public static boolean applyAutonomousCookwareDisplay(Level level, BlockPos pos, ResourceLocation recipeId, ItemStack result) {
        if (!isLoaded() || level == null || pos == null || recipeId == null || result == null || result.isEmpty() || level.isClientSide) {
            return false;
        }
        return SafeInvoker.applyAutonomousCookwareDisplay(level, pos, recipeId, result);
    }

    public static void restoreAutonomousCookwareSnapshot(Level level, BlockPos pos, CompoundTag snapshot) {
        if (!isLoaded() || level == null || pos == null || snapshot == null || snapshot.isEmpty()) {
            return;
        }
        SafeInvoker.restoreAutonomousCookwareSnapshot(level, pos, snapshot);
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
        hero.getPersistentData().putInt(COOK_REPEAT_REMAINING_KEY, clampCookRepeatCount(count));
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

    public static int clampCookRepeatCount(int count) {
        return Math.max(MIN_COOK_REPEAT_COUNT, Math.min(MAX_COOK_REPEAT_COUNT, count));
    }

    private static int getCookRepeatCount(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return MIN_COOK_REPEAT_COUNT;
        }

        long storedPos = hero.getPersistentData().getLong(COOK_REPEAT_POS_KEY);
        if (storedPos != pos.asLong()) {
            setCookRepeatCount(hero, pos, MIN_COOK_REPEAT_COUNT);
            return MIN_COOK_REPEAT_COUNT;
        }

        int remaining = clampCookRepeatCount(hero.getPersistentData().getInt(COOK_REPEAT_REMAINING_KEY));
        hero.getPersistentData().putInt(COOK_REPEAT_REMAINING_KEY, remaining);
        return remaining;
    }

    private static boolean consumeCookRepeat(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return false;
        }

        int remaining = getCookRepeatCount(hero, pos);
        if (remaining <= MIN_COOK_REPEAT_COUNT) {
            endCookChunkTicket(hero);
            hero.setInvitedPos(null);
            hero.setInvitedAction(0);
            setCookRepeatCount(hero, pos, MIN_COOK_REPEAT_COUNT);
            return true;
        }

        setCookRepeatCount(hero, pos, remaining - 1);
        return true;
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

    public record CookOptionView(ResourceLocation recipeId, ItemStack result, int maxRepeatCount) {
    }

    public record AutonomousCookOption(ResourceLocation recipeId, ItemStack result) {
    }

    private interface RecipeCandidate {
        ResourceLocation recipeId();

        int maxRepeatCount();
    }

    private record IngredientPlan(@Nullable ItemStack nextIngredient, int matchedCount, int missingCount) {
    }

    private record PotPlan(ResourceLocation recipeId, ItemStack result, IngredientPlan ingredientPlan,
                           int maxRepeatCount) implements RecipeCandidate {
    }

    private record StockpotPlan(ResourceLocation recipeId, ItemStack result, @Nullable ItemStack soupBaseStack,
                                IngredientPlan ingredientPlan, int maxRepeatCount) implements RecipeCandidate {
    }

    private record TeapotPlan(ResourceLocation recipeId, ItemStack result, @Nullable ItemStack teaFluidStack,
                              @Nullable ItemStack ingredientStack, int maxRepeatCount) implements RecipeCandidate {
    }

    private record ChoppingBoardPlan(ResourceLocation recipeId, ItemStack result, @Nullable ItemStack ingredientStack,
                                     int maxRepeatCount) implements RecipeCandidate {
    }

    private record SteamerPlan(ResourceLocation recipeId, ItemStack result, @Nullable ItemStack ingredientStack,
                               int maxRepeatCount) implements RecipeCandidate {
    }

    private record CookOption(ResourceLocation recipeId, ItemStack result, int maxRepeatCount) implements RecipeCandidate {
    }

    private interface StackAction {
        boolean apply(ItemStack stack);
    }

    private static final class SafeInvoker {
        private static final String TEAPOT_BLOCK_ENTITY_CLASS_NAME =
                "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity";
        @Nullable
        private static final Class<?> TEAPOT_BLOCK_ENTITY_CLASS = findOptionalClass(TEAPOT_BLOCK_ENTITY_CLASS_NAME);

        private SafeInvoker() {
        }

        static boolean isCookwareStation(Level level, BlockPos pos) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            return blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity
                    || blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity
                    || isTeapotBlockEntity(blockEntity)
                    || blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IChoppingBoard
                    || blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ISteamer;
        }

        static ItemStack getCookMainHandDisplay(Level level, BlockPos pos) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity) {
                return com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems.KITCHEN_SHOVEL.get().getDefaultInstance();
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity) {
                return new ItemStack(Items.WOODEN_SHOVEL);
            }
            if (isTeapotBlockEntity(blockEntity)) {
                return new ItemStack(Items.WOODEN_SHOVEL);
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IChoppingBoard) {
                return com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems.IRON_KITCHEN_KNIFE.get().getDefaultInstance();
            }
            return ItemStack.EMPTY;
        }

        static ItemStack getCookOffhandDisplay(Level level, BlockPos pos) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (isTeapotBlockEntity(blockEntity)) {
                return TeapotSupport.getCookOffhandDisplay();
            }
            return ItemStack.EMPTY;
        }

        static boolean tickCookware(HeroEntity hero, BlockPos pos) {
            if (!(hero.level() instanceof ServerLevel serverLevel)) {
                return false;
            }

            ServerPlayer owner = resolveOwner(hero);
            if (owner == null || owner.level() != serverLevel) {
                return false;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity pot) {
                return tickPot(hero, serverLevel, owner, pot);
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity stockpot) {
                return tickStockpot(hero, serverLevel, owner, stockpot);
            }
            if (isTeapotBlockEntity(blockEntity)) {
                return TeapotSupport.tickCookware(hero, serverLevel, owner, blockEntity);
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity choppingBoard) {
                return tickChoppingBoard(hero, serverLevel, owner, choppingBoard);
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ISteamer steamer) {
                return tickSteamer(hero, serverLevel, owner, steamer);
            }
            return false;
        }

        static Component cycleCookSelection(HeroEntity hero, BlockPos pos) {
            if (!(hero.level() instanceof ServerLevel serverLevel)) {
                return Component.translatable("message.herobrine_companion.invite_cook_none");
            }

            ServerPlayer owner = resolveOwner(hero);
            if (owner == null || owner.level() != serverLevel) {
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

        static List<CookOptionView> getCookOptions(HeroEntity hero, BlockPos pos) {
            if (!(hero.level() instanceof ServerLevel serverLevel)) {
                return List.of();
            }

            ServerPlayer owner = resolveOwner(hero);
            if (owner == null || owner.level() != serverLevel) {
                return List.of();
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
            List<CookOption> options = getCookOptions(serverLevel, owner, blockEntity);
            if (options.isEmpty()) {
                return List.of();
            }

            List<CookOptionView> result = new ArrayList<>(options.size());
            for (CookOption option : options) {
                result.add(new CookOptionView(option.recipeId(), option.result().copy(), option.maxRepeatCount()));
            }
            return result;
        }

        static List<ItemStack> getAutonomousCookResults(Level level, BlockPos pos) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return List.of();
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            List<ItemStack> results = new ArrayList<>();

            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity) {
                for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.PotRecipe recipe : serverLevel.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.POT_RECIPE)) {
                    addUniqueResult(results, recipe.getResultItem(serverLevel.registryAccess()).copy());
                }
                return results;
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity) {
                for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe recipe : serverLevel.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.STOCKPOT_RECIPE)) {
                    addUniqueResult(results, recipe.getResultItem(serverLevel.registryAccess()).copy());
                }
                return results;
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity) {
                for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe recipe : serverLevel.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.CHOPPING_BOARD_RECIPE)) {
                    addUniqueResult(results, recipe.getResult().copy());
                }
                return results;
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ISteamer) {
                for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.SteamerRecipe recipe : serverLevel.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.STEAMER_RECIPE)) {
                    addUniqueResult(results, recipe.getResult().copy());
                }
                return results;
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity) {
                for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.TeapotRecipe recipe : serverLevel.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.TEAPOT_RECIPE)) {
                    addUniqueResult(results, recipe.result().copy());
                }
                return results;
            }

            return List.of();
        }

        static List<AutonomousCookOption> getAutonomousCookOptions(Level level, BlockPos pos) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return List.of();
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            List<AutonomousCookOption> results = new ArrayList<>();

            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity) {
                for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.PotRecipe recipe : serverLevel.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.POT_RECIPE)) {
                    addAutonomousOption(results, recipe.getId(), recipe.getResultItem(serverLevel.registryAccess()).copy());
                }
                return results;
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity) {
                for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe recipe : serverLevel.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.STOCKPOT_RECIPE)) {
                    addAutonomousOption(results, recipe.getId(), recipe.getResultItem(serverLevel.registryAccess()).copy());
                }
                return results;
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity) {
                for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe recipe : serverLevel.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.CHOPPING_BOARD_RECIPE)) {
                    addAutonomousOption(results, recipe.getId(), recipe.getResult().copy());
                }
                return results;
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ISteamer) {
                for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.SteamerRecipe recipe : serverLevel.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.STEAMER_RECIPE)) {
                    addAutonomousOption(results, recipe.getId(), recipe.getResult().copy());
                }
                return results;
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity) {
                for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.TeapotRecipe recipe : serverLevel.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.TEAPOT_RECIPE)) {
                    addAutonomousOption(results, recipe.getId(), recipe.result().copy());
                }
                return results;
            }

            return List.of();
        }

        static CompoundTag captureAutonomousCookwareSnapshot(Level level, BlockPos pos) {
            CompoundTag snapshot = new CompoundTag();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                return snapshot;
            }

            snapshot.put("BlockEntityData", blockEntity.saveWithoutMetadata());
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock.HAS_LID)) {
                snapshot.putBoolean("StockpotHasLid", state.getValue(com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock.HAS_LID));
            }
            if (state.hasProperty(com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock.HAS_LID)) {
                snapshot.putBoolean("SteamerHasLid", state.getValue(com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock.HAS_LID));
            }
            return snapshot;
        }

        static boolean applyAutonomousCookwareDisplay(Level level, BlockPos pos, ResourceLocation recipeId, ItemStack result) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return false;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity pot) {
                return applyPotAutonomousDisplay(serverLevel, pot, result);
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity stockpot) {
                return applyStockpotAutonomousDisplay(serverLevel, stockpot, recipeId, result);
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity choppingBoard) {
                return applyChoppingBoardAutonomousDisplay(serverLevel, choppingBoard, recipeId);
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity steamer) {
                return applySteamerAutonomousDisplay(serverLevel, steamer, result);
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity teapot) {
                return TeapotSupport.applyAutonomousDisplay(serverLevel, teapot, recipeId, result);
            }
            return false;
        }

        static void restoreAutonomousCookwareSnapshot(Level level, BlockPos pos, CompoundTag snapshot) {
            if (snapshot.isEmpty()) {
                return;
            }

            BlockState state = level.getBlockState(pos);
            if (snapshot.contains("StockpotHasLid") && state.hasProperty(com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock.HAS_LID)) {
                level.setBlock(pos, state.setValue(
                        com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock.HAS_LID,
                        snapshot.getBoolean("StockpotHasLid")), 3);
                state = level.getBlockState(pos);
            }
            if (snapshot.contains("SteamerHasLid") && state.hasProperty(com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock.HAS_LID)) {
                level.setBlock(pos, state.setValue(
                        com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock.HAS_LID,
                        snapshot.getBoolean("SteamerHasLid")), 3);
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || !snapshot.contains("BlockEntityData")) {
                return;
            }

            blockEntity.load(snapshot.getCompound("BlockEntityData"));
            blockEntity.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
        }

        static boolean selectCookOption(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
            if (!(hero.level() instanceof ServerLevel serverLevel)) {
                return false;
            }

            ServerPlayer owner = resolveOwner(hero);
            if (owner == null || owner.level() != serverLevel) {
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
            if (owner == null || owner.level() != serverLevel) {
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
            if (hero.getOwnerUUID() == null) {
                return null;
            }
            return hero.level() instanceof ServerLevel serverLevel
                    ? serverLevel.getServer().getPlayerList().getPlayer(hero.getOwnerUUID())
                    : null;
        }

        private static List<CookOption> getCookOptions(ServerLevel level, ServerPlayer owner, @Nullable BlockEntity blockEntity) {
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity pot) {
                return mapPotOptions(collectPotPlans(level, owner, pot));
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity stockpot) {
                return mapStockpotOptions(collectStockpotPlans(level, owner, stockpot));
            }
            if (isTeapotBlockEntity(blockEntity)) {
                return TeapotSupport.getCookOptions(level, owner, blockEntity);
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity choppingBoard) {
                return mapChoppingBoardOptions(collectChoppingBoardPlans(level, owner, choppingBoard));
            }
            if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ISteamer steamer) {
                return mapSteamerOptions(collectSteamerPlans(level, owner, steamer));
            }
            return List.of();
        }

        private static List<CookOption> mapPotOptions(List<PotPlan> plans) {
            List<CookOption> options = new ArrayList<>(plans.size());
            for (PotPlan plan : plans) {
                options.add(new CookOption(plan.recipeId(), plan.result().copy(), plan.maxRepeatCount()));
            }
            return options;
        }

        private static List<CookOption> mapStockpotOptions(List<StockpotPlan> plans) {
            List<CookOption> options = new ArrayList<>(plans.size());
            for (StockpotPlan plan : plans) {
                options.add(new CookOption(plan.recipeId(), plan.result().copy(), plan.maxRepeatCount()));
            }
            return options;
        }

        private static List<CookOption> mapTeapotOptions(List<TeapotPlan> plans) {
            List<CookOption> options = new ArrayList<>(plans.size());
            for (TeapotPlan plan : plans) {
                options.add(new CookOption(plan.recipeId(), plan.result().copy(), plan.maxRepeatCount()));
            }
            return options;
        }

        private static List<CookOption> mapChoppingBoardOptions(List<ChoppingBoardPlan> plans) {
            List<CookOption> options = new ArrayList<>(plans.size());
            for (ChoppingBoardPlan plan : plans) {
                options.add(new CookOption(plan.recipeId(), plan.result().copy(), plan.maxRepeatCount()));
            }
            return options;
        }

        private static List<CookOption> mapSteamerOptions(List<SteamerPlan> plans) {
            List<CookOption> options = new ArrayList<>(plans.size());
            for (SteamerPlan plan : plans) {
                options.add(new CookOption(plan.recipeId(), plan.result().copy(), plan.maxRepeatCount()));
            }
            return options;
        }

        private static boolean tickPot(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                       com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity pot) {
            int status = pot.getStatus();
            if (status == com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IPot.FINISHED
                    || status == com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IPot.BURNT) {
                return tryTakeOutPot(hero, level, owner, pot);
            }

            if (status == com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IPot.COOKING) {
                if (level.getGameTime() % 20L == 0L) {
                    pot.onShovelHit(level, owner, ItemStack.EMPTY);
                    owner.getInventory().setChanged();
                    return true;
                }
                return false;
            }

            List<PotPlan> plans = collectPotPlans(level, owner, pot);
            if (plans.isEmpty()) {
                return false;
            }

            int selectedIndex = resolveSelectedRecipeIndex(hero, pot.getBlockPos(), plans);
            if (selectedIndex < 0) {
                return false;
            }

            PotPlan plan = plans.get(selectedIndex);
            if (pot.getCurrentTick() <= 0) {
                if (tryAccessibleStacks(owner, stack -> pot.onPlaceOil(level, owner, stack))) {
                    return true;
                }
                return false;
            }

            ItemStack nextIngredient = plan.ingredientPlan().nextIngredient();
            if (nextIngredient != null && !nextIngredient.isEmpty()) {
                if (pot.addIngredient(level, owner, nextIngredient)) {
                    owner.getInventory().setChanged();
                    return true;
                }
                return false;
            }

            if (level.getGameTime() % 20L == 0L) {
                pot.onShovelHit(level, owner, ItemStack.EMPTY);
                owner.getInventory().setChanged();
                return true;
            }
            return false;
        }

        private static boolean tickStockpot(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                            com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity stockpot) {
            int status = stockpot.getStatus();
            if (status == com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot.FINISHED) {
                if (removeStockpotLid(level, owner, stockpot)) {
                    return true;
                }
                return tryTakeOutStockpot(hero, level, owner, stockpot);
            }

            List<StockpotPlan> plans = collectStockpotPlans(level, owner, stockpot);
            if (plans.isEmpty()) {
                return false;
            }

            int selectedIndex = resolveSelectedRecipeIndex(hero, stockpot.getBlockPos(), plans);
            if (selectedIndex < 0) {
                return false;
            }

            StockpotPlan plan = plans.get(selectedIndex);
            if (status == com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot.PUT_SOUP_BASE) {
                ItemStack soupBaseStack = plan.soupBaseStack();
                if (soupBaseStack != null && !soupBaseStack.isEmpty() && stockpot.addSoupBase(level, owner, soupBaseStack)) {
                    owner.getInventory().setChanged();
                    return true;
                }
                return false;
            }

            if (status == com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot.PUT_INGREDIENT) {
                ItemStack nextIngredient = plan.ingredientPlan().nextIngredient();
                if (nextIngredient != null && !nextIngredient.isEmpty()) {
                    if (stockpot.addIngredient(level, owner, nextIngredient)) {
                        owner.getInventory().setChanged();
                        return true;
                    }
                    return false;
                }

                if (!stockpot.hasLid() && tryAccessibleStacks(owner, stack -> stockpot.onLitClick(level, owner, stack))) {
                    return true;
                }
            } else if (status == com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot.COOKING) {
                if (!stockpot.hasLid() && tryAccessibleStacks(owner, stack -> stockpot.onLitClick(level, owner, stack))) {
                    return true;
                }
            }

            return false;
        }

        private static boolean tickChoppingBoard(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                                 com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity choppingBoard) {
            List<ChoppingBoardPlan> plans = collectChoppingBoardPlans(level, owner, choppingBoard);
            if (plans.isEmpty()) {
                return false;
            }

            int selectedIndex = resolveSelectedRecipeIndex(hero, choppingBoard.getBlockPos(), plans);
            if (selectedIndex < 0) {
                return false;
            }

            ChoppingBoardPlan plan = plans.get(selectedIndex);
            if (choppingBoard.getCurrentCutStack().isEmpty()) {
                ItemStack ingredientStack = plan.ingredientStack();
                if (ingredientStack != null && !ingredientStack.isEmpty() && choppingBoard.onPutItem(level, owner, ingredientStack)) {
                    owner.getInventory().setChanged();
                    return true;
                }
                return false;
            }

            if (level.getGameTime() % 10L != 0L) {
                return false;
            }

            boolean completesRecipe = choppingBoard.getCurrentCutCount() >= choppingBoard.getMaxCutCount();
            Set<UUID> existingDropIds = completesRecipe
                    ? snapshotChoppingBoardDropIds(level, choppingBoard.getBlockPos(), plan.result())
                    : Set.of();
            if (choppingBoard.onCutItem(level, owner, getVirtualKitchenKnife())) {
                if (completesRecipe) {
                    deliverChoppingBoardResult(level, owner, choppingBoard.getBlockPos(), plan.result(), existingDropIds);
                    consumeCookRepeat(hero, choppingBoard.getBlockPos());
                }
                return true;
            }
            return false;
        }

        private static boolean tickSteamer(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                           com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ISteamer steamer) {
            if (steamer instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity steamerBlockEntity
                    && hasReadySteamerOutput(steamerBlockEntity)) {
                if (steamer.takeFood(level, owner)) {
                    owner.getInventory().setChanged();
                    consumeCookRepeat(hero, steamerBlockEntity.getBlockPos());
                    return true;
                }
                return false;
            }

            List<SteamerPlan> plans = collectSteamerPlans(level, owner, steamer);
            if (plans.isEmpty()) {
                return false;
            }

            BlockPos blockPos = steamer instanceof BlockEntity be ? be.getBlockPos() : hero.blockPosition();
            int selectedIndex = resolveSelectedRecipeIndex(hero, blockPos, plans);
            if (selectedIndex < 0) {
                return false;
            }

            SteamerPlan plan = plans.get(selectedIndex);
            ItemStack ingredientStack = plan.ingredientStack();
            if (ingredientStack != null && !ingredientStack.isEmpty() && steamer.placeFood(level, owner, ingredientStack)) {
                owner.getInventory().setChanged();
                if (steamer instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity steamerBlockEntity) {
                    setSteamerLid(level, steamerBlockEntity, true);
                }
                return true;
            }
            return false;
        }

        private static boolean hasReadySteamerOutput(com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity steamer) {
            List<ItemStack> items = steamer.getItems();
            int[] cookingTime = steamer.getCookingTime();
            for (int i = 0; i < items.size() && i < cookingTime.length; i++) {
                if (!items.get(i).isEmpty() && cookingTime[i] == -1) {
                    return true;
                }
            }
            return false;
        }

        private static boolean tryTakeOutPot(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                             com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IPot pot) {
            if (withTemporaryShift(owner, () -> pot.takeOutProduct(level, owner, ItemStack.EMPTY))) {
                owner.getInventory().setChanged();
                if (pot instanceof BlockEntity blockEntity) {
                    consumeCookRepeat(hero, blockEntity.getBlockPos());
                }
                return true;
            }
            boolean success = tryAccessibleStacks(owner, stack -> withTemporaryShift(owner, () -> pot.takeOutProduct(level, owner, stack)));
            if (success && pot instanceof BlockEntity blockEntity) {
                consumeCookRepeat(hero, blockEntity.getBlockPos());
            }
            return success;
        }

        private static boolean tryTakeOutStockpot(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                                  com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot stockpot) {
            if (stockpot.takeOutProduct(level, owner, ItemStack.EMPTY)) {
                owner.getInventory().setChanged();
                if (shouldConsumeStockpotRepeat(stockpot) && stockpot instanceof BlockEntity blockEntity) {
                    consumeCookRepeat(hero, blockEntity.getBlockPos());
                }
                return true;
            }
            boolean success = tryAccessibleStacks(owner, stack -> stockpot.takeOutProduct(level, owner, stack));
            if (success && shouldConsumeStockpotRepeat(stockpot) && stockpot instanceof BlockEntity blockEntity) {
                consumeCookRepeat(hero, blockEntity.getBlockPos());
            }
            return success;
        }

        private static boolean shouldConsumeStockpotRepeat(com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot stockpot) {
            if (stockpot instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity stockpotBlockEntity) {
                return stockpotBlockEntity.getTakeoutCount() <= 0
                        || stockpotBlockEntity.getStatus() != com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot.FINISHED;
            }
            return true;
        }

        private static ItemStack getVirtualKitchenKnife() {
            return com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems.IRON_KITCHEN_KNIFE.get().getDefaultInstance();
        }

        private static Set<UUID> snapshotChoppingBoardDropIds(ServerLevel level, BlockPos pos, ItemStack expectedResult) {
            Set<UUID> dropIds = new HashSet<>();
            if (expectedResult.isEmpty()) {
                return dropIds;
            }

            for (ItemEntity itemEntity : getChoppingBoardResultEntities(level, pos, expectedResult, 1.0D)) {
                dropIds.add(itemEntity.getUUID());
            }
            return dropIds;
        }

        private static void deliverChoppingBoardResult(ServerLevel level, ServerPlayer owner, BlockPos pos, ItemStack expectedResult,
                                                       Set<UUID> existingDropIds) {
            if (expectedResult.isEmpty()) {
                return;
            }

            int remaining = expectedResult.getCount();
            Vec3 boardCenter = Vec3.atCenterOf(pos);
            List<ItemEntity> newDrops = new ArrayList<>();
            for (ItemEntity itemEntity : getChoppingBoardResultEntities(level, pos, expectedResult, 1.0D)) {
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
                net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(owner, stack.copyWithCount(movedCount));
                remaining -= movedCount;
                if (movedCount >= stack.getCount()) {
                    itemEntity.discard();
                } else {
                    stack.shrink(movedCount);
                    itemEntity.setItem(stack);
                }
            }

            if (remaining > 0) {
                net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(owner, expectedResult.copyWithCount(remaining));
            }
            owner.getInventory().setChanged();
        }

        private static List<ItemEntity> getChoppingBoardResultEntities(ServerLevel level, BlockPos pos, ItemStack expectedResult, double radius) {
            AABB searchBox = new AABB(pos).inflate(radius);
            return level.getEntitiesOfClass(ItemEntity.class, searchBox, itemEntity ->
                    itemEntity.tickCount <= 2 && ItemStack.isSameItemSameTags(itemEntity.getItem(), expectedResult));
        }

        private static boolean removeStockpotLid(ServerLevel level, ServerPlayer owner,
                                                 com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity stockpot) {
            if (!stockpot.hasLid()) {
                return false;
            }

            BlockPos pos = stockpot.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock.HAS_LID)) {
                return false;
            }

            ItemStack lidItem = stockpot.getLidItem().isEmpty()
                    ? com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems.STOCKPOT_LID.get().getDefaultInstance()
                    : stockpot.getLidItem().copy();
            stockpot.setLidItem(ItemStack.EMPTY);
            stockpot.setChanged();
            level.setBlockAndUpdate(pos, state.setValue(
                    com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock.HAS_LID, false));
            if (!owner.getInventory().add(lidItem)) {
                owner.drop(lidItem, false);
            }
            owner.getInventory().setChanged();
            return true;
        }

        private static void setSteamerLid(ServerLevel level,
                                          com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity steamer,
                                          boolean hasLid) {
            BlockPos pos = steamer.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock.HAS_LID)) {
                return;
            }
            if (state.getValue(com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock.HAS_LID) == hasLid) {
                return;
            }
            level.setBlock(pos, state.setValue(
                    com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock.HAS_LID, hasLid), 3);
        }

        private static int computeIngredientCraftCount(List<Ingredient> rawIngredients, List<ItemStack> currentInputs,
                                                       List<ItemStack> availableStacks) {
            List<Ingredient> ingredients = normalizeIngredients(rawIngredients);
            if (ingredients.isEmpty()) {
                return MAX_COOK_REPEAT_COUNT;
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
            while (craftCount < MAX_COOK_REPEAT_COUNT && consumeIngredientGroup(ingredients, availableStacks, counts)) {
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

        private static int countSoupBaseItems(ResourceLocation soupBaseId, List<ItemStack> availableStacks) {
            com.github.ysbbbbbb.kaleidoscopecookery.api.recipe.soupbase.ISoupBase soupBase =
                    com.github.ysbbbbbb.kaleidoscopecookery.crafting.soupbase.SoupBaseManager.getSoupBase(soupBaseId);
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

        private static int countMatchingItems(Ingredient ingredient, List<ItemStack> availableStacks) {
            int total = 0;
            for (ItemStack stack : availableStacks) {
                if (!stack.isEmpty() && ingredient.test(stack)) {
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

        private static List<PotPlan> collectPotPlans(ServerLevel level, ServerPlayer owner,
                                                     com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity pot) {
            List<ItemStack> currentInputs = collectNonEmptyStacks(pot.getInputs());
            List<ItemStack> availableStacks = collectAccessibleStacks(owner);
            List<PotPlan> plans = new ArrayList<>();

            for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.PotRecipe recipe : level.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.POT_RECIPE)) {
                IngredientPlan ingredientPlan = buildIngredientPlan(recipe.ingredients(), currentInputs, availableStacks);
                int maxRepeatCount = computeIngredientCraftCount(recipe.ingredients(), currentInputs, availableStacks);
                if (ingredientPlan != null && maxRepeatCount > 0) {
                    plans.add(new PotPlan(recipe.getId(), recipe.getResultItem(level.registryAccess()).copy(),
                            ingredientPlan, maxRepeatCount));
                }
            }

            plans.sort(Comparator.comparing(plan -> plan.recipeId().toString()));
            return plans;
        }

        private static List<StockpotPlan> collectStockpotPlans(ServerLevel level, ServerPlayer owner,
                                                               com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity stockpot) {
            List<ItemStack> currentInputs = collectNonEmptyStacks(stockpot.getInputs());
            List<ItemStack> availableStacks = collectAccessibleStacks(owner);
            ResourceLocation selectedSoupBase = stockpot.getStatus() == com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot.PUT_SOUP_BASE
                    ? null
                    : stockpot.getSoupBaseId();
            List<StockpotPlan> plans = new ArrayList<>();

            for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe recipe : level.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.STOCKPOT_RECIPE)) {
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

                IngredientPlan ingredientPlan = buildIngredientPlan(recipe.ingredients(), currentInputs, availableStacks);
                int ingredientRepeatCount = computeIngredientCraftCount(recipe.ingredients(), currentInputs, availableStacks);
                int soupBaseRepeatLimit = selectedSoupBase == null
                        ? countSoupBaseItems(recipe.soupBase(), availableStacks)
                        : countSoupBaseItems(recipe.soupBase(), availableStacks) + 1;
                int maxRepeatCount = Math.min(ingredientRepeatCount, soupBaseRepeatLimit);
                if (ingredientPlan != null && maxRepeatCount > 0) {
                    plans.add(new StockpotPlan(recipe.getId(), recipe.getResultItem(level.registryAccess()).copy(),
                            soupBaseStack.isEmpty() ? null : soupBaseStack, ingredientPlan, maxRepeatCount));
                }
            }

            plans.sort(Comparator.comparing(plan -> plan.recipeId().toString()));
            return plans;
        }

        private static List<ChoppingBoardPlan> collectChoppingBoardPlans(ServerLevel level, ServerPlayer owner,
                                                                         com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity choppingBoard) {
            List<ItemStack> availableStacks = collectAccessibleStacks(owner);
            List<ChoppingBoardPlan> plans = new ArrayList<>();
            ItemStack currentCutStack = choppingBoard.getCurrentCutStack();
            boolean hasCurrentIngredient = !currentCutStack.isEmpty();
            ResourceLocation currentModelId = choppingBoard.getModelId();
            int currentMaxCutCount = choppingBoard.getMaxCutCount();

            for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe recipe : level.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.CHOPPING_BOARD_RECIPE)) {
                int maxRepeatCount;
                ItemStack ingredientStack = ItemStack.EMPTY;
                if (hasCurrentIngredient) {
                    if (currentModelId == null
                            || currentMaxCutCount != recipe.getCutCount()
                            || !recipe.getModelId().equals(currentModelId)
                            || !recipe.getIngredient().test(currentCutStack)) {
                        continue;
                    }
                    maxRepeatCount = Math.min(MAX_COOK_REPEAT_COUNT, countMatchingItems(recipe.getIngredient(), availableStacks) + 1);
                } else {
                    ingredientStack = findMatchingStack(recipe.getIngredient(), 1, availableStacks);
                    if (ingredientStack.isEmpty() || !isPlaceableChoppingBoardRecipe(level, ingredientStack, recipe.getId())) {
                        continue;
                    }
                    maxRepeatCount = Math.min(MAX_COOK_REPEAT_COUNT, countMatchingItems(recipe.getIngredient(), availableStacks));
                }

                if (maxRepeatCount <= 0) {
                    continue;
                }

                plans.add(new ChoppingBoardPlan(recipe.getId(), recipe.getResult().copy(),
                        ingredientStack.isEmpty() ? null : ingredientStack, maxRepeatCount));
            }

            plans.sort(Comparator.comparing(plan -> plan.recipeId().toString()));
            return plans;
        }

        private static List<SteamerPlan> collectSteamerPlans(ServerLevel level, ServerPlayer owner,
                                                             com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ISteamer steamer) {
            List<ItemStack> availableStacks = collectAccessibleStacks(owner);
            List<SteamerPlan> plans = new ArrayList<>();

            for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.SteamerRecipe recipe : level.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.STEAMER_RECIPE)) {
                ItemStack ingredientStack = findMatchingStack(recipe.getIngredient(), 1, availableStacks);
                int maxRepeatCount = Math.min(MAX_COOK_REPEAT_COUNT, countMatchingItems(recipe.getIngredient(), availableStacks));
                if (!ingredientStack.isEmpty() && maxRepeatCount > 0) {
                    plans.add(new SteamerPlan(recipe.getId(), recipe.getResult().copy(), ingredientStack, maxRepeatCount));
                }
            }

            plans.sort(Comparator.comparing(plan -> plan.recipeId().toString()));
            return plans;
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

        private static ItemStack findSoupBaseStack(ResourceLocation soupBaseId, List<ItemStack> availableStacks) {
            com.github.ysbbbbbb.kaleidoscopecookery.api.recipe.soupbase.ISoupBase soupBase =
                    com.github.ysbbbbbb.kaleidoscopecookery.crafting.soupbase.SoupBaseManager.getSoupBase(soupBaseId);
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

        private static ItemStack findTeaFluidStack(ResourceLocation teaFluidId, List<ItemStack> availableStacks) {
            for (ItemStack stack : availableStacks) {
                if (stack.isEmpty()) {
                    continue;
                }
                if (containsFluid(stack, teaFluidId)) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        private static boolean containsFluid(ItemStack stack, ResourceLocation fluidId) {
            return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).map(handler -> {
                net.minecraftforge.fluids.FluidStack contained = handler.getFluidInTank(0);
                if (contained.isEmpty() || contained.getAmount() < 1000) {
                    return false;
                }
                ResourceLocation containedFluidId = ForgeRegistries.FLUIDS.getKey(contained.getFluid());
                return fluidId.equals(containedFluidId);
            }).orElse(false);
        }

        private static boolean isPlaceableChoppingBoardRecipe(ServerLevel level, ItemStack ingredientStack, ResourceLocation recipeId) {
            net.minecraft.world.SimpleContainer container = new net.minecraft.world.SimpleContainer(ingredientStack.copyWithCount(1));
            return level.getRecipeManager()
                    .getRecipeFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.CHOPPING_BOARD_RECIPE, container, level)
                    .map(recipe -> recipe.getId().equals(recipeId))
                    .orElse(false);
        }

        private static ItemStack findMatchingStack(Ingredient ingredient, int minCount, List<ItemStack> availableStacks) {
            for (ItemStack stack : availableStacks) {
                if (!stack.isEmpty() && stack.getCount() >= minCount && ingredient.test(stack)) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        private static List<ItemStack> collectNonEmptyStacks(List<ItemStack> stacks) {
            List<ItemStack> result = new ArrayList<>();
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    result.add(stack);
                }
            }
            return result;
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

        private static void addAutonomousOption(List<AutonomousCookOption> results, ResourceLocation recipeId, ItemStack candidate) {
            if (recipeId == null || candidate.isEmpty()) {
                return;
            }
            results.add(new AutonomousCookOption(recipeId, candidate.copy()));
        }

        private static boolean applyPotAutonomousDisplay(ServerLevel level,
                                                         com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity pot,
                                                         ItemStack result) {
            boolean updated = setFieldValue(pot, "result", result.copy())
                    | setFieldValue(pot, "status", com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IPot.FINISHED)
                    | setFieldValue(pot, "currentTick", 0);
            return markCookwareUpdated(level, pot.getBlockPos(), pot, updated);
        }

        private static boolean applyStockpotAutonomousDisplay(ServerLevel level,
                                                              com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity stockpot,
                                                              ResourceLocation recipeId, ItemStack result) {
            com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe recipe = findStockpotRecipe(level, recipeId);
            boolean updated = false;
            updated |= setFieldValue(stockpot, "recipeId", recipeId);
            if (recipe != null) {
                stockpot.recipe = recipe;
                updated |= setFieldValue(stockpot, "soupBaseId", recipe.soupBase());
            }
            updated |= setFieldValue(stockpot, "result", result.copy());
            updated |= setFieldValue(stockpot, "status", com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot.FINISHED);
            updated |= setFieldValue(stockpot, "currentTick", 0);
            updated |= setFieldValue(stockpot, "takeoutCount", Math.max(1, result.getCount()));
            updated |= setFieldValue(stockpot, "lidItem", ItemStack.EMPTY);

            BlockState state = level.getBlockState(stockpot.getBlockPos());
            if (state.hasProperty(com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock.HAS_LID)
                    && state.getValue(com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock.HAS_LID)) {
                level.setBlock(stockpot.getBlockPos(), state.setValue(
                        com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock.HAS_LID, false), 3);
                updated = true;
            }
            return markCookwareUpdated(level, stockpot.getBlockPos(), stockpot, updated);
        }

        private static boolean applyChoppingBoardAutonomousDisplay(ServerLevel level,
                                                                   com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity choppingBoard,
                                                                   ResourceLocation recipeId) {
            com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe recipe = findChoppingBoardRecipe(level, recipeId);
            if (recipe == null) {
                return false;
            }

            ItemStack displayStack = getFirstIngredientExample(recipe.getIngredient());
            boolean updated = setFieldValue(choppingBoard, "modelId", recipe.getModelId())
                    | setFieldValue(choppingBoard, "maxCutCount", recipe.getCutCount())
                    | setFieldValue(choppingBoard, "currentCutCount", Math.max(0, recipe.getCutCount() - 1))
                    | setFieldValue(choppingBoard, "currentCutStack", displayStack)
                    | setFieldValue(choppingBoard, "result", recipe.getResult().copy());
            return markCookwareUpdated(level, choppingBoard.getBlockPos(), choppingBoard, updated);
        }

        private static boolean applySteamerAutonomousDisplay(ServerLevel level,
                                                             com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity steamer,
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
        private static com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe findStockpotRecipe(ServerLevel level,
                                                                                                                  ResourceLocation recipeId) {
            for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe recipe : level.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.STOCKPOT_RECIPE)) {
                if (recipe.getId().equals(recipeId)) {
                    return recipe;
                }
            }
            return null;
        }

        @Nullable
        private static com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe findChoppingBoardRecipe(ServerLevel level,
                                                                                                                            ResourceLocation recipeId) {
            for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe recipe : level.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.CHOPPING_BOARD_RECIPE)) {
                if (recipe.getId().equals(recipeId)) {
                    return recipe;
                }
            }
            return null;
        }

        private static ItemStack getFirstIngredientExample(Ingredient ingredient) {
            ItemStack[] examples = ingredient.getItems();
            if (examples.length <= 0) {
                return ItemStack.EMPTY;
            }
            return examples[0].copyWithCount(1);
        }

        private static boolean markCookwareUpdated(Level level, BlockPos pos, BlockEntity blockEntity, boolean updated) {
            if (!updated) {
                return false;
            }
            blockEntity.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            return true;
        }

        private static boolean setFieldValue(Object target, String fieldName, Object value) {
            Field field = findField(target.getClass(), fieldName);
            if (field == null) {
                return false;
            }
            try {
                field.setAccessible(true);
                field.set(target, value);
                return true;
            } catch (IllegalAccessException ignored) {
                return false;
            }
        }

        @Nullable
        private static Field findField(Class<?> type, String fieldName) {
            Class<?> current = type;
            while (current != null) {
                try {
                    return current.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            return null;
        }

        private static void addUniqueResult(List<ItemStack> results, ItemStack candidate) {
            if (candidate.isEmpty()) {
                return;
            }
            for (ItemStack existing : results) {
                if (ItemStack.isSameItemSameTags(existing, candidate)) {
                    return;
                }
            }
            results.add(candidate);
        }

        @Nullable
        private static Class<?> findOptionalClass(String className) {
            try {
                return Class.forName(className, false, HeroKaleidoscopeCompat.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError ignored) {
                return null;
            }
        }

        private static boolean isTeapotBlockEntity(@Nullable BlockEntity blockEntity) {
            return blockEntity != null && TEAPOT_BLOCK_ENTITY_CLASS != null && TEAPOT_BLOCK_ENTITY_CLASS.isInstance(blockEntity);
        }

        private static final class TeapotSupport {
            @Nullable
            private static final ResourceLocation EMPTY_TEA_FLUID = resolveEmptyTeaFluid();

            private TeapotSupport() {
            }

            static ItemStack getCookOffhandDisplay() {
                return com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems.EMPTY_CUP.get().getDefaultInstance();
            }

            static boolean tickCookware(HeroEntity hero, ServerLevel level, ServerPlayer owner, BlockEntity blockEntity) {
                if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity teapot) {
                    return tickTeapot(hero, level, owner, teapot);
                }
                return false;
            }

            static List<CookOption> getCookOptions(ServerLevel level, ServerPlayer owner, BlockEntity blockEntity) {
                if (blockEntity instanceof com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity teapot) {
                    return mapTeapotOptions(collectTeapotPlans(owner, teapot, level));
                }
                return List.of();
            }

            static boolean applyAutonomousDisplay(ServerLevel level,
                                                  com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity teapot,
                                                  ResourceLocation recipeId, ItemStack result) {
                com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.TeapotRecipe selectedRecipe = null;
                for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.TeapotRecipe recipe : level.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.TEAPOT_RECIPE)) {
                    if (recipe.getId().equals(recipeId)) {
                        selectedRecipe = recipe;
                        break;
                    }
                }
                if (selectedRecipe == null) {
                    return false;
                }

                boolean updated = setFieldValue(teapot, "teaFluidId", selectedRecipe.teaFluid())
                        | setFieldValue(teapot, "result", result.copy())
                        | setFieldValue(teapot, "status", com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ITeapot.FINISHED)
                        | setFieldValue(teapot, "currentTick", 0)
                        | setFieldValue(teapot, "input", ItemStack.EMPTY);
                return markCookwareUpdated(level, teapot.getBlockPos(), teapot, updated);
            }

            @Nullable
            private static ResourceLocation resolveEmptyTeaFluid() {
                try {
                    Class<?> serializerClass = Class.forName(
                            "com.github.ysbbbbbb.kaleidoscopecookery.crafting.serializer.TeapotRecipeSerializer",
                            false,
                            HeroKaleidoscopeCompat.class.getClassLoader());
                    Object value = serializerClass.getField("EMPTY_TEA_FLUID").get(null);
                    return value instanceof ResourceLocation resourceLocation ? resourceLocation : null;
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    return null;
                }
            }

            private static boolean needsTeaFluid(@Nullable ResourceLocation teaFluidId) {
                if (teaFluidId == null) {
                    return true;
                }
                if (EMPTY_TEA_FLUID != null && EMPTY_TEA_FLUID.equals(teaFluidId)) {
                    return true;
                }
                String path = teaFluidId.getPath();
                return path == null || path.isEmpty() || "empty".equals(path);
            }

            private static boolean tickTeapot(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                              com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity teapot) {
                int status = teapot.getStatus();
                if (status == com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ITeapot.FINISHED) {
                    if (teapot.takeTeapot(level, owner)) {
                        owner.getInventory().setChanged();
                        consumeCookRepeat(hero, teapot.getBlockPos());
                        return true;
                    }
                    return false;
                }

                if (status == com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ITeapot.PROCESSING) {
                    return false;
                }

                List<TeapotPlan> plans = collectTeapotPlans(owner, teapot, level);
                if (plans.isEmpty()) {
                    return false;
                }

                int selectedIndex = resolveSelectedRecipeIndex(hero, teapot.getBlockPos(), plans);
                if (selectedIndex < 0) {
                    return false;
                }

                TeapotPlan plan = plans.get(selectedIndex);
                if (needsTeaFluid(teapot.getTeaFluidId())) {
                    ItemStack teaFluidStack = plan.teaFluidStack();
                    if (teaFluidStack != null && !teaFluidStack.isEmpty() && teapot.addTeaFluid(level, owner, teaFluidStack)) {
                        owner.getInventory().setChanged();
                        return true;
                    }
                    return false;
                }

                if (teapot.getInput().isEmpty()) {
                    ItemStack ingredientStack = plan.ingredientStack();
                    if (ingredientStack != null && !ingredientStack.isEmpty() && teapot.addIngredient(level, owner, ingredientStack)) {
                        owner.getInventory().setChanged();
                        return true;
                    }
                }
                return false;
            }

            private static List<TeapotPlan> collectTeapotPlans(ServerPlayer owner,
                                                               com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity teapot,
                                                               ServerLevel level) {
                List<ItemStack> availableStacks = collectAccessibleStacks(owner);
                List<TeapotPlan> plans = new ArrayList<>();
                ResourceLocation currentTeaFluid = teapot.getTeaFluidId();
                boolean needsTeaFluid = needsTeaFluid(currentTeaFluid);
                ItemStack currentInput = teapot.getInput();

                for (com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.TeapotRecipe recipe : level.getRecipeManager().getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes.TEAPOT_RECIPE)) {
                    if (!needsTeaFluid && !recipe.teaFluid().equals(currentTeaFluid)) {
                        continue;
                    }

                    ItemStack teaFluidStack = ItemStack.EMPTY;
                    if (needsTeaFluid) {
                        teaFluidStack = findTeaFluidStack(recipe.teaFluid(), availableStacks);
                        if (teaFluidStack.isEmpty()) {
                            continue;
                        }
                    }

                    ItemStack ingredientStack = ItemStack.EMPTY;
                    if (!currentInput.isEmpty()) {
                        if (!recipe.ingredient().test(currentInput) || currentInput.getCount() < recipe.ingredientCount()) {
                            continue;
                        }
                    } else {
                        ingredientStack = findMatchingStack(recipe.ingredient(), recipe.ingredientCount(), availableStacks);
                        if (ingredientStack.isEmpty()) {
                            continue;
                        }
                    }

                    plans.add(new TeapotPlan(recipe.getId(), recipe.result().copy(),
                            teaFluidStack.isEmpty() ? null : teaFluidStack,
                            ingredientStack.isEmpty() ? null : ingredientStack, 1));
                }

                plans.sort(Comparator.comparing(plan -> plan.recipeId().toString()));
                return plans;
            }
        }

        private static boolean tryAccessibleStacks(ServerPlayer owner, StackAction action) {
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

        private static boolean withTemporaryShift(ServerPlayer owner, BooleanSupplier action) {
            boolean wasShiftDown = owner.isShiftKeyDown();
            if (!wasShiftDown) {
                owner.setShiftKeyDown(true);
            }
            try {
                return action.getAsBoolean();
            } finally {
                if (!wasShiftDown) {
                    owner.setShiftKeyDown(false);
                }
            }
        }
    }
}
