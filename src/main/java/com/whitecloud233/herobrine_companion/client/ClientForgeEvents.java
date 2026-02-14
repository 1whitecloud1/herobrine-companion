package com.whitecloud233.herobrine_companion.client;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.service.LocalChatService;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ClientForgeEvents {

    @SubscribeEvent
    public static void onClientPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // 修改说明：LocalChatService 在第一次 getInstance() 时会自动连接数据库。
        // 这里调用 loadChatRules() 是为了确保每次进游戏都重新读取一遍规则。
        LocalChatService.getInstance().loadChatRules();
    }
}