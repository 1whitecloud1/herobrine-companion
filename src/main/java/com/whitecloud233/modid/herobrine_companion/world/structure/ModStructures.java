package com.whitecloud233.modid.herobrine_companion.world.structure;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;
import java.util.Optional;

public class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, HerobrineCompanion.MODID);

    public static final RegistryObject<StructureType<EndRingStructure>> END_RING = STRUCTURE_TYPES.register("end_ring", () -> () -> EndRingStructure.CODEC);
    
    public static final RegistryObject<StructureType<UnstableZoneStructure>> UNSTABLE_ZONE = STRUCTURE_TYPES.register("unstable_zone", () -> () -> UnstableZoneStructure.CODEC);
    
    public static final ResourceKey<Level> END_RING_DIMENSION_KEY = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(HerobrineCompanion.MODID, "end_ring_dimension"));

    public static final ResourceKey<Structure> UNSTABLE_ZONE_KEY = ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(HerobrineCompanion.MODID, "unstable_zone"));
    public static final ResourceKey<StructureSet> UNSTABLE_ZONE_SET_KEY = ResourceKey.create(Registries.STRUCTURE_SET, new ResourceLocation(HerobrineCompanion.MODID, "unstable_zones"));

    public static void bootstrap(BootstapContext<Structure> context) {
        HolderGetter<Biome> biomeGetter = context.lookup(Registries.BIOME);
        HolderGetter<StructureTemplatePool> templatePoolGetter = context.lookup(Registries.TEMPLATE_POOL);

        context.register(UNSTABLE_ZONE_KEY, new UnstableZoneStructure(
                new Structure.StructureSettings(
                        biomeGetter.getOrThrow(BiomeTags.IS_OVERWORLD),
                        Map.of(),
                        GenerationStep.Decoration.SURFACE_STRUCTURES,
                        TerrainAdjustment.BEARD_THIN
                ),
                templatePoolGetter.getOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL, new ResourceLocation(HerobrineCompanion.MODID, "unstable_zone/start_pool"))),
                Optional.empty(),
                1,
                ConstantHeight.of(net.minecraft.world.level.levelgen.VerticalAnchor.absolute(0)),
                Optional.of(Heightmap.Types.WORLD_SURFACE_WG),
                80
        ));
    }

    public static void bootstrapSets(BootstapContext<StructureSet> context) {
        HolderGetter<Structure> structureGetter = context.lookup(Registries.STRUCTURE);

        context.register(UNSTABLE_ZONE_SET_KEY, new StructureSet(
                structureGetter.getOrThrow(UNSTABLE_ZONE_KEY),
                new RandomSpreadStructurePlacement(
                        32,
                        8,
                        RandomSpreadType.LINEAR,
                        12345678
                )
        ));
    }
}
