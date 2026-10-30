package com.whitecloud233.modid.herobrine_companion.config;

/**
 * TTS 设置页的布局计算（纯几何，不碰任何控件状态）。
 *
 * <p>面板高度按"实际可见行数"收敛，切换引擎时不会有整块空白；
 * 列位置统一由 {@link #columnX} / {@link #columnWidth} 计算，
 * 同一行的多块控件按列索引排布，从几何上避免重叠。</p>
 */
record TtsLayout(
        int startX,
        int startY,
        int panelWidth,
        int panelHeight,
        int contentX,
        int contentWidth,
        int rowsTop,
        int rowHeight,
        int hintY,
        int actionY
) {

    static final int BUTTON_HEIGHT = 20;
    private static final int ROW_HEIGHT = 20;
    private static final int MIN_ROW_HEIGHT = 14;
    private static final int GAP = 6;
    /** 左侧标签列宽度：要容纳"模型 / 音色"这类较长标签。 */
    private static final int LABEL_WIDTH = 78;
    private static final int TITLE_HEIGHT = 30;
    private static final int HINT_HEIGHT = 22;
    private static final int BOTTOM_MARGIN = 8;

    /**
     * @param rowCount 当前引擎下实际要显示的行数（含顶部开关行与底部两行）
     */
    static TtsLayout of(int screenWidth, int screenHeight, int maxPanelWidth, int maxPanelHeight, int rowCount) {
        int rows = Math.max(1, rowCount);
        int screenMargin = (screenWidth < 380 || screenHeight < 300) ? 6 : 10;
        int panelWidth = Math.max(1, Math.min(maxPanelWidth, screenWidth - screenMargin * 2));

        int wantedHeight = TITLE_HEIGHT + rows * ROW_HEIGHT + HINT_HEIGHT + BUTTON_HEIGHT + BOTTOM_MARGIN;
        int availableHeight = screenHeight - screenMargin * 2;
        int panelHeight = Math.max(1, Math.min(Math.min(maxPanelHeight, availableHeight), wantedHeight));

        int startX = Math.max(screenMargin, (screenWidth - panelWidth) / 2);
        int startY = Math.max(screenMargin, (screenHeight - panelHeight) / 2);
        int contentMargin = panelWidth < 340 ? 8 : 14;
        int contentWidth = Math.max(1, panelWidth - contentMargin * 2);
        int contentX = startX + contentMargin;

        int actionY = startY + panelHeight - BOTTOM_MARGIN - BUTTON_HEIGHT;
        int hintY = actionY - HINT_HEIGHT;
        int rowsTop = startY + TITLE_HEIGHT;

        // 屏幕过矮时压缩行距，而不是把最后一行挤出面板
        int rowHeight = Math.min(ROW_HEIGHT, Math.max(MIN_ROW_HEIGHT, (hintY - rowsTop) / rows));
        return new TtsLayout(startX, startY, panelWidth, panelHeight, contentX, contentWidth,
                rowsTop, rowHeight, hintY, actionY);
    }

    int rowY(int slot) {
        return this.rowsTop + this.rowHeight * slot;
    }

    /** 行内控件高度：比行距略矮，避免上下行控件贴边。 */
    int controlHeight() {
        return Math.max(12, Math.min(BUTTON_HEIGHT, this.rowHeight - 2));
    }

    int fieldX() {
        return this.contentX + LABEL_WIDTH;
    }

    int fieldWidth() {
        return Math.max(1, this.contentWidth - LABEL_WIDTH);
    }

    /** 带标签行里，把控件区均分为 columns 列时第 index 列的 x。 */
    int columnX(TtsCell cell) {
        return this.columnX(cell.index(), cell.columns());
    }

    int columnX(int index, int columns) {
        return this.fieldX() + (this.columnWidth(columns) + GAP) * index;
    }

    int columnWidth(TtsCell cell) {
        return this.columnWidth(cell.columns());
    }

    int columnWidth(int columns) {
        int count = Math.max(1, columns);
        return Math.max(1, (fieldWidth() - GAP * (count - 1)) / count);
    }

    /** 底部按钮按内容区全宽均分。 */
    int actionWidth(int count) {
        int columns = Math.max(1, count);
        return Math.max(1, (this.contentWidth - GAP * (columns - 1)) / columns);
    }

    int actionX(int index, int count) {
        return this.contentX + (this.actionWidth(count) + GAP) * index;
    }
}
