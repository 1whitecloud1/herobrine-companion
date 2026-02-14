package com.whitecloud233.herobrine_companion.world.structure;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, HerobrineCompanion.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<EndRingStructure>> END_RING = STRUCTURE_TYPES.register("end_ring", () -> () -> EndRingStructure.CODEC);
    
    // Fix: StructureType is a functional interface returning MapCodec. 
    // The outer lambda is the Supplier for DeferredRegister.
    // The inner lambda is the implementation of StructureType.
    public static final DeferredHolder<StructureType<?>, StructureType<UnstableZoneStructure>> UNSTABLE_ZONE = STRUCTURE_TYPES.register("unstable_zone", () -> () -> UnstableZoneStructure.CODEC);
    
    public static final ResourceKey<Level> END_RING_DIMENSION_KEY = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "end_ring_dimension"));

    public static final ResourceKey<Structure> UNSTABLE_ZONE_KEY = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "unstable_zone"));
}
