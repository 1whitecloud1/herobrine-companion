package com.whitecloud233.modid.herobrine_companion.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.event.TickEvent; // 【修复】正确的 TickEvent 导入路径
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "herobrine_companion", bus = Mod.EventBusSubscriber.Bus.FORGE)
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

            // 2. 处理双格方块（门、高草、向日葵等）
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
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // 【修复】判断 Tick 阶段为 END 时再清空
        if (event.phase == TickEvent.Phase.END) {
            PLAYER_BROKEN_BLOCKS.clear();
        }
    }
}