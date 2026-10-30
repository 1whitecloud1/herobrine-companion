package com.whitecloud233.herobrine_companion.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * Herobrine 界面统一视觉规范（以 {@link HeroScreen} 为准）。
 *
 * <p>面板底色 / 侧栏色 / 边框 / 文字色 / 标题强调条这些常量原先分散在各个页面里，
 * 各写一套近似值（0xFF2B2B2B、0xFF3C3F41 反复硬编码），改一处就要全工程搜替换。
 * 这里集中成唯一来源，新页面只要调用 {@link #panel} / {@link #titleBar} / {@link #section}
 * 就自动与 HeroScreen 长得一样。</p>
 *
 * <p>只做绘制与配色，不持有页面状态：所有方法都是无状态的静态工具。</p>
 */
public final class HeroTheme {

    // ---- 基础配色（与 HeroScreen 完全一致）----
    /** 主内容区底色。 */
    public static final int BG_MAIN = 0xFF2B2B2B;
    /** 主内容区底色（半透明，用于看得见世界背景的浮层面板）。 */
    public static final int BG_MAIN_TRANSLUCENT = 0xEE2B2B2B;
    /** 侧栏 / 顶栏 / 底栏底色。 */
    public static final int BG_SIDE = 0xFF3C3F41;
    /** 面板外框。 */
    public static final int BORDER = 0xFF555555;
    /** 正文文字。 */
    public static final int TEXT = 0xFFA9B7C6;
    /** 字段名（橙）。 */
    public static final int LABEL = 0xFFCC7832;
    /** 次级字段名（紫）。 */
    public static final int VALUE = 0xFF9876AA;
    /** 字段值 / 提示（绿）。 */
    public static final int INFO = 0xFF6A8759;
    /** 标题栏强调条（蓝）。 */
    public static final int ACCENT = 0xFF4A88C7;
    /** 内嵌深色块（编辑区 / 列表底）。 */
    public static final int SUNKEN = 0xFF1E1E1E;
    /** 悬停高亮。 */
    public static final int HOVER = 0xFF4C5052;
    /** 警告红。 */
    public static final int WARN = 0xFFFF8080;
    /** 成功绿。 */
    public static final int GOOD = 0xFF55FF55;
    /** 不可用 / 占位灰。 */
    public static final int DISABLED = 0xFF808080;
    /** 输入框聚焦边框（与 MultilineTextEditor 的聚焦色一致）。 */
    public static final int FOCUS = 0xFF6A9FB5;
    /** 二次确认弹窗的世界压暗遮罩。 */
    public static final int DIM = 0xB0000000;

    /** 顶部标题栏高度（HeroScreen 的 topBarHeight）。 */
    public static final int TITLE_BAR_HEIGHT = 25;
    /** 底部操作栏高度（HeroScreen 的 bottomBarHeight）。 */
    public static final int FOOTER_HEIGHT = 20;
    /** 标题栏左侧强调块宽度。 */
    private static final int TITLE_ACCENT_WIDTH = 140;

    private HeroTheme() {
    }

    /** 面板几何：外层框 + 标题栏 + 底部栏。 */
    public record Frame(int left, int top, int width, int height) {
        /** 内容区左边界（留 5px 内边距）。 */
        public int contentLeft() {
            return this.left + 5;
        }

        /** 内容区上边界（标题栏下 5px）。 */
        public int contentTop() {
            return this.top + TITLE_BAR_HEIGHT + 5;
        }

        /** 内容区宽度（左右各 5px 内边距）。 */
        public int contentWidth() {
            return Math.max(1, this.width - 10);
        }

        /** 内容区下边界（底部栏上 5px）。 */
        public int contentBottom() {
            return this.top + this.height - FOOTER_HEIGHT - 5;
        }

        /** 内容区高度。 */
        public int contentHeight() {
            return Math.max(1, this.contentBottom() - this.contentTop());
        }

        /** 底部栏按钮的 Y（按钮高 16，垂直居中于底栏）。 */
        public int footerButtonY() {
            return this.top + this.height - FOOTER_HEIGHT + 2;
        }

        /** 水平中心。 */
        public int centerX() {
            return this.left + this.width / 2;
        }
    }

    /**
     * 按窗口钳制出居中的面板几何。
     *
     * @param baseWidth   设计宽度
     * @param baseHeight  设计高度
     * @param minWidth    允许压缩到的最小宽度
     * @param minHeight   允许压缩到的最小高度
     * @param screenWidth  当前窗口宽
     * @param screenHeight 当前窗口高
     */
    public static Frame frame(int screenWidth, int screenHeight, int baseWidth, int baseHeight,
                              int minWidth, int minHeight, int margin) {
        int availableWidth = Math.max(1, screenWidth - margin * 2);
        int availableHeight = Math.max(1, screenHeight - margin * 2);
        int width = clampPanel(baseWidth, minWidth, availableWidth);
        int height = clampPanel(baseHeight, minHeight, availableHeight);
        int left = Math.max(margin, (screenWidth - width) / 2);
        int top = Math.max(margin, (screenHeight - height) / 2);
        return new Frame(left, top, width, height);
    }

    /** 优先取设计尺寸，窗口不够时退到最小尺寸，再不够就让位给可用空间。 */
    private static int clampPanel(int base, int min, int available) {
        int preferred = Math.min(base, available);
        return Math.max(Math.min(min, available), preferred);
    }

    /**
     * 「最小值 / 最大值 / 可用空间」三选一的钳制原语。
     *
     * <p>{@link #frame} 用它算面板宽高；页面内算内容区宽度（没有设计尺寸概念）时也直接用它，
     * 不必再各写一份 {@code Math.max(Math.min(...))}。</p>
     */
    public static int clampToAvailable(int min, int max, int available) {
        int clamped = Math.min(max, available);
        return Math.max(Math.min(min, available), clamped);
    }

    /**
     * 绘制面板：底色、外框、顶部标题栏、标题文字与蓝色强调条、底部操作栏。
     *
     * <p>与 HeroScreen 的层叠顺序一致：先铺满底色，再压上侧栏色块，
     * 最后描边，保证边框永远压在最上层不被内容盖住。</p>
     */
    public static void panel(GuiGraphics graphics, Font font, Frame frame, Component title) {
        // 主内容区
        graphics.fill(frame.left(), frame.top(), frame.left() + frame.width(), frame.top() + frame.height(), BG_MAIN);
        // 顶部标题栏 + 底部操作栏
        graphics.fill(frame.left(), frame.top(), frame.left() + frame.width(), frame.top() + TITLE_BAR_HEIGHT, BG_SIDE);
        graphics.fill(frame.left(), frame.top() + frame.height() - FOOTER_HEIGHT,
                frame.left() + frame.width(), frame.top() + frame.height(), BG_SIDE);
        // 外框 + 标题栏/内容区/底栏之间的分隔线
        graphics.renderOutline(frame.left(), frame.top(), frame.width(), frame.height(), BORDER);
        int dividerRight = frame.left() + frame.width() - 1;
        graphics.fill(frame.left() + 1, frame.top() + TITLE_BAR_HEIGHT, dividerRight, frame.top() + TITLE_BAR_HEIGHT + 1, BORDER);
        graphics.fill(frame.left() + 1, frame.top() + frame.height() - FOOTER_HEIGHT - 1,
                dividerRight, frame.top() + frame.height() - FOOTER_HEIGHT, BORDER);
        // 标题栏左侧蓝色强调条
        int accentWidth = Math.min(TITLE_ACCENT_WIDTH, Math.max(60, frame.width() - 20));
        graphics.fill(frame.left() + 1, frame.top() + 1, frame.left() + 1 + accentWidth, frame.top() + 3, ACCENT);
        // 标题
        if (title != null) {
            graphics.drawString(font, title, frame.left() + 8, frame.top() + 8, TEXT, false);
        }
    }

    /**
     * 绘制「扁平面板」：只有底色 + 外框，没有标题栏/底栏/强调条。
     *
     * <p>项目里有 19 处界面是这种形态（crosschat 三屏、对话管理、LLM 各设置页、
     * 英雄交互弹窗、三个确认弹窗、厨具选择屏……），原先各自写
     * {@code fill(...) + renderOutline(...)} 两行。</p>
     *
     * @param background 底色（需要半透明时传 {@link #BG_MAIN_TRANSLUCENT}）
     */
    public static void plainPanel(GuiGraphics graphics, int left, int top, int width, int height, int background) {
        graphics.fill(left, top, left + width, top + height, background);
        graphics.renderOutline(left, top, width, height, BORDER);
    }

    /** 扁平面板，使用标准不透明底色。 */
    public static void plainPanel(GuiGraphics graphics, int left, int top, int width, int height) {
        plainPanel(graphics, left, top, width, height, BG_MAIN);
    }

    /** 扁平面板 + 顶部居中标题。 */
    public static void plainPanel(GuiGraphics graphics, Font font,
                                  int left, int top, int width, int height, Component title) {
        plainPanel(graphics, font, left, top, width, height, BG_MAIN, title);
    }

    /** 扁平面板（指定底色）+ 顶部居中标题。 */
    public static void plainPanel(GuiGraphics graphics, Font font,
                                  int left, int top, int width, int height, int background, @Nullable Component title) {
        plainPanel(graphics, left, top, width, height, background);
        if (title != null) {
            graphics.drawCenteredString(font, title, left + width / 2, top + 12, LABEL);
        }
    }
    /** 表单字段名（HeroScreen 的橙色标签）。 */
    public static void fieldLabel(GuiGraphics graphics, Font font, int x, int y, Component label) {
        graphics.drawString(font, label, x, y, LABEL, false);
    }

    /**
     * 深色内嵌区块（编辑区 / 列表容器）。
     *
     * <p>注意：HeroScreen 风格里深色块只出现在侧栏信息框，因此这里不带外描边，
     * 由调用方按需再描一次 {@link #BORDER}。</p>
     */
    public static void sunken(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, SUNKEN);
    }

    /** 细分割线。 */
    public static void divider(GuiGraphics graphics, int x1, int x2, int y) {
        graphics.fill(x1, y, x2, y + 1, BORDER);
    }

    /**
     * 输入框的主题描边。
     *
     * <p>配合 {@code EditBox} 自带的 1px 外圈边使用：原版在 {@code (x-1, y-1)} 处按
     * {@code width+2 × height+2} 描边，这里用同一几何换色，等于把原版描边替换成主题色，
     * 从而既保留原版的垂直居中排版，又拿到统一外观。</p>
     */
    public static void outlineInput(GuiGraphics graphics, int x, int y, int width, int height,
                                    boolean focused, boolean editable) {
        int border = focused && editable ? FOCUS : BORDER;
        // 原版描边是 (x-1,y-1) 起、size+2，覆盖它需要同样的外扩一圈。
        graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, border);
        // 描边外侧再补一层面板底色，压掉原版黑框可能露出的 1px 残边。
        graphics.renderOutline(x - 2, y - 2, width + 4, height + 4, BG_MAIN);
    }

    /**
     * 把已排队的批次立刻提交。
     *
     * <p>1.20.1 的 {@code GuiGraphics} 会把文字攒进 {@code Font} 批次、实心块攒进 {@code gui()}
     * 批次延迟提交。两条队列不完全等价于调用顺序，单纯"后画一个 fill"不保证盖住先前排队的文字。</p>
     *
     * <p>注意 flush 只是必要条件、不是充分条件：{@code EditBox} 等控件仍可能以别的批次顺序
     * 把文字排到浮层之上。要彻底避免穿透，调用方应在浮层打开时把被覆盖的控件
     * {@code visible = false}（见 {@code ApiKeyInputScreen#syncOverlayOcclusion}）。</p>
     */
    public static void flush(GuiGraphics graphics) {
        graphics.flush();
        RenderSystem.enableBlend();
    }

    /**
     * 统一的"下拉/浮层"外框：先 flush 旧批次，再铺不透明底 + 描边。
     *
     * <p>配合"隐藏被覆盖控件"使用：flush 负责把已有内容落到帧缓冲，
     * 隐藏控件负责让不该出现的文字根本不排队。</p>
     */
    public static void overlay(GuiGraphics graphics, int x, int y, int width, int height) {
        flush(graphics);
        graphics.fill(x, y, x + width, y + height, SUNKEN);
        graphics.renderOutline(x, y, width, height, BORDER);
    }

    /** 浮层里一行的高亮底色。 */
    public static void overlayRow(GuiGraphics graphics, int x, int y, int width, int height,
                                  boolean selected, boolean hovered) {
        if (selected) {
            graphics.fill(x, y, x + width, y + height, 0xFF3A5A3C);
        } else if (hovered) {
            graphics.fill(x, y, x + width, y + height, HOVER);
        }
    }

    /**
     * 竖滚动条（HeroScreen 系列页面统一样式）。
     *
     * @param trackX  轨道左边界
     * @param trackY  轨道上边界
     * @param trackHeight 轨道高度
     * @param thumbY  滑块上边界（-1 表示不显示）
     * @param thumbHeight 滑块高度
     */
    /**
     * 竖滚动条（HeroScreen 系列页面统一样式）。
     *
     * <p>只负责"画"，不含交互。需要拖拽/命中测试的页面请直接用 {@link HeroScrollbar}
     * （它把同一套换算与绘制封装在一起）。</p>
     *
     * @param trackX  轨道左边界
     * @param trackY  轨道上边界
     * @param trackHeight 轨道高度
     * @param thumbY  滑块上边界（-1 表示不显示）
     * @param thumbHeight 滑块高度
     */
    public static void scrollbar(GuiGraphics graphics, int trackX, int trackY, int trackHeight,
                                 int thumbY, int thumbHeight, boolean active) {
        graphics.fill(trackX, trackY, trackX + HeroScrollbar.DEFAULT_WIDTH, trackY + trackHeight, 0xFF333333);
        if (thumbY >= 0) {
            graphics.fill(trackX, thumbY, trackX + HeroScrollbar.DEFAULT_WIDTH, thumbY + thumbHeight,
                    active ? 0xFFE0E0E0 : 0xFF8A8A8A);
        }
    }

    /** 按像素宽度截断，超出补省略号。 */
    public static String clip(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width("…");
        return font.plainSubstrByWidth(text, Math.max(1, maxWidth - ellipsisWidth)) + "…";
    }

    /**
     * 按字符数截断，超出补省略号（用于固定宽度等宽/日志类文本，字符数与像素宽度近似线性）。
     *
     * <p>与 {@link #clip} 的区别：这里不查字体，调用方已知一字符≈固定像素的场景
     * （调试日志、单行摘要）用它更省，且不会因西文/中文混排宽度差而抖动。</p>
     */
    public static String clipChars(String text, int maxChars) {
        if (text == null || text.isEmpty() || maxChars <= 0) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…";
    }

    /**
     * 二次确认弹窗的背景：整屏压暗。
     *
     * <p>确认弹窗浮在世界之上，既不能保留原版模糊（读不清），也不该全空（和世界糊在一起），
     * 因此统一走半透明黑。</p>
     */
    public static void dimOverlay(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, DIM);
    }
}
