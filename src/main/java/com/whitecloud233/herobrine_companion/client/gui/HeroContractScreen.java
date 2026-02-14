package com.whitecloud233.herobrine_companion.client.gui;

import com.whitecloud233.herobrine_companion.network.ContractPacket;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.world.inventory.HeroContractMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class HeroContractScreen extends AbstractContainerScreen<HeroContractMenu> {

    // 定义蓝白色系配色方案
    private static final int COL_BORDER_OUTER = 0xFF81D4FA; // 浅天蓝边框 (Light Blue)
    private static final int COL_BG_MAIN      = 0xFF011826; //以此深蓝为底 (Dark Deep Blue)
    private static final int COL_SLOT_BORDER  = 0xFF0277BD; // 插槽边框 (Medium Blue)
    private static final int COL_SLOT_BG      = 0xFF002538; // 插槽内部 (Darker Blue)
    private static final int COL_TEXT_TITLE   = 0xFFE1F5FE; // 标题白青色 (Ice White)
    private static final int COL_TEXT_LABEL   = 0xFFB3E5FC; // 标签浅蓝色

    public HeroContractScreen(HeroContractMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        // 按钮 (Sign Contract)
        this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.sign_contract"), button -> {
                    PacketHandler.sendToServer(new ContractPacket());
                })
                .bounds(this.leftPos + 48, this.topPos + 55, 80, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染屏幕背景遮罩 (稍微带点蓝色的半透明黑)
        if (this.minecraft.level != null) {
            guiGraphics.fill(0, 0, this.width, this.height, 0x90000510);
        } else {
            this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // 1. 绘制主背景板 (蓝白风格)
        // 外边框 (浅蓝色)
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, COL_BORDER_OUTER);
        // 内背景 (深蓝色)
        guiGraphics.fill(x + 2, y + 2, x + this.imageWidth - 2, y + this.imageHeight - 2, COL_BG_MAIN);

        // 2. 绘制插槽背景
        // 两个输入格子的特殊提示色也改为蓝/白色系的微调
        // Slot 1 (Nether Star): 给他一点明亮的青色光晕
        drawSlotBackground(guiGraphics, x + 60, y + 25, 0x3000FFFF);
        // Slot 2 (Dragon Breath): 给他一点深邃的蓝紫色光晕
        drawSlotBackground(guiGraphics, x + 100, y + 25, 0x305E35B1);

        // 玩家物品栏插槽背景
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                drawSlotBackground(guiGraphics, x + 8 + j * 18, y + 84 + i * 18, 0);
            }
        }
        // 快捷栏插槽背景
        for (int k = 0; k < 9; ++k) {
            drawSlotBackground(guiGraphics, x + 8 + k * 18, y + 142, 0);
        }

        // 3. 绘制标题 (冰白色)
        guiGraphics.drawCenteredString(this.font, this.title, x + this.imageWidth / 2, y + 10, COL_TEXT_TITLE);

        // 4. 绘制加号 (浅蓝色)
        guiGraphics.drawCenteredString(this.font, "+", x + 88, y + 29, COL_TEXT_LABEL);
    }

    private void drawSlotBackground(GuiGraphics guiGraphics, int x, int y, int tintColor) {
        // 绘制插槽边框 (中蓝色)
        guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, COL_SLOT_BORDER);
        // 绘制插槽底色 (深蓝偏黑)
        guiGraphics.fill(x, y, x + 16, y + 16, COL_SLOT_BG);

        // 如果有特殊的染色 (Hint)，叠加一层半透明色
        if (tintColor != 0) {
            guiGraphics.fill(x, y, x + 16, y + 16, tintColor);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 禁用默认标签，手动绘制
    }
}
