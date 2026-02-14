package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.SyncHeroVisitPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class PlayerConnectionHandler {

    // 1. 玩家登录游戏时同步
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncVisitState(player);
        }
    }

    // 2. 玩家重生时同步 (防止死亡后客户端数据丢失)
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncVisitState(player);
        }
    }

    // 3. 玩家切换维度时同步 (你之前已经写了，但这里加一层保险)
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncVisitState(player);
        }
    }

    // 辅助方法：发送数据包
    private static void syncVisitState(ServerPlayer player) {
        boolean visited = player.getPersistentData().getBoolean("HasVisitedHeroDimension");
        // 哪怕是 false 也要发，确保客户端状态被重置/初始化
        PacketHandler.sendToPlayer(new SyncHeroVisitPacket(visited), player);
    }
}