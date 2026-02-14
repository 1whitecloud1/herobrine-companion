package com.whitecloud233.herobrine_companion.datagen;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DataGenerators {

    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        generator.addProvider(event.includeServer(), new LootTableProvider(packOutput, Set.of(), List.of(
                new LootTableProvider.SubProviderEntry(ModEntityLootTables::new, LootContextParamSets.ENTITY)
        ), lookupProvider));

        generator.addProvider(event.includeServer(), new ModRecipeProvider(packOutput, lookupProvider));
        
        // Register Language Providers
        generator.addProvider(event.includeClient(), new ModEnUsLangProvider(packOutput));
        generator.addProvider(event.includeClient(), new ModZhCnLangProvider(packOutput));

        // Register Item Model Provider
        generator.addProvider(event.includeClient(), new ModItemModelProvider(packOutput, event.getExistingFileHelper()));

        // Register Registries Datapack Generator
        generator.addProvider(event.includeServer(), new RegistriesDatapackGenerator(packOutput, lookupProvider));

        // Register Item Tags Provider
        // Note: ItemTagsProvider requires a BlockTagsProvider, even if we don't have custom block tags yet.
        // We can use a dummy one or create a ModBlockTagsProvider if needed.
        // For now, let's create a simple BlockTagsProvider instance.
        BlockTagsProvider blockTagsProvider = new BlockTagsProvider(packOutput, lookupProvider, HerobrineCompanion.MODID, event.getExistingFileHelper()) {
            @Override
            protected void addTags(HolderLookup.Provider pProvider) {
                // Add block tags here if needed
            }
        };
        generator.addProvider(event.includeServer(), blockTagsProvider);
        generator.addProvider(event.includeServer(), new ModItemTagsProvider(packOutput, lookupProvider, blockTagsProvider.contentsGetter(), event.getExistingFileHelper()));

        // Register Advancement Provider
        generator.addProvider(event.includeServer(), new AdvancementProvider(packOutput, lookupProvider, event.getExistingFileHelper(), List.of(new ModAdvancementGenerator())));
    }
}
