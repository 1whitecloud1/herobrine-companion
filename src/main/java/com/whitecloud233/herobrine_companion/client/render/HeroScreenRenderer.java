package com.whitecloud233.herobrine_companion.client.render;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class HeroScreenRenderer {

    // 渲染主背景和边框
    public static void renderBackground(GuiGraphics guiGraphics, int width, int height, int startX, int startY, int panelWidth, int panelHeight) {
        // 暗色背景
        guiGraphics.fill(0, 0, width, height, 0x80000000);
        
        // 面板渐变背景
        guiGraphics.fillGradient(startX, startY, startX + panelWidth, startY + panelHeight, 0xE6050510, 0xE61A0520);
        
        // 金色边框
        int borderColor = 0xFFD700; 
        guiGraphics.renderOutline(startX, startY, panelWidth, panelHeight, borderColor);
        guiGraphics.renderOutline(startX - 1, startY - 1, panelWidth + 2, panelHeight + 2, 0xFF000000);
    }

    // 渲染 Hero 实体模型
    public static void renderEntity(GuiGraphics guiGraphics, int startX, int startY, float xMouse, float yMouse, HeroEntity dummyHero) {
        if (dummyHero != null) {
            int entityBoxX1 = startX + 20;
            int entityBoxY1 = startY + 20;
            int entityBoxX2 = startX + 140;
            int entityBoxY2 = startY + 180;
            
            // 实体背景微光
            guiGraphics.fillGradient(entityBoxX1, entityBoxY1, entityBoxX2, entityBoxY2, 0x00000000, 0x40FFFFFF);
            
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics, 
                entityBoxX1, entityBoxY1, 
                entityBoxX2, entityBoxY2, 
                70, 0.0625F, 
                xMouse, yMouse, 
                dummyHero
            );
        }
    }

    // 渲染标题文字
    public static void renderTitle(GuiGraphics guiGraphics, Font font, int textX, int textY) {
        float pulse = (Mth.sin(System.currentTimeMillis() / 500.0f) + 1.0f) * 0.5f;
        int titleColor = interpolateColor(0xFFD700, 0xFFFFFF, pulse * 0.5f); 

        guiGraphics.drawString(font, Component.translatable("entity.herobrine_companion.hero").withStyle(style -> style.withBold(true)), textX, textY, titleColor, true);
        
        // 标题下划线
        guiGraphics.fill(textX, textY + 12, textX + 140, textY + 13, 0xFF555555);
    }
    
    private static int interpolateColor(int color1, int color2, float factor) {
        int r1 = (color1 >> 16) & 0xFF; int g1 = (color1 >> 8) & 0xFF; int b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF; int g2 = (color2 >> 8) & 0xFF; int b2 = color2 & 0xFF;
        int r = (int)(r1 + (r2 - r1) * factor);
        int g = (int)(g1 + (g2 - g1) * factor);
        int b = (int)(b1 + (b2 - b1) * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}