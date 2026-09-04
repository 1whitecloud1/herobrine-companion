package com.whitecloud233.modid.herobrine_companion.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 电脑控制确认屏：向玩家展示将被执行的本机动作并征求确认。
 * 面板尺寸<b>自适应窗口</b>（宽高均钳制到窗口内），内容区 scissor 裁剪防溢出。
 */
public class ComputerControlConfirmScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 440;
    private static final int PANEL_MIN_WIDTH = 240;
    private static final int PANEL_MAX_HEIGHT = 220;
    private static final int PANEL_MIN_HEIGHT = 160;
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

    private record Panel(int left, int top, int width, int height) {
        int contentLeft() {
            return left + 16;
        }

        int contentWidth() {
            return width - 32;
        }

        int contentBottom() {
            return top + height - 46;
        }
    }

    private Panel panel() {
        int w = Math.min(PANEL_MAX_WIDTH, Math.max(PANEL_MIN_WIDTH, this.width - 24));
        int h = Math.min(PANEL_MAX_HEIGHT, Math.max(PANEL_MIN_HEIGHT, this.height - 24));
        return new Panel((this.width - w) / 2, Math.max(4, (this.height - h) / 2), w, h);
    }

    @Override
    protected void init() {
        Panel p = panel();
        int buttonY = p.top() + p.height() - 34;
        int buttonGroupWidth = BUTTON_WIDTH * 2 + GAP;
        int buttonX = p.left() + (p.width() - buttonGroupWidth) / 2;

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.computer_control.confirm.execute"),
                ignored -> this.decide(true), null));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                buttonX + BUTTON_WIDTH + GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.computer_control.confirm.cancel"),
                ignored -> this.decide(false), null));
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        Panel p = panel();
        int contentLeft = p.contentLeft();
        int contentWidth = p.contentWidth();

        guiGraphics.fill(p.left(), p.top(), p.left() + p.width(), p.top() + p.height(), PANEL_BACKGROUND);
        guiGraphics.renderOutline(p.left(), p.top(), p.width(), p.height(), PANEL_BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, p.top() + 12, 0xFFFF7777);

        guiGraphics.enableScissor(p.left() + 1, p.top() + 26, p.left() + p.width() - 1, p.contentBottom());
        guiGraphics.drawWordWrap(this.font,
                Component.translatable("gui.herobrine_companion.computer_control.confirm.warning"),
                contentLeft, p.top() + 34, contentWidth, 0xFFFFCC66);
        guiGraphics.drawWordWrap(this.font, this.actionDescription,
                contentLeft, p.top() + 70, contentWidth, 0xFFFFFFFF);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.computer_control.confirm.command"),
                contentLeft, p.top() + 112, 0xFFAAAAAA, false);
        guiGraphics.drawWordWrap(this.font, Component.literal(this.commandPreview),
                contentLeft, p.top() + 126, contentWidth, 0xFF88CCFF);
        guiGraphics.disableScissor();

        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.herobrine_companion.computer_control.confirm.no_admin"),
                this.width / 2, p.top() + p.height() - 42, 0xFF999999);

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
