package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WorldAnomalyEventHandler {
    // 创建一个白名单，记录当前 tick 玩家正在合法破坏的方块
    public static final Set<BlockPos> PLAYER_BROKEN_BLOCKS = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onPlayerBreak(BlockEvent.BreakEvent event) {
        // 如果配置文件中禁用了方块修复，则完全不需要记录玩家破坏事件
        if (!Config.heroBlockRestoration) {
            return;
        }

        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            BlockPos pos = event.getPos();
            BlockState state = event.getState();

            // 1. 将玩家直接破坏的方块加入白名单
            PLAYER_BROKEN_BLOCKS.add(pos.immutable());

            // 2. 处理双格方块（门、高草、向日葵等）
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
                // 如果挖的是下半截，就把上面的坐标也加进去；反之亦然
                BlockPos otherHalfPos = (half == DoubleBlockHalf.LOWER) ? pos.above() : pos.below();
                PLAYER_BROKEN_BLOCKS.add(otherHalfPos.immutable());
            }
        }
    }

    // 【修改】NeoForge 1.21.1 使用 ServerTickEvent.Post 来代替原来的 Phase.END
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 每 tick 结束时清空白名单，防止内存泄漏
        if (!PLAYER_BROKEN_BLOCKS.isEmpty()) {
            PLAYER_BROKEN_BLOCKS.clear();
        }
    }
}
