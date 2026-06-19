package com.whitecloud233.herobrine_companion.init;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.block.entity.EndRingPortalBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HerobrineCompanion.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EndRingPortalBlockEntity>> END_RING_PORTAL_BE = 
            BLOCK_ENTITY_TYPES.register("end_ring_portal", 
                    () -> BlockEntityType.Builder.of(EndRingPortalBlockEntity::new, ModBlocks.END_RING_PORTAL.get()).build(null));
}
