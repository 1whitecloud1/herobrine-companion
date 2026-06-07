package com.whitecloud233.herobrine_companion.entity.family;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public record HerobrineFamilySummonStructure(
        HerobrineFamilyMemberType memberType,
        BlockPos center,
        BlockPos heroStandPos,
        List<BlockPos> consumeBlocks,
        List<BlockPos> crystalAnchors
) {
    public HerobrineFamilySummonStructure {
        consumeBlocks = List.copyOf(consumeBlocks);
        crystalAnchors = List.copyOf(crystalAnchors);
    }

    public Vec3 spawnPos() {
        if (memberType == HerobrineFamilyMemberType.JEAN) {
            return new Vec3(center.getX() + 0.5D, center.getY() + 8.0D, center.getZ() + 0.5D);
        }

        return new Vec3(center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D);
    }
}
