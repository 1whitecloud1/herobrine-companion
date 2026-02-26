package com.whitecloud233.herobrine_companion.client;

import com.whitecloud233.herobrine_companion.client.gui.HeroScreen;
import net.minecraft.client.Minecraft;

public class ClientHooks {
    public enum ChatMode {
        NONE,
        CHAT // Unified chat mode
    }

    private static ChatMode currentMode = ChatMode.NONE;
    private static boolean apiEnabled = false; // Default to false (Local)

    public static void openHeroScreen(int entityId) {
        Minecraft.getInstance().setScreen(new HeroScreen(entityId));
    }

    public static void enableChat() {
        currentMode = ChatMode.CHAT;
    }

    public static void disableChat() {
        currentMode = ChatMode.NONE;
    }

    // 👇 【新增】：彻底重置所有状态的方法
    public static void resetAll() {
        currentMode = ChatMode.NONE;
        apiEnabled = false; // 强行重置回本地模式
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
}