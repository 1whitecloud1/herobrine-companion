package com.whitecloud233.herobrine_companion.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 竖滚动条的几何与命中测试（单一职责：给定"轨道 + 内容长度 + 当前偏移"，回答
 * 滑块在哪、某点是否在轨道上、拖到某处应对应多少偏移）。
 *
 * <p>项目里此前有 9 处各自实现这套换算：{@code ApiKeyInputScreen} 有一份完整的
 * （含拖拽状态机），{@code LLMRoutingScreen}、{@code LocalModelScreen}、
 * {@code HeroPoseScreen}、{@code HeroRewardScreen}、{@code HeroTradeScreen}、
 * {@code CrossChatGuideScreen}、{@code CrossSessionHubScreen}、
 * {@code CrossSessionArchiveScreen} 各有简化版。而 {@link HeroTheme#scrollbar}
 * 只负责"画两根竖条"，没有任何交互能力，因此只有 1 处在用。</p>
 *
 * <p><b>绘制与状态分离</b>：本类是不可变值对象，只回答几何问题；拖拽状态
 * （{@code dragging}、抓取偏移）留在各 Screen 里，因为它们的鼠标事件入口各不相同。
 * 这样既能消除换算公式的九份副本，又不会把 UI 生命周期强行塞进一个工具类。</p>
 *
 * <p><b>两种宽度并存</b>：{@code ApiKeyInputScreen} 用 4px 宽滑块，
 * 其余页面是 6px。宽度由构造参数决定，默认 4px。</p>
 */
public final class HeroScrollbar {

    /** 默认滑块宽度（与 {@link HeroTheme#scrollbar} 一致）。 */
    public static final int DEFAULT_WIDTH = 4;
    /** 滑块最小高度：太短的滑块几乎无法用鼠标抓住。 */
    public static final int MIN_THUMB_HEIGHT = 18;

    private final int x;
    private final int trackTop;
    private final int trackHeight;
    private final int width;
    private final int thumbY;
    private final int thumbHeight;
    private final int maxScroll;

    private HeroScrollbar(int x, int trackTop, int trackHeight, int width,
                          int thumbY, int thumbHeight, int maxScroll) {
        this.x = x;
        this.trackTop = trackTop;
        this.trackHeight = trackHeight;
        this.width = width;
        this.thumbY = thumbY;
        this.thumbHeight = thumbHeight;
        this.maxScroll = maxScroll;
    }

    /**
     * 计算滚动条几何。
     *
     * @param x          轨道左边界
     * @param trackTop   轨道上边界
     * @param trackHeight 轨道高度（即可视高度）
     * @param maxScroll  内容可滚动的最大偏移量；{@code <=0} 表示不需要滚动条
     * @param scrollOffset 当前偏移
     */
    public static HeroScrollbar of(int x, int trackTop, int trackHeight, int maxScroll, int scrollOffset) {
        return of(x, DEFAULT_WIDTH, trackTop, trackHeight, maxScroll, scrollOffset);
    }

    public static HeroScrollbar of(int x, int width, int trackTop, int trackHeight, int maxScroll, int scrollOffset) {
        if (maxScroll <= 0 || trackHeight <= 0) {
            return new HeroScrollbar(x, trackTop, trackHeight, width, 0, 0, Math.max(0, maxScroll));
        }
        // 滑块高度按"可视 / 总内容"的比例缩放，下限保证可抓取。
        int thumbHeight = Math.min(trackHeight, Math.max(MIN_THUMB_HEIGHT,
                (int) ((long) trackHeight * trackHeight / (trackHeight + maxScroll))));
        int travel = trackHeight - thumbHeight;
        int clampedOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        int thumbY = trackTop + (travel == 0 ? 0 : (int) ((long) travel * clampedOffset / maxScroll));
        return new HeroScrollbar(x, trackTop, trackHeight, width, thumbY, thumbHeight, maxScroll);
    }

    public int x() {
        return x;
    }

    public int trackTop() {
        return trackTop;
    }

    public int trackHeight() {
        return trackHeight;
    }

    public int width() {
        return width;
    }

    public int thumbY() {
        return thumbY;
    }

    public int thumbHeight() {
        return thumbHeight;
    }

    public int maxScroll() {
        return maxScroll;
    }

    /** 是否需要绘制/响应滚动条。 */
    public boolean isActive() {
        return maxScroll > 0 && trackHeight > 0;
    }

    /** 鼠标是否落在滑块上（用于 hover 高亮）。 */
    public boolean isOverThumb(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= thumbY && mouseY < thumbY + thumbHeight;
    }

    /** 鼠标是否落在整条轨道上（用于开始拖拽）。 */
    public boolean isOverTrack(double mouseX, double mouseY) {
        return isActive()
                && mouseX >= x && mouseX < x + width
                && mouseY >= trackTop && mouseY < trackTop + trackHeight;
    }

    /** 给定滑块顶端位置，反解出对应的滚动偏移。 */
    public int scrollOffsetForThumbTop(double thumbTop) {
        int travel = trackHeight - thumbHeight;
        if (travel <= 0 || maxScroll <= 0) {
            return 0;
        }
        int offset = (int) Math.round((thumbTop - trackTop) * maxScroll / travel);
        return Math.max(0, Math.min(maxScroll, offset));
    }

    /**
     * 绘制轨道与滑块。
     *
     * @param dragging 正在拖拽（滑块最亮）
     * @param hovered  鼠标悬停在滑块上
     */
    public void render(GuiGraphics graphics, boolean dragging, boolean hovered) {
        if (!isActive()) {
            return;
        }
        graphics.fill(x, trackTop, x + width, trackTop + trackHeight, 0x80333333);
        int thumbColor = dragging ? 0xFFE0E0E0 : (hovered ? 0xFFC8C8C8 : 0xFF8A8A8A);
        graphics.fill(x, thumbY, x + width, thumbY + thumbHeight, thumbColor);
    }
}
