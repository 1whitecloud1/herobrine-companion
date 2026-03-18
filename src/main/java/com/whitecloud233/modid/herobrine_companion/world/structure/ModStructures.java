package com.whitecloud233.modid.herobrine_companion.world.structure;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;

@SuppressWarnings("removal")
public class ModStructures {

    // ================= 1. 结构类型注册 (StructureType) =================
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, HerobrineCompanion.MODID);

    public static final RegistryObject<StructureType<EndRingStructure>> END_RING = STRUCTURE_TYPES.register("end_ring", () -> () -> EndRingStructure.CODEC);
    public static final RegistryObject<StructureType<UnstableZoneStructure>> UNSTABLE_ZONE = STRUCTURE_TYPES.register("unstable_zone", () -> () -> UnstableZoneStructure.CODEC);

    // ================= 2. 资源键定义 (Resource Keys) =================
    // 维度 Key
    public static final ResourceKey<Level> END_RING_DIMENSION_KEY = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(HerobrineCompanion.MODID, "end_ring_dimension"));

    // 结构配置 (Structure) Key
    public static final ResourceKey<Structure> UNSTABLE_ZONE_KEY = ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(HerobrineCompanion.MODID, "unstable_zone"));
    public static final ResourceKey<Structure> END_RING_KEY = ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(HerobrineCompanion.MODID, "end_ring"));

    // 结构集分布 (StructureSet) Key
    public static final ResourceKey<StructureSet> UNSTABLE_ZONE_SET_KEY = ResourceKey.create(Registries.STRUCTURE_SET, new ResourceLocation(HerobrineCompanion.MODID, "unstable_zones"));
    public static final ResourceKey<StructureSet> END_RING_SET_KEY = ResourceKey.create(Registries.STRUCTURE_SET, new ResourceLocation(HerobrineCompanion.MODID, "end_rings"));


    // ================= 3. 数据生成：配置结构 (Datagen - Structure) =================
    public static void bootstrapStructures(BootstapContext<Structure> context) {
        HolderGetter<net.minecraft.world.level.biome.Biome> biomes = context.lookup(Registries.BIOME);

        context.register(UNSTABLE_ZONE_KEY, new UnstableZoneStructure(
                new Structure.StructureSettings(
                        biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
                        Map.of(),
                        GenerationStep.Decoration.SURFACE_STRUCTURES,
                        TerrainAdjustment.BEARD_THIN
                )
        ));

        context.register(END_RING_KEY, new EndRingStructure(
                new Structure.StructureSettings(
                        HolderSet.direct(biomes.getOrThrow(Biomes.THE_VOID)),
                        Map.of(),
                        GenerationStep.Decoration.SURFACE_STRUCTURES,
                        TerrainAdjustment.NONE
                )
        ));
    }

    // ================= 4. 数据生成：结构在地图上的分布 (Datagen - StructureSet) =================
    public static void bootstrapSets(BootstapContext<StructureSet> context) {
        HolderGetter<Structure> structureGetter = context.lookup(Registries.STRUCTURE);

        // 注册 Unstable Zone 的生成分布
        context.register(UNSTABLE_ZONE_SET_KEY, new StructureSet(
                structureGetter.getOrThrow(UNSTABLE_ZONE_KEY),
                new RandomSpreadStructurePlacement(
                        32,
                        8,
                        RandomSpreadType.LINEAR,
                        12345678
                )
        ));

        // 注册 End Ring 的生成分布 (从 ModStructureSets 搬过来的)
        context.register(END_RING_SET_KEY, new StructureSet(
                structureGetter.getOrThrow(END_RING_KEY),
                new RandomSpreadStructurePlacement(
                        32,
                        8,
                        RandomSpreadType.LINEAR,
                        87654321
                )
        ));
    }
}