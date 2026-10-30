package com.whitecloud233.modid.herobrine_companion.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 二次确认弹窗的公共骨架。
 *
 * <p>项目里有三个确认弹窗：agent 工具确认、电脑控制确认、JVM 代码确认。它们的差异只在
 * 「正文画什么」与「同意后做什么」，其余部分（自适应面板几何、同意/取消按钮对、
 * 整屏压暗背景、关屏即拒绝、不暂停游戏）此前是三份逐字复制的代码。</p>
 *
 * <p><b>决策语义统一在这里</b>：无论玩家点按钮、按 ESC 还是切走界面，
 * 都<b>恰好</b>回调一次 {@code callback}；{@code approved=true} 只可能来自同意按钮。
 * 三个弹窗原先各自维护 {@code decided} 标志位来实现这一点，现在只剩基类这一份。</p>
 */
public abstract class HeroConfirmScreen extends Screen {

    /** 面板底色（比 HeroTheme.BG_MAIN 更深，确认弹窗要压过世界）。 */
    protected static final int PANEL_BACKGROUND = 0xFF202124;
    /** 危险操作用红色外框，刻意不复用主题灰。 */
    protected static final int PANEL_BORDER = 0xFFAA3333;

    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 10;
    /** 按钮区在面板底部向上留出的高度。 */
    private static final int BUTTON_BOTTOM_MARGIN = 34;

    private final Screen previousScreen;
    private final Consumer<Boolean> callback;
    private boolean decided;

    protected HeroConfirmScreen(Component title, Screen previousScreen, Consumer<Boolean> callback) {
        super(title);
        this.previousScreen = previousScreen;
        this.callback = callback;
    }

    // ---- 子类需要实现的差异点 ----

    /** 设计尺寸与最小尺寸，用于 {@link #panel()} 的钳制。 */
    protected abstract int[] panelSize();

    /** 按钮文字（同意 / 取消）。 */
    protected abstract Component approveLabel();

    protected abstract Component cancelLabel();

    /** 内容区底边距：决定 scissor 的下界，各弹窗因内容行数不同取值不同。 */
    protected abstract int contentBottomMargin();

    /** 在面板内绘制正文。已处于 scissor 裁剪范围内，坐标直接用绝对屏幕坐标。 */
    protected abstract void renderContent(GuiGraphics guiGraphics, int contentLeft, int contentTop, int contentWidth);

    /** 底部提示文字（可为 null）。 */
    protected Component footerHint() {
        return null;
    }

    // ---- 公共骨架 ----

    /** 自适应面板几何：每次由窗口尺寸现算，保证不溢出。 */
    protected record Panel(int left, int top, int width, int height) {
        public int contentLeft() {
            return left + 16;
        }

        public int contentWidth() {
            return width - 32;
        }

        public int contentTop() {
            return top + 34;
        }
    }

    protected final Panel panel() {
        int[] size = panelSize();
        int w = Math.min(size[0], Math.max(size[1], this.width - 24));
        int h = Math.min(size[2], Math.max(size[3], this.height - 24));
        return new Panel((this.width - w) / 2, Math.max(4, (this.height - h) / 2), w, h);
    }

    @Override
    protected void init() {
        Panel p = panel();
        int buttonY = p.top() + p.height() - BUTTON_BOTTOM_MARGIN;
        int buttonGroupWidth = BUTTON_WIDTH * 2 + GAP;
        int buttonX = p.left() + (p.width() - buttonGroupWidth) / 2;

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                approveLabel(), ignored -> this.decide(true), null));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                buttonX + BUTTON_WIDTH + GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                cancelLabel(), ignored -> this.decide(false), null));
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
        HeroTheme.dimOverlay(guiGraphics, this.width, this.height);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        Panel p = panel();

        HeroTheme.plainPanel(guiGraphics, p.left(), p.top(), p.width(), p.height(), PANEL_BACKGROUND);
        guiGraphics.renderOutline(p.left(), p.top(), p.width(), p.height(), PANEL_BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, p.top() + 11, 0xFFFF7777);

        int contentBottom = p.top() + p.height() - contentBottomMargin();
        guiGraphics.enableScissor(p.left() + 1, p.top() + 26, p.left() + p.width() - 1, contentBottom);
        renderContent(guiGraphics, p.contentLeft(), p.contentTop(), p.contentWidth());
        guiGraphics.disableScissor();

        Component hint = footerHint();
        if (hint != null) {
            guiGraphics.drawCenteredString(this.font, hint, this.width / 2, p.top() + p.height() - 42, 0xFF999999);
        }

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

    /** 结算一次决策；重复调用只生效第一次。 */
    protected final void decide(boolean approved) {
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
