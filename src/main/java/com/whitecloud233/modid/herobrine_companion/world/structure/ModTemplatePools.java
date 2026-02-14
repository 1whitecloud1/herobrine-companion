package com.whitecloud233.modid.herobrine_companion.world.structure;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class ModTemplatePools {
    public static final ResourceKey<StructureTemplatePool> UNSTABLE_ZONE_POOL = ResourceKey.create(Registries.TEMPLATE_POOL, new ResourceLocation(HerobrineCompanion.MODID, "unstable_zone/start_pool"));

    public static void bootstrap(BootstapContext<StructureTemplatePool> context) {
        context.register(UNSTABLE_ZONE_POOL, new StructureTemplatePool(
                context.lookup(Registries.TEMPLATE_POOL).getOrThrow(Pools.EMPTY),
                java.util.List.of(),
                StructureTemplatePool.Projection.RIGID
        ));
    }
}
