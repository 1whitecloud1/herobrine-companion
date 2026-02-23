package com.whitecloud233.herobrine_companion.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "herobrine_companion", bus = EventBusSubscriber.Bus.GAME)
public class WorldAnomalyEventHandler {

    // 创建一个白名单，记录当前 tick 玩家正在合法破坏的方块
    public static final Set<BlockPos> PLAYER_BROKEN_BLOCKS = ConcurrentHashMap.newKeySet();

    // 监听玩家挖方块事件，让 Mixin 放行
    @SubscribeEvent
    public static void onPlayerBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            PLAYER_BROKEN_BLOCKS.add(event.getPos().immutable());
        }
    }

    // 每 tick 结束时清空白名单，防止内存泄漏
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PLAYER_BROKEN_BLOCKS.clear();
    }
}