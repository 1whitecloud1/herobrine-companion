package com.whitecloud233.modid.herobrine_companion.client.gui;

import com.mojang.blaze3d.systems.RenderSystem; // 新增导入
import com.whitecloud233.modid.herobrine_companion.network.ContractPacket;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.world.inventory.HeroContractMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class HeroContractScreen extends AbstractContainerScreen<HeroContractMenu> {

    private static final int COL_BORDER_OUTER = 0xFF81D4FA;
    private static final int COL_BG_MAIN      = 0xFF011826;
    private static final int COL_SLOT_BORDER  = 0xFF0277BD;
    private static final int COL_SLOT_BG      = 0xFF002538;
    private static final int COL_TEXT_TITLE   = 0xFFE1F5FE;
    private static final int COL_TEXT_LABEL   = 0xFFB3E5FC;

    public HeroContractScreen(HeroContractMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.sign_contract"), button -> {
                    PacketHandler.sendToServer(new ContractPacket());
                })
                .bounds(this.leftPos + 48, this.topPos + 55, 80, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft.level != null) {
            guiGraphics.fill(0, 0, this.width, this.height, 0x90000510);
        } else {
            this.renderBackground(guiGraphics);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, COL_BORDER_OUTER);
        guiGraphics.fill(x + 2, y + 2, x + this.imageWidth - 2, y + this.imageHeight - 2, COL_BG_MAIN);

        // 绘制特定的槽位背景及高亮颜色
        drawSlotBackground(guiGraphics, x + 60, y + 25, 0x3000FFFF);
        drawSlotBackground(guiGraphics, x + 100, y + 25, 0x305E35B1);

        // --- 修改：绘制半透明提示材料（下界之星 与 龙息） ---

        // 1. 设置渲染系统的颜色，RGBA 中的 Alpha (透明度) 设为 0.5f (50% 透明)
        // 注意：如果不重置，之后绘制的所有内容都会变半透明
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.5F);
        // 启用混合（渲染半透明通常需要）
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 只有当槽位中没有物品时，才渲染虚影提示
        if (!this.menu.getSlot(0).hasItem()) {
            guiGraphics.renderFakeItem(new ItemStack(Items.NETHER_STAR), x + 60, y + 25);
        }

        if (!this.menu.getSlot(1).hasItem()) {
            guiGraphics.renderFakeItem(new ItemStack(Items.DRAGON_BREATH), x + 100, y + 25);
        }

        // 2. 非常重要：渲染完成后，必须将颜色和透明度重置回 1.0f (全不透明)，否则会影响后续的文字和物品渲染
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend(); // 关闭混合

        // ------------------------------------

        // 绘制玩家背包的槽位背景
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                drawSlotBackground(guiGraphics, x + 8 + j * 18, y + 84 + i * 18, 0);
            }
        }
        // 绘制快捷栏的槽位背景
        for (int k = 0; k < 9; ++k) {
            drawSlotBackground(guiGraphics, x + 8 + k * 18, y + 142, 0);
        }

        // 绘制标题和中间的 "+" 号
        guiGraphics.drawCenteredString(this.font, this.title, x + this.imageWidth / 2, y + 10, COL_TEXT_TITLE);
        guiGraphics.drawCenteredString(this.font, "+", x + 88, y + 29, COL_TEXT_LABEL);
    }

    private void drawSlotBackground(GuiGraphics guiGraphics, int x, int y, int tintColor) {
        guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, COL_SLOT_BORDER);
        guiGraphics.fill(x, y, x + 16, y + 16, COL_SLOT_BG);

        if (tintColor != 0) {
            guiGraphics.fill(x, y, x + 16, y + 16, tintColor);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 覆盖默认方法以防止原版在奇怪的位置渲染标题和 "Inventory" 标签
    }
}