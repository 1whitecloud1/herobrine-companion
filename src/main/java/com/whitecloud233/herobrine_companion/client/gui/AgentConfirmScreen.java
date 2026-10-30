package com.whitecloud233.herobrine_companion.client.gui;

import com.whitecloud233.herobrine_companion.network.ApproveToolPacket;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.RejectToolPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.Map;
import java.util.UUID;

/**
 * agent 工具确认屏（P3，单一职责：向玩家展示待确认的工具调用并征求同意/拒绝）。
 * 确认 → 发 {@link ApproveToolPacket}；拒绝 / 关屏 → 发 {@link RejectToolPacket}。
 *
 * <p>面板几何、按钮对、压暗背景、关屏即拒绝等骨架见 {@link HeroConfirmScreen}；
 * 本类只负责"把待确认的工具与参数画出来"和"把决策转成网络包"。</p>
 */
public class AgentConfirmScreen extends HeroConfirmScreen {
    private static final int PANEL_MAX_WIDTH = 520;
    private static final int PANEL_MIN_WIDTH = 240;
    private static final int PANEL_MAX_HEIGHT = 260;
    private static final int PANEL_MIN_HEIGHT = 160;
    private static final int MAX_ARG_LINES = 6;

    private final UUID requestId;
    private final String toolId;
    private final Map<String, String> args;
    private final Component description;

    public AgentConfirmScreen(Screen previousScreen, UUID requestId, String toolId, Map<String, String> args,
                              Component description) {
        super(Component.translatable("gui.herobrine_companion.agent_confirm.title"), previousScreen,
                approved -> sendDecision(requestId, approved));
        this.requestId = requestId;
        this.toolId = toolId == null ? "" : toolId;
        this.args = args == null ? Map.of() : args;
        this.description = description == null ? Component.empty() : description;
    }

    @Override
    protected int[] panelSize() {
        return new int[]{PANEL_MAX_WIDTH, PANEL_MIN_WIDTH, PANEL_MAX_HEIGHT, PANEL_MIN_HEIGHT};
    }

    @Override
    protected Component approveLabel() {
        return Component.translatable("gui.herobrine_companion.agent_confirm.approve");
    }

    @Override
    protected Component cancelLabel() {
        return Component.translatable("gui.herobrine_companion.agent_confirm.reject");
    }

    @Override
    protected int contentBottomMargin() {
        return 42;
    }

    @Override
    protected Component footerHint() {
        return Component.translatable("gui.herobrine_companion.agent_confirm.hint");
    }

    @Override
    protected void renderContent(GuiGraphics guiGraphics, int contentLeft, int contentTop, int contentWidth) {
        int y = contentTop;
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.agent_confirm.tool", this.toolId),
                contentLeft, y, 0xFFFFCC66, false);
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
    }

    private static void sendDecision(UUID requestId, boolean approved) {
        if (approved) {
            PacketHandler.sendToServer(new ApproveToolPacket(requestId));
        } else {
            PacketHandler.sendToServer(new RejectToolPacket(requestId));
        }
    }
}
