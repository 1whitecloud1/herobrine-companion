package com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ITeapot;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.TeapotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope.HeroKaleidoscopeCompat.AutonomousCookOption;
import com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope.KitchenRecipes.BackendCookOption;
import com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope.KitchenRecipes.TeapotPlan;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Keeps optional teapot recipes and interactions behind a lazy class boundary. */
final class TeapotKitchenStation {
    private TeapotKitchenStation() {
    }

    private static final String TEAPOT_BLOCK_ENTITY_CLASS_NAME =
            "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity";

    @Nullable
    private static final Class<?> TEAPOT_BLOCK_ENTITY_CLASS = KitchenReflection.findOptionalClass(TEAPOT_BLOCK_ENTITY_CLASS_NAME);

    static boolean isTeapotBlockEntity(@Nullable BlockEntity blockEntity) {
        return blockEntity != null && TEAPOT_BLOCK_ENTITY_CLASS != null && TEAPOT_BLOCK_ENTITY_CLASS.isInstance(blockEntity);
    }

    static ItemStack getCookOffhandDisplay() {
        return TEAPOT_BLOCK_ENTITY_CLASS == null ? ItemStack.EMPTY : Available.getCookOffhandDisplay();
    }

    static boolean tickCookware(HeroEntity hero, ServerLevel level, ServerPlayer owner, BlockEntity blockEntity) {
        return isTeapotBlockEntity(blockEntity) && Available.tickCookware(hero, level, owner, blockEntity);
    }

    static List<BackendCookOption> getCookOptions(ServerLevel level, ServerPlayer owner, BlockEntity blockEntity) {
        return isTeapotBlockEntity(blockEntity) ? Available.getCookOptions(level, owner, blockEntity) : List.of();
    }

    static boolean applyAutonomousDisplay(ServerLevel level, BlockEntity blockEntity, ResourceLocation recipeId, ItemStack result) {
        return isTeapotBlockEntity(blockEntity) && Available.applyAutonomousDisplay(level, blockEntity, recipeId, result);
    }

    static List<AutonomousCookOption> getAutonomousCookOptions(ServerLevel level) {
        return TEAPOT_BLOCK_ENTITY_CLASS == null ? List.of() : Available.getAutonomousCookOptions(level);
    }

    // This holder is initialized only after the optional teapot class is present.
    private static final class Available {
        @Nullable
        private static final ResourceLocation EMPTY_TEA_FLUID = resolveEmptyTeaFluid();

        static ItemStack getCookOffhandDisplay() {
            return ModItems.EMPTY_CUP.get().getDefaultInstance();
        }

        static boolean tickCookware(HeroEntity hero, ServerLevel level, ServerPlayer owner, BlockEntity blockEntity) {
            if (blockEntity instanceof TeapotBlockEntity teapot) {
                return tickTeapot(hero, level, owner, teapot);
            }
            return false;
        }

        static List<BackendCookOption> getCookOptions(ServerLevel level, ServerPlayer owner, BlockEntity blockEntity) {
            if (blockEntity instanceof TeapotBlockEntity teapot) {
                return mapTeapotOptions(collectTeapotPlans(owner, teapot, level));
            }
            return List.of();
        }

        static boolean applyAutonomousDisplay(ServerLevel level,
                                              BlockEntity blockEntity,
                                              ResourceLocation recipeId, ItemStack result) {
            if (!(blockEntity instanceof TeapotBlockEntity teapot)) {
                return false;
            }
            TeapotRecipe selectedRecipe = null;
            for (TeapotRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.TEAPOT_RECIPE)) {
                if (recipe.getId().equals(recipeId)) {
                    selectedRecipe = recipe;
                    break;
                }
            }
            if (selectedRecipe == null) {
                return false;
            }

            boolean updated = KitchenReflection.setFieldValue(teapot, "teaFluidId", selectedRecipe.teaFluid())
                    | KitchenReflection.setFieldValue(teapot, "result", result.copy())
                    | KitchenReflection.setFieldValue(teapot, "status", ITeapot.FINISHED)
                    | KitchenReflection.setFieldValue(teapot, "currentTick", 0)
                    | KitchenReflection.setFieldValue(teapot, "input", ItemStack.EMPTY);
            return KitchenAutonomousDisplay.markCookwareUpdated(level, teapot.getBlockPos(), teapot, updated);
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
                                          TeapotBlockEntity teapot) {
            int status = teapot.getStatus();
            if (status == ITeapot.FINISHED) {
                if (teapot.takeTeapot(level, hero)) {
                    owner.getInventory().setChanged();
                    KitchenSession.consumeCookRepeat(hero, teapot.getBlockPos());
                    return true;
                }
                return false;
            }

            if (status == ITeapot.PROCESSING) {
                return false;
            }

            List<TeapotPlan> plans = collectTeapotPlans(owner, teapot, level);
            int selectedIndex = KitchenSession.resolveSelectedRecipeIndex(hero, teapot.getBlockPos(), plans);
            if (selectedIndex < 0) {
                if (plans.isEmpty()) {
                    return false;
                }
                // 选中项漂移：候选已按壶内茶汤 / 已有食材过滤，取首条继续，不要永久放弃。
                KitchenDiagnostics.LOGGER.debug("[CookDebug] teapot 选中项失效，改用首条方案 plans={}", plans.size());
                selectedIndex = 0;
            }

            TeapotPlan plan = plans.get(selectedIndex);
            if (needsTeaFluid(teapot.getTeaFluidId())) {
                ItemStack teaFluidStack = plan.teaFluidStack();
                if (teaFluidStack != null && !teaFluidStack.isEmpty() && teapot.addTeaFluid(level, hero, teaFluidStack)) {
                    owner.getInventory().setChanged();
                    return true;
                }
                return false;
            }

            if (teapot.getInput().isEmpty()) {
                ItemStack ingredientStack = plan.ingredientStack();
                if (ingredientStack != null && !ingredientStack.isEmpty() && teapot.addIngredient(level, hero, ingredientStack)) {
                    owner.getInventory().setChanged();
                    return true;
                }
            }
            return false;
        }

        private static List<TeapotPlan> collectTeapotPlans(ServerPlayer owner,
                                                           TeapotBlockEntity teapot,
                                                           ServerLevel level) {
            List<ItemStack> availableStacks = KitchenRecipes.collectAccessibleStacks(owner);
            List<TeapotPlan> plans = new ArrayList<>();
            ResourceLocation currentTeaFluid = teapot.getTeaFluidId();
            boolean needsTeaFluid = needsTeaFluid(currentTeaFluid);
            ItemStack currentInput = teapot.getInput();

            for (TeapotRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.TEAPOT_RECIPE)) {
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
                    ingredientStack = KitchenRecipes.findMatchingStack(recipe.ingredient(), recipe.ingredientCount(), availableStacks);
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

        static List<BackendCookOption> mapTeapotOptions(List<TeapotPlan> plans) {
            List<BackendCookOption> options = new ArrayList<>(plans.size());
            for (TeapotPlan plan : plans) {
                options.add(new BackendCookOption(plan.recipeId(), plan.result().copy(), plan.maxRepeatCount()));
            }
            return options;
        }

        private static ItemStack findTeaFluidStack(ResourceLocation teaFluidId, List<ItemStack> availableStacks) {
            for (ItemStack stack : availableStacks) {
                if (stack.isEmpty()) {
                    continue;
                }
                if (KitchenRecipes.containsFluid(stack, teaFluidId)) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        static List<AutonomousCookOption> getAutonomousCookOptions(ServerLevel level) {
            List<AutonomousCookOption> results = new ArrayList<>();
            for (TeapotRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.TEAPOT_RECIPE)) {
                KitchenRecipeCatalog.addAutonomousOption(results, recipe.getId(), recipe.result().copy());
            }
            return results;
        }
    }
}
