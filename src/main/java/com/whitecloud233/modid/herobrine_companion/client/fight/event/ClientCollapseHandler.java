package com.whitecloud233.modid.herobrine_companion.client.fight.event;

import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "herobrine_companion", value = Dist.CLIENT)
public class ClientCollapseHandler {

    public static boolean isCollapsing = false;
    // 【新增】：客户端专属演出计时器
    public static int collapseTicks = 0;

    public static void startCollapse() {
        isCollapsing = true;
        collapseTicks = 0; // 重置计时器
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.hideGui = true;
        }
    }

    public static void stopCollapse() {
        isCollapsing = false;
        collapseTicks = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.hideGui = false;
        }
    }

    // =========================================
    // 【核心演出】：控制音乐停止、心跳声播放
    // =========================================
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        if (isCollapsing && mc.player != null && mc.level != null) {
            collapseTicks++;

            // 安全机制：如果回到了主世界，强制停止演出
            if (mc.level.dimension() != ModStructures.END_RING_DIMENSION_KEY) {
                stopCollapse();
                return;
            }

            // 第 1 帧：瞬间掐断所有原版背景音乐和环境音效，营造极致的死寂
            if (collapseTicks == 1) {
                mc.getSoundManager().stop();
            }

            // 第 140 帧（玩家开始掉入虚空后）：开始播放沉闷的心跳声
            if (collapseTicks > 140) {
                // 心跳间隔越来越短：从 35 帧跳一次，缩短到最快 10 帧跳一次
                int interval = Math.max(10, 35 - (collapseTicks - 140) / 6);
                if (collapseTicks % interval == 0) {
                    // 使用循声守卫(Warden)的震撼心跳声！
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, 2.0F, 1.0F);
                }
            }
        }
    }

    // =========================================
    // 【核心演出】：从透明逐渐向纯黑渐变 (Fade to Black)
    // =========================================
    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (isCollapsing) {
            int alpha = 0;

            // 在前 135 帧（约 7 秒），让玩家眼睁睁看着方块消失，不遮挡视线
            // 从 135 帧开始（玩家掉下去了），用 5 秒的时间，慢慢变黑
            if (collapseTicks > 135) {
                alpha = (int) (((collapseTicks - 135) / 100.0f) * 255);
                alpha = Math.min(255, Math.max(0, alpha)); // 限制在 0~255 之间
            }

            // 只有当 alpha 大于 0 时才画黑布，越往后越黑，直到彻底吞噬屏幕
            if (alpha > 0) {
                GuiGraphics guiGraphics = event.getGuiGraphics();
                int width = event.getWindow().getGuiScaledWidth();
                int height = event.getWindow().getGuiScaledHeight();

                // 使用 ARGB 颜色格式组合透明度与黑色
                int color = (alpha << 24) | 0x000000;
                guiGraphics.fill(0, 0, width, height, color);
            }
        }
    }

    // 天空与浓雾变黑保留，增加环境压抑感
    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (isCollapsing) { event.setRed(0.0f); event.setGreen(0.0f); event.setBlue(0.0f); }
    }
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (isCollapsing) { event.setNearPlaneDistance(0.0f); event.setFarPlaneDistance(10.0f); event.setCanceled(true); }
    }
    @SubscribeEvent
    public static void onClientLogOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { stopCollapse(); }
    @SubscribeEvent
    public static void onClientLogIn(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) { stopCollapse(); }
}