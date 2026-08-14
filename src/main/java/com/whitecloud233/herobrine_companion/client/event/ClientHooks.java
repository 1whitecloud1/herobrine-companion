package com.whitecloud233.herobrine_companion.client.event;

import com.whitecloud233.herobrine_companion.client.gui.EternalOathScreen;
import com.whitecloud233.herobrine_companion.client.gui.HeroChatScreen;
import com.whitecloud233.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.herobrine_companion.client.gui.LoreHandbookScreen;
import com.whitecloud233.herobrine_companion.client.gui.crosschat.CrossChatInviteScreen;
import com.whitecloud233.herobrine_companion.client.gui.crosschat.CrossChatScreen;
import com.whitecloud233.herobrine_companion.client.gui.crosschat.CrossSessionHubScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.UUID;

public class ClientHooks {
    public enum ChatMode {
        NONE,
        CHAT,
        CROSS_CHAT
    }
    private static ChatMode currentMode = ChatMode.NONE;
    private static boolean apiEnabled = false; // Default to false (Local)
    private static boolean allowIncomingCrossChat = true;
    private static boolean crossChatSessionActive = false;
    private static String crossChatPeerName = "";
    private static boolean crossChatHbInputMode = false;
    private static boolean crossChatAutoChatEnabled = false;
    private static int crossChatAutoHbTurnLimit = 8;

    public static void openHeroScreen(int entityId) {
        Minecraft.getInstance().setScreen(new HeroScreen(entityId));
    }


    public static void enableChat() {
        currentMode = ChatMode.CHAT;
    }
    public static void enableCrossChat() {
        currentMode = ChatMode.CROSS_CHAT;
    }

    public static void disableChat() {
        currentMode = ChatMode.NONE;
    }

    // 👇 【新增】：彻底重置所有状态的方法
    public static void resetAll() {
        currentMode = ChatMode.NONE;
        apiEnabled = false; // 强行重置回本地模式
        crossChatSessionActive = false;
        crossChatPeerName = "";
        crossChatHbInputMode = false;
        crossChatAutoChatEnabled = false;
        allowIncomingCrossChat = true;
        crossChatAutoHbTurnLimit = 8;
    }

    public static ChatMode getChatMode() {
        return currentMode;
    }

    public static boolean isApiEnabled() {
        return apiEnabled;
    }

    public static void toggleApiEnabled() {
        apiEnabled = !apiEnabled;
    }

    public static void setApiEnabled(boolean enabled) {
        apiEnabled = enabled;
    }

    public static void syncCrossChatState(boolean allowIncoming, boolean sessionActive, String peerName, boolean autoChatEnabled, int autoHbTurnLimit) {
        allowIncomingCrossChat = allowIncoming;
        crossChatSessionActive = sessionActive;
        crossChatPeerName = peerName == null ? "" : peerName;
        crossChatAutoChatEnabled = sessionActive && autoChatEnabled;
        crossChatAutoHbTurnLimit = Math.max(1, Math.min(16, autoHbTurnLimit));
        if (!crossChatSessionActive && currentMode == ChatMode.CROSS_CHAT) {
            currentMode = ChatMode.NONE;
        }
        if (!crossChatSessionActive) {
            crossChatHbInputMode = false;
        }
    }

    public static boolean isAllowIncomingCrossChat() {
        return allowIncomingCrossChat;
    }

    public static boolean hasActiveCrossChatSession() {
        return crossChatSessionActive;
    }

    public static String getCrossChatPeerName() {
        return crossChatPeerName;
    }

    public static boolean isCrossChatHbInputMode() {
        return crossChatHbInputMode;
    }

    public static void setCrossChatHbInputMode(boolean hbInputMode) {
        crossChatHbInputMode = hbInputMode;
    }

    public static int getCrossChatAutoHbTurnLimit() {
        return crossChatAutoHbTurnLimit;
    }

    public static void setCrossChatAutoHbTurnLimit(int turnLimit) {
        crossChatAutoHbTurnLimit = Math.max(1, Math.min(16, turnLimit));
    }
    public static boolean isCrossChatAutoChatEnabled() {
        return crossChatAutoChatEnabled;
    }

    public static void setCrossChatAutoChatEnabled(boolean enabled) {
        crossChatAutoChatEnabled = crossChatSessionActive && enabled;
    }

    public static void openCrossChatInvite(UUID requesterId, String requesterName) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new CrossChatInviteScreen(mc.screen, requesterId, requesterName));
    }

    public static void openCrossSessionHub(int entityId) {
        Minecraft.getInstance().setScreen(new CrossSessionHubScreen(entityId));
    }

    public static void openHeroChatFromCommand() {
        openHeroChatFromCommand(false);
    }

    public static void openHeroChatFromCommand(boolean hbInputMode) {
        Minecraft mc = Minecraft.getInstance();
        if (hasActiveCrossChatSession()) {
            if (currentMode == ChatMode.CROSS_CHAT && crossChatHbInputMode == hbInputMode) {
                disableChat();
                mc.setScreen(null);
                mc.gui.setOverlayMessage(Component.empty(), false);
                return;
            }

            crossChatHbInputMode = hbInputMode;
            enableCrossChat();
            mc.setScreen(new CrossChatScreen(""));
            if (mc.player != null) {
                mc.gui.getChat().addMessage(Component.translatable(
                        "message.herobrine_companion.cross_chat.current_peer",
                        getCrossChatPeerName().isBlank() ? "-" : getCrossChatPeerName()
                ));
            }
            return;
        }

        if (currentMode == ChatMode.CHAT) {
            disableChat();
            mc.setScreen(null);
            mc.gui.setOverlayMessage(Component.empty(), false);
            return;
        }

        enableChat();
        mc.setScreen(new HeroChatScreen(""));
    }

    // 👇 新增：给 LoreHandbookItem 用的安全打开界面方法
    public static void openHandbook(ItemStack stack) {
        Minecraft.getInstance().setScreen(new LoreHandbookScreen(stack));
    }

    // 👇 新增：给 HeroSummonItem 用的安全获取冷却进度方法
    public static float getSummonItemCooldown(ItemStack stack) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            long currentTime = level.getGameTime();
            
            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = customData.copyTag();
            long lastUseTime = tag.getLong("LastHeroSummonTime");

            long timePassed = currentTime - lastUseTime;
            long cooldownTicks = 100; // Assuming 100 ticks from HeroSummonItem
            if (timePassed < 0 || timePassed >= cooldownTicks) return 0.0F;
            return (float)(cooldownTicks - timePassed) / (float)cooldownTicks;
        }
        return 0.0F;
    }

    // 👇 新增：给 TriggerEternalOathPacket 用的安全执行方法
    public static void triggerEternalOath() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            CompoundTag data = player.getPersistentData();
            // 检查客户端临时 NBT 标记，防止同一局游戏内重复触发
            if (!data.getBoolean("HasSeenEternalOath_Client")) {
                data.putBoolean("HasSeenEternalOath_Client", true);
                Minecraft.getInstance().setScreen(new EternalOathScreen());
            }
        } else {
            // 兜底逻辑
            Minecraft.getInstance().setScreen(new EternalOathScreen());
        }
    }

    // 👇 新增：给 SyncHeroVisitPacket 用的安全设置方法
    public static void setVisitedHeroDimension(boolean visited) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.getPersistentData().putBoolean("HasVisitedHeroDimension", visited);
        }
    }
}
