package com.whitecloud233.modid.herobrine_companion.client.gui.crosschat;

import com.whitecloud233.modid.herobrine_companion.client.gui.HeroScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CrossChatGuideScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 392;
    private static final int MAX_PANEL_HEIGHT = 292;
    private static final int MIN_PANEL_WIDTH = 250;
    private static final int MIN_PANEL_HEIGHT = 160;
    private static final int SCREEN_MARGIN = 8;
    private static final int CONTENT_MARGIN = 16;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SCROLL_STEP = 18;
    private static final int BG = 0xEE2B2B2B;
    private static final int BORDER = 0xFF555555;
    private static final int TITLE = 0xFFD16D9E;
    private static final int TEXT = 0xFFA9B7C6;
    private static final int INFO = 0xFF6A8759;

    private final Screen previousScreen;
    private int scrollOffset;

    public CrossChatGuideScreen(Screen previousScreen) {
        super(Component.translatable("gui.herobrine_companion.cross_chat.guide_title"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        super.init();
        Layout layout = this.createLayout();
        this.scrollOffset = Math.min(this.scrollOffset, this.getMaxScroll(layout));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.left + (layout.panelWidth - 100) / 2,
                layout.backY,
                100,
                BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.back"),
                button -> this.closeAndReturn(),
                null
        ));
    }

    private Layout createLayout() {
        int availableWidth = Math.max(1, this.width - SCREEN_MARGIN * 2);
        int availableHeight = Math.max(1, this.height - SCREEN_MARGIN * 2);
        int panelWidth = clampToAvailable(MIN_PANEL_WIDTH, MAX_PANEL_WIDTH, availableWidth);
        int panelHeight = clampToAvailable(MIN_PANEL_HEIGHT, MAX_PANEL_HEIGHT, availableHeight);
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int textLeft = left + CONTENT_MARGIN;
        int textWidth = Math.max(1, panelWidth - CONTENT_MARGIN * 2);
        int contentTop = top + 32;
        int backY = top + panelHeight - 28;
        int contentBottom = Math.max(contentTop + 1, backY - 8);
        return new Layout(panelWidth, panelHeight, left, top, textLeft, textWidth, contentTop, contentBottom, backY);
    }

    private static int clampToAvailable(int min, int max, int available) {
        int clamped = Math.min(max, available);
        return Math.max(Math.min(min, available), clamped);
    }

    private void closeAndReturn() {
        Minecraft.getInstance().setScreen(this.previousScreen);
    }

    @Override
    public void onClose() {
        this.closeAndReturn();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = this.createLayout();
        this.scrollOffset = Math.min(this.scrollOffset, this.getMaxScroll(layout));

        guiGraphics.fill(layout.left, layout.top, layout.left + layout.panelWidth, layout.top + layout.panelHeight, BG);
        guiGraphics.renderOutline(layout.left, layout.top, layout.panelWidth, layout.panelHeight, BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, layout.top + 12, TITLE);

        guiGraphics.enableScissor(layout.textLeft, layout.contentTop,
                layout.textLeft + layout.textWidth, layout.contentBottom);
        int y = layout.contentTop - this.scrollOffset;
        y = this.drawWrapped(guiGraphics, Component.translatable("gui.herobrine_companion.cross_chat.guide_intro"), layout.textLeft, y, layout.textWidth, 8);
        y = this.drawSection(guiGraphics, layout.textLeft, y, layout.textWidth,
                Component.translatable("gui.herobrine_companion.cross_chat.guide_request_title"),
                Component.translatable("gui.herobrine_companion.cross_chat.guide_request_body"));
        y = this.drawSection(guiGraphics, layout.textLeft, y, layout.textWidth,
                Component.translatable("gui.herobrine_companion.cross_chat.guide_player_title"),
                Component.translatable("gui.herobrine_companion.cross_chat.guide_player_body"));
        y = this.drawSection(guiGraphics, layout.textLeft, y, layout.textWidth,
                Component.translatable("gui.herobrine_companion.cross_chat.guide_hb_title"),
                Component.translatable("gui.herobrine_companion.cross_chat.guide_hb_body"));
        y = this.drawSection(guiGraphics, layout.textLeft, y, layout.textWidth,
                Component.translatable("gui.herobrine_companion.cross_chat.guide_close_title"),
                Component.translatable("gui.herobrine_companion.cross_chat.guide_close_body"));
        this.drawSection(guiGraphics, layout.textLeft, y, layout.textWidth,
                Component.translatable("gui.herobrine_companion.cross_chat.guide_token_title"),
                Component.translatable("gui.herobrine_companion.cross_chat.guide_token_body"));
        guiGraphics.disableScissor();

        int maxScroll = this.getMaxScroll(layout);
        if (maxScroll > 0) {
            int barX = layout.left + layout.panelWidth - 8;
            int barTop = layout.contentTop;
            int barHeight = layout.contentBottom - layout.contentTop;
            int thumbHeight = Math.max(18, barHeight * barHeight / (barHeight + maxScroll));
            int thumbY = barTop + this.scrollOffset * (barHeight - thumbHeight) / maxScroll;
            guiGraphics.fill(barX, barTop, barX + 3, layout.contentBottom, 0x40000000);
            guiGraphics.fill(barX, thumbY, barX + 3, thumbY + thumbHeight, TITLE);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private int drawSection(GuiGraphics guiGraphics, int x, int y, int width, Component title, Component body) {
        guiGraphics.drawString(this.font, title, x, y, INFO, false);
        return this.drawWrapped(guiGraphics, body, x, y + 12, width, 10);
    }

    private int drawWrapped(GuiGraphics guiGraphics, Component text, int x, int y, int width, int gapAfter) {
        guiGraphics.drawWordWrap(this.font, text, x, y, width, TEXT);
        return y + this.font.split(text, width).size() * this.font.lineHeight + gapAfter;
    }

    private int getMaxScroll(Layout layout) {
        int contentHeight = this.measureContentHeight(layout.textWidth);
        int viewportHeight = layout.contentBottom - layout.contentTop;
        return Math.max(0, contentHeight - viewportHeight);
    }

    private int measureContentHeight(int width) {
        int y = 0;
        y = this.measureWrapped(y, Component.translatable("gui.herobrine_companion.cross_chat.guide_intro"), width, 8);
        y = this.measureSection(y, Component.translatable("gui.herobrine_companion.cross_chat.guide_request_body"), width);
        y = this.measureSection(y, Component.translatable("gui.herobrine_companion.cross_chat.guide_player_body"), width);
        y = this.measureSection(y, Component.translatable("gui.herobrine_companion.cross_chat.guide_hb_body"), width);
        y = this.measureSection(y, Component.translatable("gui.herobrine_companion.cross_chat.guide_close_body"), width);
        return this.measureSection(y, Component.translatable("gui.herobrine_companion.cross_chat.guide_token_body"), width);
    }

    private int measureSection(int y, Component body, int width) {
        return this.measureWrapped(y + 12, body, width, 10);
    }

    private int measureWrapped(int y, Component text, int width, int gapAfter) {
        return y + this.font.split(text, width).size() * this.font.lineHeight + gapAfter;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        Layout layout = this.createLayout();
        if (mouseX >= layout.textLeft && mouseX <= layout.textLeft + layout.textWidth
                && mouseY >= layout.contentTop && mouseY <= layout.contentBottom) {
            int maxScroll = this.getMaxScroll(layout);
            if (maxScroll > 0) {
                this.scrollOffset = clamp(this.scrollOffset - (int) (scrollDelta * SCROLL_STEP), 0, maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class Layout {
        private final int panelWidth;
        private final int panelHeight;
        private final int left;
        private final int top;
        private final int textLeft;
        private final int textWidth;
        private final int contentTop;
        private final int contentBottom;
        private final int backY;

        private Layout(int panelWidth, int panelHeight, int left, int top, int textLeft, int textWidth,
                       int contentTop, int contentBottom, int backY) {
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;
            this.left = left;
            this.top = top;
            this.textLeft = textLeft;
            this.textWidth = textWidth;
            this.contentTop = contentTop;
            this.contentBottom = contentBottom;
            this.backY = backY;
        }
    }
}

