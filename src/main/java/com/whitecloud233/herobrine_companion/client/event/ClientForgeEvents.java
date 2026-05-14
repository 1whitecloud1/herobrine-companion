package com.whitecloud233.herobrine_companion.client.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.service.ConversationStore;
import com.whitecloud233.herobrine_companion.client.service.LocalChatService;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.ai.UpdateClientLanguagePacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.Locale;

@EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public class ClientForgeEvents {
    private static String lastSyncedLanguageCode;

    @SubscribeEvent
    public static void onClientPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // 修改说明：LocalChatService 在第一次 getInstance() 时会自动连接数据库。
        // 这里调用 loadChatRules() 是为了确保每次进游戏都重新读取一遍规则。
        LocalChatService.getInstance().loadChatRules();
        ConversationStore.getInstance().loadForCurrentSession();
        lastSyncedLanguageCode = null;
    }

    @SubscribeEvent
    public static void onClientPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        lastSyncedLanguageCode = null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        syncLanguageToServerIfNeeded(Minecraft.getInstance());
    }

    private static void syncLanguageToServerIfNeeded(Minecraft mc) {
        if (mc == null || mc.player == null || mc.getConnection() == null) {
            return;
        }
        String currentLanguageCode = normalizeLanguageCode(mc.options.languageCode);
        if (currentLanguageCode.equals(lastSyncedLanguageCode)) {
            return;
        }
        PacketHandler.sendToServer(new UpdateClientLanguagePacket(currentLanguageCode));
        lastSyncedLanguageCode = currentLanguageCode;
    }

    private static String normalizeLanguageCode(String rawLanguageCode) {
        if (rawLanguageCode == null) {
            return "en_us";
        }
        String normalized = rawLanguageCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.isEmpty() ? "en_us" : normalized;
    }
}