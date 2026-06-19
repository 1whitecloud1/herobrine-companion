package com.whitecloud233.modid.herobrine_companion.init;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.block.entity.EndRingPortalBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HerobrineCompanion.MODID);

    public static final RegistryObject<BlockEntityType<EndRingPortalBlockEntity>> END_RING_PORTAL_BE = BLOCK_ENTITY_TYPES.register("end_ring_portal", () -> BlockEntityType.Builder.of(EndRingPortalBlockEntity::new, ModBlocks.END_RING_PORTAL.get()).build(null));
}
