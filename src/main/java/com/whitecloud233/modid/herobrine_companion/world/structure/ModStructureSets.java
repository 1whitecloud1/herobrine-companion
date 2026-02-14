package com.whitecloud233.modid.herobrine_companion.world.structure;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

public class ModStructureSets {
    public static final ResourceKey<StructureSet> UNSTABLE_ZONE_SET = ResourceKey.create(Registries.STRUCTURE_SET, new ResourceLocation(HerobrineCompanion.MODID, "unstable_zones"));
    public static final ResourceKey<StructureSet> END_RING_SET = ResourceKey.create(Registries.STRUCTURE_SET, new ResourceLocation(HerobrineCompanion.MODID, "end_rings"));

    public static void bootstrap(BootstapContext<StructureSet> context) {
        var structures = context.lookup(Registries.STRUCTURE);

        context.register(UNSTABLE_ZONE_SET, new StructureSet(
                structures.getOrThrow(ModConfiguredStructures.UNSTABLE_ZONE_KEY),
                new RandomSpreadStructurePlacement(
                        32, 
                        8,  
                        RandomSpreadType.LINEAR,
                        12345678 
                )
        ));

        context.register(END_RING_SET, new StructureSet(
                structures.getOrThrow(ModConfiguredStructures.END_RING_KEY),
                new RandomSpreadStructurePlacement(
                        32, 8, RandomSpreadType.LINEAR, 87654321
                )
        ));
    }
}
