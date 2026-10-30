package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingRules;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

public final class HeroInvitationHelper {
    /** 无邀请。逻辑侧清除邀请统一写这个常量，别再写字面量 0。 */
    public static final int ACTION_NONE = CookingRules.ACTION_NONE;
    public static final int ACTION_INSPECT = 1;
    public static final int ACTION_REST = 2;
    public static final int ACTION_GUARD = 3;
    public static final int ACTION_COOK = CookingService.INVITED_ACTION_COOK;
    public static final int ACTION_MILLSTONE = CookingService.INVITED_ACTION_MILLSTONE;

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

        if (CookingService.isCookwareStation(level, pos)) {
            return ACTION_COOK;
        }

        // 石磨不是锅具，必须排在做饭判定之后：它会先被做成「厨具站点」再被排除。
        if (CookingService.isMillstoneStation(level, pos)) {
            return ACTION_MILLSTONE;
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

    public static boolean isMillstoneTarget(Level level, BlockPos pos) {
        return getInteractionType(level, pos) == ACTION_MILLSTONE;
    }

    /** 厨具类邀请（做饭、拉磨）都靠石磨/锅具自身状态维持，取消时要一起释放。 */
    public static boolean isStationAction(int action) {
        return action == ACTION_COOK || action == ACTION_MILLSTONE;
    }

    /**
     * 座位上方净空检查：坐姿模型向上延伸约 1.5 格，座位方块上方两格内若有碰撞体
     * （屋檐、楼梯下沿、吊灯等），坐下去模型会插进方块。随机选座时要求上方连续
     * 两格无碰撞，宁缺毋滥；地毯、火把、花草等无碰撞方块不受影响。
     */
    public static boolean hasRestHeadroom(Level level, BlockPos seatPos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(seatPos.getX(), seatPos.getY(), seatPos.getZ());
        for (int i = 0; i < 2; i++) {
            cursor.move(Direction.UP);
            BlockState state = level.getBlockState(cursor);
            if (!state.getCollisionShape(level, cursor).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
