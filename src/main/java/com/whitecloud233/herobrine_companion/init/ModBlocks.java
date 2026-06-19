package com.whitecloud233.herobrine_companion.init;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.block.EndRingPortalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HerobrineCompanion.MODID);

    public static final DeferredHolder<Block, EndRingPortalBlock> END_RING_PORTAL = BLOCKS.register("end_ring_portal", 
            () -> new EndRingPortalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).noCollission().strength(-1.0F, 3600000.8F).noLootTable()));
}
