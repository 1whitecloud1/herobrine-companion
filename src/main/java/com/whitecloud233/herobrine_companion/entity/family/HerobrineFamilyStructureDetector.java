package com.whitecloud233.herobrine_companion.entity.family;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class HerobrineFamilyStructureDetector {
    private static final int SEARCH_RADIUS_XZ = 6;
    private static final int SEARCH_RADIUS_Y = 3;
    private static final int REQUIRED_OPEN_HEIGHT = 12;

    private HerobrineFamilyStructureDetector() {
    }

    public static HerobrineFamilySummonStructure findNearbyStructure(ServerLevel level, BlockPos origin) {
        for (int y = -SEARCH_RADIUS_Y; y <= SEARCH_RADIUS_Y; y++) {
            for (int x = -SEARCH_RADIUS_XZ; x <= SEARCH_RADIUS_XZ; x++) {
                for (int z = -SEARCH_RADIUS_XZ; z <= SEARCH_RADIUS_XZ; z++) {
                    BlockPos center = origin.offset(x, y, z);
                    HerobrineFamilySummonStructure structure = detectAt(level, HerobrineFamilyMemberType.SIMMONS, center);
                    if (structure != null) {
                        return structure;
                    }

                    structure = detectAt(level, HerobrineFamilyMemberType.JEAN, center);
                    if (structure != null) {
                        return structure;
                    }
                }
            }
        }
        return null;
    }

    public static HerobrineFamilySummonStructure detectAt(ServerLevel level, HerobrineFamilyMemberType type, BlockPos center) {
        if (type == HerobrineFamilyMemberType.SIMMONS) {
            return detectSimmons(level, center);
        }
        if (type == HerobrineFamilyMemberType.JEAN) {
            return detectJean(level, center);
        }
        return null;
    }

    private static HerobrineFamilySummonStructure detectSimmons(ServerLevel level, BlockPos center) {
        if (level.dimension() != Level.OVERWORLD && level.dimension() != Level.NETHER) {
            return null;
        }

        if (!level.getBlockState(center).is(Blocks.SOUL_SAND)) {
            return null;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!level.getBlockState(center.relative(direction)).is(Blocks.CRYING_OBSIDIAN)) {
                return null;
            }
        }

        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                BlockPos campfirePos = center.offset(x, 0, z);
                BlockState state = level.getBlockState(campfirePos);
                if (!state.is(Blocks.SOUL_CAMPFIRE)
                        || !state.hasProperty(BlockStateProperties.LIT)
                        || !state.getValue(BlockStateProperties.LIT)) {
                    return null;
                }
            }
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pedestalPos = center.relative(direction, 2);
            if (!level.getBlockState(pedestalPos).is(Blocks.NETHERRACK)) {
                return null;
            }
        }

        if (!hasClearVerticalSpace(level, center.above(), REQUIRED_OPEN_HEIGHT)) {
            return null;
        }

        for (Direction standDirection : Direction.Plane.HORIZONTAL) {
            List<BlockPos> skulls = new ArrayList<>();
            boolean hasSkulls = true;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (direction == standDirection) {
                    continue;
                }
                BlockPos skullPos = center.relative(direction, 2).above();
                if (!level.getBlockState(skullPos).is(Blocks.WITHER_SKELETON_SKULL)) {
                    hasSkulls = false;
                    break;
                }
                skulls.add(skullPos);
            }

            if (!hasSkulls) {
                continue;
            }

            List<BlockPos> consumeBlocks = new ArrayList<>(skulls);
            consumeBlocks.add(center.offset(1, 0, 1));
            consumeBlocks.add(center.offset(1, 0, -1));
            consumeBlocks.add(center.offset(-1, 0, 1));
            consumeBlocks.add(center.offset(-1, 0, -1));

            return new HerobrineFamilySummonStructure(
                    HerobrineFamilyMemberType.SIMMONS,
                    center.immutable(),
                    center.relative(standDirection, 3).immutable(),
                    consumeBlocks,
                    List.of()
            );
        }

        return null;
    }

    private static HerobrineFamilySummonStructure detectJean(ServerLevel level, BlockPos center) {
        if (level.dimension() != Level.END) {
            return null;
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos floorPos = center.offset(x, 0, z);
                if (x == 0 && z == 0) {
                    if (!level.getBlockState(floorPos).is(Blocks.PURPUR_BLOCK)) {
                        return null;
                    }
                } else if (!level.getBlockState(floorPos).is(Blocks.OBSIDIAN)) {
                    return null;
                }
            }
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!level.getBlockState(center.relative(direction, 2)).is(Blocks.END_STONE)) {
                return null;
            }
        }

        int[] offsets = {-2, 2};
        for (int x : offsets) {
            for (int z : offsets) {
                BlockPos pillarBase = center.offset(x, 0, z);
                for (int y = 0; y < 3; y++) {
                    if (!level.getBlockState(pillarBase.above(y)).is(Blocks.CRYING_OBSIDIAN)) {
                        return null;
                    }
                }
                if (!level.getBlockState(pillarBase.above(3)).is(Blocks.END_ROD)) {
                    return null;
                }
            }
        }

        List<BlockPos> crystalAnchors = List.of(
                center.offset(3, 0, 3),
                center.offset(3, 0, -3),
                center.offset(-3, 0, 3),
                center.offset(-3, 0, -3)
        );
        for (BlockPos anchor : crystalAnchors) {
            if (!level.getBlockState(anchor).is(Blocks.BEDROCK) || !hasEndCrystal(level, anchor)) {
                return null;
            }
        }

        if (!hasClearVerticalSpace(level, center.above(), REQUIRED_OPEN_HEIGHT + 6)) {
            return null;
        }

        List<BlockPos> consumeBlocks = List.of(
                center.offset(2, 3, 2),
                center.offset(2, 3, -2),
                center.offset(-2, 3, 2),
                center.offset(-2, 3, -2)
        );

        return new HerobrineFamilySummonStructure(
                HerobrineFamilyMemberType.JEAN,
                center.immutable(),
                center.south(4).immutable(),
                consumeBlocks,
                crystalAnchors
        );
    }

    private static boolean hasClearVerticalSpace(ServerLevel level, BlockPos start, int height) {
        for (int i = 0; i < height; i++) {
            if (!level.getBlockState(start.above(i)).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasEndCrystal(ServerLevel level, BlockPos anchor) {
        AABB searchBox = new AABB(
                anchor.getX() - 0.25D,
                anchor.getY(),
                anchor.getZ() - 0.25D,
                anchor.getX() + 1.25D,
                anchor.getY() + 2.75D,
                anchor.getZ() + 1.25D
        );
        return !level.getEntitiesOfClass(EndCrystal.class, searchBox, crystal -> crystal.isAlive()).isEmpty();
    }
}
