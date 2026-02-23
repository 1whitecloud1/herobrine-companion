package com.whitecloud233.modid.herobrine_companion.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "herobrine_companion", bus = Mod.EventBusSubscriber.Bus.FORGE)
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
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            PLAYER_BROKEN_BLOCKS.clear();
        }
    }
}