package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.compat.cooking.HeroCookingCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

public final class HeroInvitationHelper {
    public static final int ACTION_NONE = 0;
    public static final int ACTION_INSPECT = 1;
    public static final int ACTION_REST = 2;
    public static final int ACTION_GUARD = 3;
    public static final int ACTION_COOK = HeroCookingCompat.INVITED_ACTION_COOK;

    private static final TagKey<Block> FORGE_CHAIRS = BlockTags.create(new ResourceLocation("forge", "chairs"));
    private static final TagKey<Block> COMMON_CHAIRS = BlockTags.create(new ResourceLocation("c", "chairs"));

    private HeroInvitationHelper() {
    }

    public static int getInteractionType(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return ACTION_NONE;
        }

        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (HeroCookingCompat.isCookwareStation(level, pos)) {
            return ACTION_COOK;
        }

        if (state.is(BlockTags.BEDS) || block instanceof BedBlock) {
            return ACTION_REST;
        }
        if (state.is(BlockTags.STAIRS) || block instanceof StairBlock) {
            return ACTION_REST;
        }
        if (state.is(BlockTags.SLABS) || block instanceof SlabBlock) {
            return ACTION_REST;
        }
        if (state.is(FORGE_CHAIRS) || state.is(COMMON_CHAIRS)) {
            return ACTION_REST;
        }

        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        if (key != null) {
            String path = key.getPath().toLowerCase();
            if (path.contains("chair") || path.contains("seat") || path.contains("sofa")
                    || path.contains("stool") || path.contains("bench")) {
                return ACTION_REST;
            }
        }

        if (state.is(BlockTags.DOORS) || block instanceof DoorBlock) {
            return ACTION_GUARD;
        }
        if (state.is(BlockTags.TRAPDOORS) || block instanceof TrapDoorBlock) {
            return ACTION_GUARD;
        }
        if (state.is(BlockTags.FENCE_GATES) || block instanceof FenceGateBlock) {
            return ACTION_GUARD;
        }
        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.ENDER_CHEST)
                || state.is(Blocks.BARREL) || state.is(Blocks.SHULKER_BOX)) {
            return ACTION_GUARD;
        }

        if (state.is(Blocks.SPAWNER) || state.is(Blocks.ENCHANTING_TABLE) || state.is(Blocks.BEACON)
                || state.is(Blocks.COMMAND_BLOCK) || state.is(Blocks.CHAIN_COMMAND_BLOCK)
                || state.is(Blocks.REPEATING_COMMAND_BLOCK) || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES) || state.is(BlockTags.GOLD_ORES)
                || state.is(Blocks.ANCIENT_DEBRIS)) {
            return ACTION_INSPECT;
        }

        return ACTION_NONE;
    }

    public static boolean isRestTarget(Level level, BlockPos pos) {
        return getInteractionType(level, pos) == ACTION_REST;
    }

    public static boolean isCookTarget(Level level, BlockPos pos) {
        return getInteractionType(level, pos) == ACTION_COOK;
    }
}
