package com.whitecloud233.herobrine_companion.datagen;

import com.whitecloud233.herobrine_companion.world.structure.EndRingStructure;
import com.whitecloud233.herobrine_companion.world.structure.UnstableZoneStructure;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RegistriesDatapackGenerator extends DatapackBuiltinEntriesProvider {

    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.STRUCTURE, RegistriesDatapackGenerator::bootstrapStructures)
            .add(Registries.STRUCTURE_SET, RegistriesDatapackGenerator::bootstrapStructureSets)
            .add(Registries.BIOME, RegistriesDatapackGenerator::bootstrapBiomes)
            .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, ModBiomeModifiers::bootstrap);

    public RegistriesDatapackGenerator(PackOutput output, CompletableFuture<net.minecraft.core.HolderLookup.Provider> registries) {
        super(output, registries, BUILDER, Set.of(HerobrineCompanion.MODID));
    }

    private static void bootstrapStructures(BootstrapContext<Structure> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);

        // Register End Ring Structure
        context.register(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "end_ring")),
                new EndRingStructure(
                        new Structure.StructureSettings(
                                biomes.getOrThrow(BiomeTags.IS_END), // Allowed biomes
                                Map.of(), // Spawn overrides
                                GenerationStep.Decoration.SURFACE_STRUCTURES,
                                TerrainAdjustment.NONE
                        )
                )
        );

        // Register Unstable Zone Structure
        context.register(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "unstable_zone")),
                new UnstableZoneStructure(
                        new Structure.StructureSettings(
                                biomes.getOrThrow(BiomeTags.IS_OVERWORLD), // Allowed biomes
                                Map.of(), // Spawn overrides
                                GenerationStep.Decoration.SURFACE_STRUCTURES,
                                TerrainAdjustment.BEARD_THIN
                        )
                )
        );
    }

    private static void bootstrapStructureSets(BootstrapContext<StructureSet> context) {
        HolderGetter<Structure> structures = context.lookup(Registries.STRUCTURE);

        // End Ring Structure Set
        context.register(ResourceKey.create(Registries.STRUCTURE_SET, ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "end_ring")),
                new StructureSet(
                        structures.getOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "end_ring"))),
                        new RandomSpreadStructurePlacement(
                                32, // Spacing
                                8, // Separation
                                RandomSpreadType.LINEAR,
                                14357619 // Salt
                        )
                )
        );

        // Unstable Zone Structure Set
        context.register(ResourceKey.create(Registries.STRUCTURE_SET, ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "unstable_zone")),
                new StructureSet(
                        structures.getOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "unstable_zone"))),
                        new RandomSpreadStructurePlacement(
                                40, // Spacing
                                20, // Separation
                                RandomSpreadType.LINEAR,
                                16432345 // Salt
                        )
                )
        );
    }

    private static void bootstrapBiomes(BootstrapContext<Biome> context) {
        // Register Past World Plains Biome (Alpha Void)
        // Alpha grass color: #B7CB62
        // Alpha sky color: #99CCFF
        
        MobSpawnSettings.Builder spawnBuilder = new MobSpawnSettings.Builder();
        
        BiomeGenerationSettings.Builder biomeBuilder = new BiomeGenerationSettings.Builder(context.lookup(Registries.PLACED_FEATURE), context.lookup(Registries.CONFIGURED_CARVER));
        
        // Add basic trees (Oak and Birch only)
        biomeBuilder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.TREES_PLAINS);
        
        // Add flowers (Poppy and Dandelion)
        biomeBuilder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.FLOWER_PLAINS);
        
        context.register(ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "alpha_void")),
                new Biome.BiomeBuilder()
                        .hasPrecipitation(true)
                        .temperature(0.8F)
                        .downfall(0.4F)
                        .specialEffects(new BiomeSpecialEffects.Builder()
                                .waterColor(0x3F76E4)
                                .waterFogColor(0x050533)
                                .fogColor(0xC0D8FF)
                                .skyColor(0x99CCFF) // Alpha-like sky
                                .grassColorOverride(0xB7CB62) // Alpha-like bright green grass
                                .foliageColorOverride(0xB7CB62) // Matching foliage
                                .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                                .build())
                        .mobSpawnSettings(spawnBuilder.build())
                        .generationSettings(biomeBuilder.build())
                        .build()
        );
    }
}