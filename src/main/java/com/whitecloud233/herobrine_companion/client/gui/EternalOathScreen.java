package com.whitecloud233.herobrine_companion.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class EternalOathScreen extends Screen {
    private List<FormattedCharSequence> lines;
    private float scrollSpeed = 0.3f;
    private float scrollAmount = 0.0f;
    private long startTime;

    public EternalOathScreen() {
        super(GameNarrator.NO_TITLE);
    }

    @Override
    protected void init() {
        this.startTime = System.currentTimeMillis();
        // 加载文本
        Component text = Component.translatable("lore.herobrine_companion.fragment_9.body");
        // 分割文本，宽度稍微窄一点，更有诗意
        this.lines = this.font.split(text, this.width );
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 留空：禁用原版自带的世界模糊和黑色背景遮罩
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 重置 Shader 颜色和深度测试
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableDepthTest();
        
        // 绘制纯黑背景 (覆盖整个屏幕)
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);

        int lineHeight = 12;
        float timeElapsed = (System.currentTimeMillis() - startTime) / 50.0f; // 转换为 tick
        this.scrollAmount = timeElapsed * scrollSpeed;

        int yStart = this.height; // 从屏幕底部开始
        
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, -scrollAmount, 0);

        int y = yStart;
        
        // 绘制标题
        Component title = Component.translatable("lore.herobrine_companion.fragment_9.title");
        guiGraphics.drawCenteredString(this.font, title, this.width / 2, y, 0xFF55FFFF); // 青色
        y += 30;

        // 绘制正文
        for (FormattedCharSequence line : lines) {
            // 简单的淡入淡出效果 (可选)
            // float alpha = 1.0f;
            // int color = (int)(alpha * 255) << 24 | 0xFFFFFF;
            
            // 居中绘制
            // 计算行宽
            int lineWidth = this.font.width(line);
            guiGraphics.drawString(this.font, line, (this.width - lineWidth) / 2, y, 0xFFFFFFFF, false);
            y += lineHeight;
        }
        
        // 结束标志
        y += 50;
        guiGraphics.drawCenteredString(this.font, "--- Herobrine ---", this.width / 2, y, 0xFFAAAAAA);

        guiGraphics.pose().popPose();
        
        // 自动关闭逻辑
        if (yStart - scrollAmount + (lines.size() * lineHeight) + 100 < 0) {
            this.onClose();
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 不暂停游戏
    }
    
    @Override
    public void onClose() {
        super.onClose();
        // 可以在这里添加关闭后的音效或其他逻辑
    }
}
