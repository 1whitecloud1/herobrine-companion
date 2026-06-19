package com.whitecloud233.modid.herobrine_companion.init;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.block.EndRingPortalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HerobrineCompanion.MODID);

    public static final RegistryObject<EndRingPortalBlock> END_RING_PORTAL = BLOCKS.register("end_ring_portal", () -> new EndRingPortalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).noCollission().strength(-1.0F, 3600000.8F).noLootTable()));
}
