package com.whitecloud233.modid.herobrine_companion.client.gui;

import com.whitecloud233.modid.herobrine_companion.client.jvm.JvmCodeClassification;
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
 * <p>面板几何、按钮对、压暗背景、关屏即拒绝等骨架见 {@link HeroConfirmScreen}；
 * 本类只负责画"警告 + 代码预览"。</p>
 */
public class JvmCodeConfirmScreen extends HeroConfirmScreen {
    private static final int PANEL_MAX_WIDTH = 560;
    private static final int PANEL_MIN_WIDTH = 280;
    private static final int PANEL_MAX_HEIGHT = 280;
    private static final int PANEL_MIN_HEIGHT = 190;
    private static final int MAX_PREVIEW_LINES = 12;

    private final String codeSource;
    private final JvmCodeClassification classification;

    public JvmCodeConfirmScreen(Screen previousScreen, String codeSource, JvmCodeClassification classification,
                                Consumer<Boolean> callback) {
        super(Component.translatable("gui.herobrine_companion.jvm_code_skill.confirm.title"), previousScreen, callback);
        this.codeSource = codeSource == null ? "" : codeSource;
        this.classification = classification == null ? JvmCodeClassification.DANGEROUS : classification;
    }

    @Override
    protected int[] panelSize() {
        return new int[]{PANEL_MAX_WIDTH, PANEL_MIN_WIDTH, PANEL_MAX_HEIGHT, PANEL_MIN_HEIGHT};
    }

    @Override
    protected Component approveLabel() {
        return Component.translatable("gui.herobrine_companion.jvm_code_skill.confirm.execute");
    }

    @Override
    protected Component cancelLabel() {
        return Component.translatable("gui.herobrine_companion.jvm_code_skill.confirm.cancel");
    }

    @Override
    protected int contentBottomMargin() {
        return 48;
    }

    @Override
    protected Component footerHint() {
        return Component.translatable("gui.herobrine_companion.jvm_code_skill.confirm.full_access");
    }

    @Override
    protected void renderContent(GuiGraphics guiGraphics, int contentLeft, int contentTop, int contentWidth) {
        // 生命周期操作（会关闭/退出游戏）：顶部红字用大白话警告，看不懂代码也能明白后果
        boolean lifecycle = this.classification == JvmCodeClassification.LIFECYCLE;
        Component warning = lifecycle
                ? Component.translatable("gui.herobrine_companion.jvm_code_skill.confirm.lifecycle_warning")
                : Component.translatable("gui.herobrine_companion.jvm_code_skill.confirm.warning");
        int warningColor = lifecycle ? 0xFFFF5555 : 0xFFFFCC66;

        // contentTop 已经包含面板顶部留白，这里比旧实现少 6px 的硬编码偏移
        int warnY = contentTop - 6;
        List<FormattedCharSequence> warningLines = this.font.split(warning, contentWidth);
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
    }
}
