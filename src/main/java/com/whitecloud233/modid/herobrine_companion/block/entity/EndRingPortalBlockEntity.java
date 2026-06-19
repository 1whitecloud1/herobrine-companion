package com.whitecloud233.modid.herobrine_companion.block.entity;

import com.whitecloud233.modid.herobrine_companion.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class EndRingPortalBlockEntity extends BlockEntity {
    public EndRingPortalBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.END_RING_PORTAL_BE.get(), pos, blockState);
    }
}
