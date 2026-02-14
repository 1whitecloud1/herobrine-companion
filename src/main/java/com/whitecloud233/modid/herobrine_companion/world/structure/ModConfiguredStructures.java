package com.whitecloud233.modid.herobrine_companion.world.structure;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.biome.Biomes;

import java.util.Map;
import java.util.Optional;

public class ModConfiguredStructures {
    public static final ResourceKey<Structure> UNSTABLE_ZONE_KEY = ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(HerobrineCompanion.MODID, "unstable_zone"));
    public static final ResourceKey<Structure> END_RING_KEY = ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(HerobrineCompanion.MODID, "end_ring"));

    public static void bootstrap(BootstapContext<Structure> context) {
        HolderGetter<StructureTemplatePool> templatePools = context.lookup(Registries.TEMPLATE_POOL);
        HolderGetter<net.minecraft.world.level.biome.Biome> biomes = context.lookup(Registries.BIOME);

        context.register(UNSTABLE_ZONE_KEY, new UnstableZoneStructure(
                new Structure.StructureSettings(
                        biomes.getOrThrow(BiomeTags.IS_OVERWORLD), 
                        Map.of(), 
                        GenerationStep.Decoration.SURFACE_STRUCTURES,
                        TerrainAdjustment.BEARD_THIN
                ),
                templatePools.getOrThrow(ModTemplatePools.UNSTABLE_ZONE_POOL), 
                Optional.empty(), 
                1, 
                ConstantHeight.of(VerticalAnchor.absolute(0)), 
                Optional.of(Heightmap.Types.WORLD_SURFACE_WG),
                80 
        ));

        context.register(END_RING_KEY, new EndRingStructure(
                new Structure.StructureSettings(
                        // 修改为 THE_VOID，匹配虚空维度的生物群系
                        HolderSet.direct(biomes.getOrThrow(Biomes.THE_VOID)),
                        Map.of(),
                        GenerationStep.Decoration.SURFACE_STRUCTURES,
                        TerrainAdjustment.NONE
                )
        ));
    }
}
