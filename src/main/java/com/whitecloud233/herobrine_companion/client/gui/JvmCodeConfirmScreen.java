package com.whitecloud233.herobrine_companion.client.gui;

import com.whitecloud233.herobrine_companion.client.jvm.JvmCodeClassification;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Consumer;

/**
 * jvm_code_skill 确认框（单一职责：向玩家展示代码并征求确认，必要时用大白话解释后果）。
 * 明确标注：同意后该代码拥有完整 JVM 权限（可读写文件/联网/改游戏）。
 * 命中生命周期操作（{@link JvmCodeClassification#LIFECYCLE}）时，顶部用红字提示"会关闭游戏"，
 * 让看不懂代码的玩家也能明白后果。
 *
 * <p>面板尺寸<b>自适应窗口</b>（宽高均钳制到窗口内），代码预览区 scissor 裁剪防溢出。</p>
 */
public class JvmCodeConfirmScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 560;
    private static final int PANEL_MIN_WIDTH = 280;
    private static final int PANEL_MAX_HEIGHT = 280;
    private static final int PANEL_MIN_HEIGHT = 190;
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 10;
    private static final int PANEL_BACKGROUND = 0xFF202124;
    private static final int PANEL_BORDER = 0xFFAA3333;
    private static final int MAX_PREVIEW_LINES = 12;

    private final Screen previousScreen;
    private final String codeSource;
    private final JvmCodeClassification classification;
    private final Consumer<Boolean> callback;
    private boolean decided;

    public JvmCodeConfirmScreen(Screen previousScreen, String codeSource, JvmCodeClassification classification,
                                Consumer<Boolean> callback) {
        super(Component.translatable("gui.herobrine_companion.jvm_code_skill.confirm.title"));
        this.previousScreen = previousScreen;
        this.codeSource = codeSource == null ? "" : codeSource;
        this.classification = classification == null ? JvmCodeClassification.DANGEROUS : classification;
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
            return top + height - 48;
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
                Component.translatable("gui.herobrine_companion.jvm_code_skill.confirm.execute"),
                ignored -> this.decide(true), null));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                buttonX + BUTTON_WIDTH + GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.jvm_code_skill.confirm.cancel"),
                ignored -> this.decide(false), null));
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        Panel p = panel();
        int contentLeft = p.contentLeft();
        int contentWidth = p.contentWidth();

        guiGraphics.fill(p.left(), p.top(), p.left() + p.width(), p.top() + p.height(), PANEL_BACKGROUND);
        guiGraphics.renderOutline(p.left(), p.top(), p.width(), p.height(), PANEL_BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, p.top() + 10, 0xFFFF7777);

        // 生命周期操作（会关闭/退出游戏）：顶部红字用大白话警告，看不懂代码也能明白后果
        boolean lifecycle = this.classification == JvmCodeClassification.LIFECYCLE;
        Component warning = lifecycle
                ? Component.translatable("gui.herobrine_companion.jvm_code_skill.confirm.lifecycle_warning")
                : Component.translatable("gui.herobrine_companion.jvm_code_skill.confirm.warning");
        int warningColor = lifecycle ? 0xFFFF5555 : 0xFFFFCC66;

        // 内容区 scissor 裁剪：警告 + 代码预览超面板时被截断，不溢出窗口。
        guiGraphics.enableScissor(p.left() + 1, p.top() + 26, p.left() + p.width() - 1, p.contentBottom());

        List<FormattedCharSequence> warningLines = this.font.split(warning, contentWidth);
        int warnY = p.top() + 28;
        for (FormattedCharSequence line : warningLines) {
            guiGraphics.drawString(this.font, line, contentLeft, warnY, warningColor, false);
            warnY += this.font.lineHeight + 1;
        }

        int codeLabelY = warnY + 4;
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.jvm_code_skill.confirm.code"),
                contentLeft, codeLabelY, 0xFFAAAAAA, false);

        int codeTop = codeLabelY + 14;
        String[] lines = this.codeSource.split("\n", -1);
        int shown = Math.min(lines.length, MAX_PREVIEW_LINES);
        int codeY = codeTop;
        for (int i = 0; i < shown; i++) {
            String text = this.font.plainSubstrByWidth(lines[i], contentWidth);
            guiGraphics.drawString(this.font, Component.literal(text), contentLeft, codeY, 0xFF88CCFF, false);
            codeY += this.font.lineHeight + 1;
        }
        if (lines.length > shown) {
            guiGraphics.drawString(this.font,
                    Component.literal("… (" + (lines.length - shown) + " more lines)"),
                    contentLeft, codeY, 0xFF888888, false);
        }

        guiGraphics.disableScissor();

        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.herobrine_companion.jvm_code_skill.confirm.full_access"),
                this.width / 2, p.top() + p.height() - 44, 0xFF999999);

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
