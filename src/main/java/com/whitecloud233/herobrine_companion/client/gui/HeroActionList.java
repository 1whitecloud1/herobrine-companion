package com.whitecloud233.herobrine_companion.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.util.function.Supplier;

public class HeroActionList extends ObjectSelectionList<HeroActionList.ActionEntry> {

    public HeroActionList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.setRenderHeader(false, 0);
    }

    public Button addAction(Component name, Button.OnPress action, Tooltip tooltip) {
        ActionEntry entry = new ActionEntry(() -> name, action, tooltip);
        this.addEntry(entry);
        return entry.button;
    }

    public Button addDynamicAction(Supplier<Component> nameProvider, Button.OnPress action, Tooltip tooltip) {
        ActionEntry entry = new ActionEntry(nameProvider, action, tooltip);
        this.addEntry(entry);
        return entry.button;
    }

    @Override
    public int getRowWidth() {
        return this.width - 10;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getX() + this.width - 6;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();

        guiGraphics.enableScissor(x, y, x + width, y + height);

        int itemY = y - (int)this.getScrollAmount();
        for (ActionEntry entry : this.children()) {
            if (itemY + this.itemHeight >= y && itemY <= y + height) {
                entry.render(guiGraphics, 0, itemY, x, this.getRowWidth(), this.itemHeight, mouseX, mouseY, false, partialTick);
            }
            itemY += this.itemHeight;
        }

        guiGraphics.disableScissor();

        // 渲染滚动条
        int maxScroll = this.getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarX = this.getScrollbarPosition();
            int scrollbarHeight = Mth.clamp((int)((float)(height * height) / (float)this.getMaxPosition()), 32, height - 8);
            int scrollbarY = (int)this.getScrollAmount() * (height - scrollbarHeight) / maxScroll + y;
            if (scrollbarY < y) scrollbarY = y;

            guiGraphics.fill(scrollbarX, y, scrollbarX + 4, y + height, 0x40000000);
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, 0xFFD700);
        }
    }

    public int getMaxScroll() {
        return Math.max(0, this.getMaxPosition() - (this.height - 4));
    }

    public int getMaxPosition() {
        return this.getItemCount() * this.itemHeight + this.headerHeight;
    }

    public class ActionEntry extends ObjectSelectionList.Entry<ActionEntry> {
        private final Supplier<Component> nameProvider;
        private final Button button;

        public ActionEntry(Supplier<Component> nameProvider, Button.OnPress action, Tooltip tooltip) {
            this.nameProvider = nameProvider;
            
            // --- 修改开始: 使用自定义的 ThemedButton 替换原版 Button.builder ---
            // 注意: 这里直接引用 HeroScreen.ThemedButton
            this.button = new HeroScreen.ThemedButton(
                0, // x (稍后由 render 方法设置)
                0, // y (稍后由 render 方法设置)
                HeroActionList.this.getRowWidth(), // width
                20, // height
                nameProvider.get(),
                action,
                tooltip
            );
            // --- 修改结束 ---
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
            this.button.setX(left);
            this.button.setY(top);
            this.button.setWidth(width);
            this.button.setMessage(this.nameProvider.get()); // 支持动态文本更新
            this.button.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return this.button.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public Component getNarration() {
            return this.nameProvider.get();
        }
    }
}