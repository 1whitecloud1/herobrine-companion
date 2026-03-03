package com.whitecloud233.herobrine_companion.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
// 监听玩家挖方块事件，让 Mixin 放行
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "herobrine_companion", bus = EventBusSubscriber.Bus.GAME)
public class WorldAnomalyEventHandler {

    // 创建一个白名单，记录当前 tick 玩家正在合法破坏的方块
    public static final Set<BlockPos> PLAYER_BROKEN_BLOCKS = ConcurrentHashMap.newKeySet();



    @SubscribeEvent
    public static void onPlayerBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            BlockPos pos = event.getPos();
            BlockState state = event.getState();

            // 1. 将玩家直接破坏的方块加入白名单
            PLAYER_BROKEN_BLOCKS.add(pos.immutable());

            // 2. 【修复核心】处理双格方块（门、高草、向日葵等）
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
                // 如果挖的是下半截，就把上面的坐标也加进去；反之亦然
                BlockPos otherHalfPos = (half == DoubleBlockHalf.LOWER) ? pos.above() : pos.below();
                PLAYER_BROKEN_BLOCKS.add(otherHalfPos.immutable());
            }
        }
    }

    // 每 tick 结束时清空白名单，防止内存泄漏
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PLAYER_BROKEN_BLOCKS.clear();
    }
}