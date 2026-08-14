package com.whitecloud233.herobrine_companion.client.gui;

import com.whitecloud233.herobrine_companion.network.ApproveToolPacket;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.RejectToolPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * agent 工具确认屏（P3，单一职责：向玩家展示待确认的工具调用并征求同意/拒绝）。
 * 确认 → 发 {@link ApproveToolPacket}；拒绝 / 关屏 → 发 {@link RejectToolPacket}。
 *
 * <p>面板尺寸<b>自适应窗口</b>（跟随 {@code this.width/height}，缩到能放进窗口），
 * 内容区用 scissor 裁剪防溢出——与 AI 调试页等 LLM 相关界面一致。</p>
 */
public class AgentConfirmScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 520;
    private static final int PANEL_MIN_WIDTH = 240;
    private static final int PANEL_MAX_HEIGHT = 260;
    private static final int PANEL_MIN_HEIGHT = 160;
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 10;
    private static final int PANEL_BACKGROUND = 0xFF202124;
    private static final int PANEL_BORDER = 0xFFAA3333;
    private static final int MAX_ARG_LINES = 6;

    private final Screen previousScreen;
    private final UUID requestId;
    private final String toolId;
    private final Map<String, String> args;
    private final Component description;
    private boolean decided;

    /** 自适应面板几何（每次由窗口尺寸现算，保证不溢出）。 */
    private record Panel(int left, int top, int width, int height) {
        int contentLeft() {
            return left + 16;
        }

        int contentWidth() {
            return width - 32;
        }

        /** 内容区下界（按钮之上，供 scissor 裁剪）。 */
        int contentBottom() {
            return top + height - 42;
        }
    }

    public AgentConfirmScreen(Screen previousScreen, UUID requestId, String toolId, Map<String, String> args, Component description) {
        super(Component.translatable("gui.herobrine_companion.agent_confirm.title"));
        this.previousScreen = previousScreen;
        this.requestId = requestId;
        this.toolId = toolId == null ? "" : toolId;
        this.args = args == null ? Map.of() : args;
        this.description = description == null ? Component.empty() : description;
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
                Component.translatable("gui.herobrine_companion.agent_confirm.approve"),
                ignored -> this.decide(true), null));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                buttonX + BUTTON_WIDTH + GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.agent_confirm.reject"),
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

        // 内容区 scissor 裁剪：内容超出面板时被截断，不溢出窗口。
        guiGraphics.enableScissor(p.left() + 1, p.top() + 26, p.left() + p.width() - 1, p.contentBottom());

        int y = p.top() + 34;
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.agent_confirm.tool", this.toolId), contentLeft, y, 0xFFFFCC66, false);
        y += this.font.lineHeight + 4;

        for (FormattedCharSequence line : this.font.split(this.description, contentWidth)) {
            guiGraphics.drawString(this.font, line, contentLeft, y, 0xFFCCCCCC, false);
            y += this.font.lineHeight + 1;
        }
        y += 4;

        if (!this.args.isEmpty()) {
            guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.agent_confirm.args"),
                    contentLeft, y, 0xFFAAAAAA, false);
            y += this.font.lineHeight + 1;
            int shown = 0;
            for (Map.Entry<String, String> entry : this.args.entrySet()) {
                if (shown++ >= MAX_ARG_LINES) {
                    break;
                }
                String value = this.font.plainSubstrByWidth(entry.getValue(), contentWidth - 60);
                guiGraphics.drawString(this.font, Component.literal("  " + entry.getKey() + " = " + value),
                        contentLeft, y, 0xFF88CCFF, false);
                y += this.font.lineHeight + 1;
            }
        }

        guiGraphics.disableScissor();

        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.herobrine_companion.agent_confirm.hint"),
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
            sendDecision(false);
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
        sendDecision(approved);
    }

    private void sendDecision(boolean approved) {
        if (approved) {
            PacketHandler.sendToServer(new ApproveToolPacket(this.requestId));
        } else {
            PacketHandler.sendToServer(new RejectToolPacket(this.requestId));
        }
    }
}
