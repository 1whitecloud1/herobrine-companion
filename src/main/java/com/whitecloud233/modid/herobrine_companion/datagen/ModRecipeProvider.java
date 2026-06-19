package com.whitecloud233.modid.herobrine_companion.datagen;

import com.whitecloud233.modid.herobrine_companion.init.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.crafting.conditions.IConditionBuilder;

import java.util.function.Consumer;

public class ModRecipeProvider extends RecipeProvider implements IConditionBuilder {
    public ModRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.RECALL_STONE.get())
                .pattern("GEG")
                .pattern("CMC")
                .pattern("GTG")
                .define('G', ModItems.GLITCH_FRAGMENT.get())
                .define('E', Items.ENDER_PEARL)
                .define('C', ModItems.CORRUPTED_CODE.get())
                .define('M', ModItems.MEMORY_SHARD.get())
                .define('T', Items.TOTEM_OF_UNDYING)
                .unlockedBy("has_glitch_fragment", has(ModItems.GLITCH_FRAGMENT.get()))
                .save(consumer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.HERO_SHELTER.get())
                .pattern("GTG")
                .pattern("DPD")
                .pattern("GEG")
                .define('G', Items.GOLD_INGOT)
                .define('T', Items.TOTEM_OF_UNDYING)
                .define('D', Items.DIAMOND)
                .define('P', Items.PAPER)
                .define('E', Items.EXPERIENCE_BOTTLE)
                .unlockedBy("has_totem_of_undying", has(Items.TOTEM_OF_UNDYING))
                .save(consumer);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.SOURCE_FLOW.get())
                .pattern("ESE")
                .pattern("SGS")
                .pattern("ESE")
                .define('E', Items.ENDER_EYE) // 末影之眼：维持空间与维度的坐标指引
                .define('S', ModItems.SOURCE_CODE_FRAGMENT.get()) // 源代码碎片：创世权柄的核心
                .define('G', Items.GHAST_TEAR) // 恶魂之泪：替换下界之星，提供纯白的驱动能量
                .unlockedBy("has_source_code_fragment", has(ModItems.SOURCE_CODE_FRAGMENT.get()))
                .save(consumer);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.AWAKENED_VESSEL.get())
                .pattern("GEG")
                .pattern("MCM")
                .pattern("GSG")
                .define('G', ModItems.GLITCH_FRAGMENT.get())
                .define('E', Items.ENDER_EYE)
                .define('M', ModItems.MEMORY_SHARD.get())
                .define('C', Items.CRYING_OBSIDIAN)
                .define('S', ModItems.SOURCE_CODE_FRAGMENT.get())
                .unlockedBy("has_memory_shard", has(ModItems.MEMORY_SHARD.get()))
                .save(consumer);
    }
}
