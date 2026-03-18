package com.whitecloud233.modid.herobrine_companion.client.event;

import com.whitecloud233.modid.herobrine_companion.client.gui.HeroScreen;
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

    // 👇 新增：给 LoreHandbookItem 用的安全打开界面方法
    public static void openHandbook(net.minecraft.world.item.ItemStack stack) {
        Minecraft.getInstance().setScreen(new com.whitecloud233.modid.herobrine_companion.client.gui.LoreHandbookScreen(stack));
    }

    // 👇 新增：给 HeroSummonItem 用的安全获取冷却进度方法
    public static float getSummonItemCooldown(net.minecraft.world.item.ItemStack stack) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            long currentTime = level.getGameTime();
            // 注意：这里需要你对应原逻辑里的获取上次使用时间的方法
            long lastUseTime = stack.getOrCreateTag().getLong("LastHeroSummonTime");
            long timePassed = currentTime - lastUseTime;
            if (timePassed < 0 || timePassed >= 100) return 0.0F;
            return (float)(100 - timePassed) / 100.0F;
        }
        return 0.0F;
    }

    // 👇 新增：给 TriggerEternalOathPacket 用的安全执行方法
    public static void triggerEternalOath() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            net.minecraft.nbt.CompoundTag data = player.getPersistentData();
            // 检查客户端临时 NBT 标记，防止同一局游戏内重复触发
            if (!data.getBoolean("HasSeenEternalOath_Client")) {
                data.putBoolean("HasSeenEternalOath_Client", true);
                net.minecraft.client.Minecraft.getInstance().setScreen(new com.whitecloud233.modid.herobrine_companion.client.gui.EternalOathScreen());
            }
        } else {
            // 兜底逻辑
            net.minecraft.client.Minecraft.getInstance().setScreen(new com.whitecloud233.modid.herobrine_companion.client.gui.EternalOathScreen());
        }
    }

    // 在 ClientHooks.java 中新增：
    public static void setVisitedHeroDimension(boolean visited) {
        if (net.minecraft.client.Minecraft.getInstance().player != null) {
            net.minecraft.client.Minecraft.getInstance().player.getPersistentData().putBoolean("HasVisitedHeroDimension", visited);
        }
    }
}