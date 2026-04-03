package com.whitecloud233.herobrine_companion.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class EndRingRestorer {

    public static void restoreArena(ServerLevel level) {
        int centerX = 0;
        int centerY = 100;
        int centerZ = 0;

        int[][] rings = { {50, 60}, {80, 90}, {110, 120} };
        int thickness = 3;

        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState portal = Blocks.END_PORTAL.defaultBlockState();
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int x = centerX - 150; x <= centerX + 150; x++) {
            for (int z = centerZ - 150; z <= centerZ + 150; z++) {
                double distSq = (x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ);
                double dist = Math.sqrt(distSq);
                boolean isRing = false;
                for (int[] ring : rings) {
                    if (dist >= ring[0] && dist <= ring[1]) { isRing = true; break; }
                }

                if (isRing) {
                    for (int y = centerY; y < centerY + thickness; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!level.getBlockState(pos).is(Blocks.BEDROCK)) {
                            // 【修复】：全改为 2
                            level.setBlock(pos, bedrock, 2);
                        }
                    }
                } else if (dist < 50) {
                    BlockPos portalPos = new BlockPos(x, centerY, z);
                    BlockPos barrierPos = new BlockPos(x, centerY + 1, z);
                    if (!level.getBlockState(portalPos).is(Blocks.END_PORTAL)) {
                        level.setBlock(portalPos, portal, 2); // 【修复】：2
                    }
                    if (!level.getBlockState(barrierPos).is(Blocks.BARRIER)) {
                        level.setBlock(barrierPos, barrier, 2); // 【修复】：2
                    }
                } else {
                    for (int y = centerY; y < centerY + thickness; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, air, 2); // 【修复】：2
                        }
                    }
                }
            }
        }
    }
}