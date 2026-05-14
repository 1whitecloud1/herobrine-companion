package com.whitecloud233.modid.herobrine_companion.client.gui.crosschat;

import com.whitecloud233.modid.herobrine_companion.client.gui.HeroScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CrossChatGuideScreen extends Screen {
    private static final int PANEL_WIDTH = 392;
    private static final int PANEL_HEIGHT = 292;
    private static final int BG = 0xEE2B2B2B;
    private static final int BORDER = 0xFF555555;
    private static final int TITLE = 0xFFD16D9E;
    private static final int TEXT = 0xFFA9B7C6;
    private static final int INFO = 0xFF6A8759;

    private final Screen previousScreen;

    public CrossChatGuideScreen(Screen previousScreen) {
        super(Component.translatable("gui.herobrine_companion.cross_chat.guide_title"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + (PANEL_WIDTH - 100) / 2,
                top + PANEL_HEIGHT - 28,
                100,
                20,
                Component.translatable("gui.herobrine_companion.back"),
                button -> this.closeAndReturn(),
                null
        ));
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
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int textLeft = left + 16;
        int textWidth = PANEL_WIDTH - 32;

        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BG);
        guiGraphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top + 12, TITLE);

        int y = top + 32;
        y = this.drawWrapped(guiGraphics, Component.translatable("gui.herobrine_companion.cross_chat.guide_intro"), textLeft, y, textWidth, 8);
        y = this.drawSection(guiGraphics, textLeft, y, textWidth,
                Component.translatable("gui.herobrine_companion.cross_chat.guide_request_title"),
                Component.translatable("gui.herobrine_companion.cross_chat.guide_request_body"));
        y = this.drawSection(guiGraphics, textLeft, y, textWidth,
                Component.translatable("gui.herobrine_companion.cross_chat.guide_player_title"),
                Component.translatable("gui.herobrine_companion.cross_chat.guide_player_body"));
        y = this.drawSection(guiGraphics, textLeft, y, textWidth,
                Component.translatable("gui.herobrine_companion.cross_chat.guide_hb_title"),
                Component.translatable("gui.herobrine_companion.cross_chat.guide_hb_body"));
        y = this.drawSection(guiGraphics, textLeft, y, textWidth,
                Component.translatable("gui.herobrine_companion.cross_chat.guide_close_title"),
                Component.translatable("gui.herobrine_companion.cross_chat.guide_close_body"));
        this.drawSection(guiGraphics, textLeft, y, textWidth,
                Component.translatable("gui.herobrine_companion.cross_chat.guide_token_title"),
                Component.translatable("gui.herobrine_companion.cross_chat.guide_token_body"));

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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

