package com.whitecloud233.herobrine_companion.destructiongod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

record RestorationRecord(ServerLevel level, BlockPos pos, BlockState state, long restoreAt) {
}

