package com.whitecloud233.herobrine_companion.datagen;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, HerobrineCompanion.RECALL_STONE.get())
                .pattern("GEG")
                .pattern("CMC")
                .pattern("GTG")
                .define('G', HerobrineCompanion.GLITCH_FRAGMENT.get())
                .define('E', Items.ENDER_PEARL)
                .define('C', HerobrineCompanion.CORRUPTED_CODE.get())
                .define('M', HerobrineCompanion.MEMORY_SHARD.get())
                .define('T', Items.TOTEM_OF_UNDYING)
                .unlockedBy("has_glitch_fragment", has(HerobrineCompanion.GLITCH_FRAGMENT.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, HerobrineCompanion.HERO_SHELTER.get())
                .pattern("GTG")
                .pattern("DPD")
                .pattern("GEG")
                .define('G', Items.GOLD_INGOT)
                .define('T', Items.TOTEM_OF_UNDYING)
                .define('D', Items.DIAMOND)
                .define('P', Items.PAPER)
                .define('E', Items.EXPERIENCE_BOTTLE)
                .unlockedBy("has_totem", has(Items.TOTEM_OF_UNDYING))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, HerobrineCompanion.SOURCE_FLOW.get())
                .pattern("ESE")
                .pattern("SGS")
                .pattern("ESE")
                .define('E', Items.ENDER_EYE) // 末影之眼：维持空间与维度的坐标指引
                .define('S', HerobrineCompanion.SOURCE_CODE_FRAGMENT.get()) // 源代码碎片：创世权柄的核心
                .define('G', Items.GHAST_TEAR) // 恶魂之泪：替换下界之星，提供纯白的驱动能量
                .unlockedBy("has_source_code_fragment", has(HerobrineCompanion.SOURCE_CODE_FRAGMENT.get()))
                .save(output);
    }
}
