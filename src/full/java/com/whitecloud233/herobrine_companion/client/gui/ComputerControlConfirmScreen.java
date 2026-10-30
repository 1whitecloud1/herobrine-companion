package com.whitecloud233.herobrine_companion.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 电脑控制确认屏：向玩家展示将被执行的本机动作并征求确认。
 *
 * <p>面板几何、按钮对、压暗背景、关屏即拒绝等骨架见 {@link HeroConfirmScreen}；
 * 本类只负责画"警告 + 动作描述 + 命令预览"。</p>
 */
public class ComputerControlConfirmScreen extends HeroConfirmScreen {
    private static final int PANEL_MAX_WIDTH = 440;
    private static final int PANEL_MIN_WIDTH = 240;
    private static final int PANEL_MAX_HEIGHT = 220;
    private static final int PANEL_MIN_HEIGHT = 160;

    private final Component actionDescription;
    private final String commandPreview;

    public ComputerControlConfirmScreen(Screen previousScreen, Component actionDescription, String commandPreview,
                                        Consumer<Boolean> callback) {
        super(Component.translatable("gui.herobrine_companion.computer_control.confirm.title"), previousScreen, callback);
        this.actionDescription = actionDescription;
        this.commandPreview = commandPreview == null ? "" : commandPreview;
    }

    @Override
    protected int[] panelSize() {
        return new int[]{PANEL_MAX_WIDTH, PANEL_MIN_WIDTH, PANEL_MAX_HEIGHT, PANEL_MIN_HEIGHT};
    }

    @Override
    protected Component approveLabel() {
        return Component.translatable("gui.herobrine_companion.computer_control.confirm.execute");
    }

    @Override
    protected Component cancelLabel() {
        return Component.translatable("gui.herobrine_companion.computer_control.confirm.cancel");
    }

    @Override
    protected int contentBottomMargin() {
        return 46;
    }

    @Override
    protected Component footerHint() {
        return Component.translatable("gui.herobrine_companion.computer_control.confirm.no_admin");
    }

    @Override
    protected void renderContent(GuiGraphics guiGraphics, int contentLeft, int contentTop, int contentWidth) {
        guiGraphics.drawWordWrap(this.font,
                Component.translatable("gui.herobrine_companion.computer_control.confirm.warning"),
                contentLeft, contentTop, contentWidth, 0xFFFFCC66);
        guiGraphics.drawWordWrap(this.font, this.actionDescription,
                contentLeft, contentTop + 36, contentWidth, 0xFFFFFFFF);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.computer_control.confirm.command"),
                contentLeft, contentTop + 78, 0xFFAAAAAA, false);
        guiGraphics.drawWordWrap(this.font, Component.literal(this.commandPreview),
                contentLeft, contentTop + 92, contentWidth, 0xFF88CCFF);
    }
}
