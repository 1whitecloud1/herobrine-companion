package com.whitecloud233.modid.herobrine_companion.client.gui.crosschat;

import com.whitecloud233.modid.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.ai.RespondCrossChatInvitePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class CrossChatInviteScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 260;
    private static final int MAX_PANEL_HEIGHT = 120;
    private static final int MIN_PANEL_WIDTH = 200;
    private static final int MIN_PANEL_HEIGHT = 112;
    private static final int SCREEN_MARGIN = 8;
    private static final int CONTENT_MARGIN = 14;
    private static final int BUTTON_GAP = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BG = 0xEE2B2B2B;
    private static final int BORDER = 0xFF555555;
    private static final int TITLE = 0xFFD16D9E;
    private static final int TEXT = 0xFFA9B7C6;

    private final Screen previousScreen;
    private final UUID requesterId;
    private final String requesterName;

    public CrossChatInviteScreen(Screen previousScreen, UUID requesterId, String requesterName) {
        super(Component.translatable("gui.herobrine_companion.cross_chat.invite.title"));
        this.previousScreen = previousScreen;
        this.requesterId = requesterId;
        this.requesterName = requesterName == null ? "" : requesterName;
    }

    @Override
    protected void init() {
        super.init();
        Layout layout = this.createLayout();
        int buttonWidth = (layout.contentWidth - BUTTON_GAP) / 2;

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft, layout.buttonY, buttonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.cross_chat.accept"),
                button -> {
                    PacketHandler.sendToServer(new RespondCrossChatInvitePacket(this.requesterId, true));
                    this.closeAndReturn();
                }, null
        ));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft + buttonWidth + BUTTON_GAP, layout.buttonY, buttonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.cross_chat.deny"),
                button -> {
                    PacketHandler.sendToServer(new RespondCrossChatInvitePacket(this.requesterId, false));
                    this.closeAndReturn();
                }, null
        ));
    }

    private Layout createLayout() {
        int availableWidth = Math.max(1, this.width - SCREEN_MARGIN * 2);
        int availableHeight = Math.max(1, this.height - SCREEN_MARGIN * 2);
        int panelWidth = clampToAvailable(MIN_PANEL_WIDTH, MAX_PANEL_WIDTH, availableWidth);
        int panelHeight = clampToAvailable(MIN_PANEL_HEIGHT, MAX_PANEL_HEIGHT, availableHeight);
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int contentLeft = left + CONTENT_MARGIN;
        int contentWidth = Math.max(1, panelWidth - CONTENT_MARGIN * 2);
        int buttonY = top + panelHeight - 36;
        return new Layout(panelWidth, panelHeight, left, top, contentLeft, contentWidth, buttonY);
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
        guiGraphics.fill(layout.left, layout.top, layout.left + layout.panelWidth, layout.top + layout.panelHeight, BG);
        guiGraphics.renderOutline(layout.left, layout.top, layout.panelWidth, layout.panelHeight, BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, layout.top + 12, TITLE);

        guiGraphics.enableScissor(layout.contentLeft, layout.top + 34,
                layout.contentLeft + layout.contentWidth, layout.buttonY - 4);
        guiGraphics.drawWordWrap(this.font,
                Component.translatable("gui.herobrine_companion.cross_chat.invite.body", this.requesterName),
                layout.contentLeft, layout.top + 34, layout.contentWidth, TEXT);
        guiGraphics.disableScissor();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
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
        private final int contentLeft;
        private final int contentWidth;
        private final int buttonY;

        private Layout(int panelWidth, int panelHeight, int left, int top, int contentLeft, int contentWidth, int buttonY) {
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;
            this.left = left;
            this.top = top;
            this.contentLeft = contentLeft;
            this.contentWidth = contentWidth;
            this.buttonY = buttonY;
        }
    }
}

