package com.whitecloud233.herobrine_companion.client;

import com.whitecloud233.herobrine_companion.client.gui.EternalOathScreen;
import com.whitecloud233.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.herobrine_companion.client.gui.LoreHandbookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

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
