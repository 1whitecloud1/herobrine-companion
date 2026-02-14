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

    public static ChatMode getChatMode() {
        return currentMode;
    }
    
    public static boolean isApiEnabled() {
        return apiEnabled;
    }
    
    public static void setApiEnabled(boolean enabled) {
        apiEnabled = enabled;
    }
    
    public static void toggleApiEnabled() {
        apiEnabled = !apiEnabled;
    }
}
