package com.whitecloud233.herobrine_companion.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class ComputerControlConfirmScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 440;
    private static final int PANEL_HEIGHT = 220;
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 10;
    private static final int PANEL_BACKGROUND = 0xFF202124;
    private static final int PANEL_BORDER = 0xFFAA3333;

    private final Screen previousScreen;
    private final Component actionDescription;
    private final String commandPreview;
    private final Consumer<Boolean> callback;
    private boolean decided;

    public ComputerControlConfirmScreen(Screen previousScreen, Component actionDescription, String commandPreview,
                                        Consumer<Boolean> callback) {
        super(Component.translatable("gui.herobrine_companion.computer_control.confirm.title"));
        this.previousScreen = previousScreen;
        this.actionDescription = actionDescription;
        this.commandPreview = commandPreview == null ? "" : commandPreview;
        this.callback = callback;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(PANEL_MAX_WIDTH, Math.max(280, this.width - 24));
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = Math.max(8, (this.height - PANEL_HEIGHT) / 2);
        int buttonY = panelTop + PANEL_HEIGHT - 34;
        int buttonGroupWidth = BUTTON_WIDTH * 2 + GAP;
        int buttonX = panelLeft + (panelWidth - buttonGroupWidth) / 2;

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.computer_control.confirm.execute"),
                ignored -> this.decide(true), null));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                buttonX + BUTTON_WIDTH + GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.computer_control.confirm.cancel"),
                ignored -> this.decide(false), null));
    }

    public void renderBackground(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int panelWidth = Math.min(PANEL_MAX_WIDTH, Math.max(280, this.width - 24));
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = Math.max(8, (this.height - PANEL_HEIGHT) / 2);
        int contentLeft = panelLeft + 16;
        int contentWidth = panelWidth - 32;

        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + PANEL_HEIGHT, PANEL_BACKGROUND);
        guiGraphics.renderOutline(panelLeft, panelTop, panelWidth, PANEL_HEIGHT, PANEL_BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, panelTop + 12, 0xFFFF7777);
        guiGraphics.drawWordWrap(this.font,
                Component.translatable("gui.herobrine_companion.computer_control.confirm.warning"),
                contentLeft, panelTop + 34, contentWidth, 0xFFFFCC66);
        guiGraphics.drawWordWrap(this.font, this.actionDescription,
                contentLeft, panelTop + 70, contentWidth, 0xFFFFFFFF);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.computer_control.confirm.command"),
                contentLeft, panelTop + 112, 0xFFAAAAAA, false);
        guiGraphics.drawWordWrap(this.font, Component.literal(this.commandPreview),
                contentLeft, panelTop + 126, contentWidth, 0xFF88CCFF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.herobrine_companion.computer_control.confirm.no_admin"),
                this.width / 2, panelTop + 166, 0xFF999999);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.decide(false);
    }

    @Override
    public void removed() {
        super.removed();
        if (!this.decided) {
            this.decided = true;
            this.callback.accept(false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void decide(boolean approved) {
        if (this.decided) {
            return;
        }
        this.decided = true;
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.previousScreen);
        }
        this.callback.accept(approved);
    }
}