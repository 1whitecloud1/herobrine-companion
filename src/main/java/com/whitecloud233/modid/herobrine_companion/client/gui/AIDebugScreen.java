package com.whitecloud233.modid.herobrine_companion.client.gui;

import com.whitecloud233.modid.herobrine_companion.client.service.AIDebugLog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 调试界面：查看每次后台 AI 调用的<b>思考过程（reasoning_content）</b>与实际返回。
 *
 * <p>数据来自 {@link AIDebugLog} 的环形缓冲（所有 LLM 调用无论成败自动记录）。
 * 左栏是可点选的条目列表（新→旧），右栏展示选中条目的思考过程 / 实际回复 /
 * 工具调用 / 原始响应 body，支持滚轮滚动。</p>
 */
public class AIDebugScreen extends Screen {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final int COL_BG = 0xFF2B2B2B;
    private static final int COL_BORDER = 0xFF555555;
    private static final int COL_TITLE = 0xFFCC7832;
    private static final int COL_TEXT = 0xFFA9B7C6;
    private static final int COL_INFO = 0xFF6A8759;
    private static final int COL_WARN = 0xFFFF8080;
    private static final int COL_SELECT = 0xFF40485C;

    private static final int LIST_WIDTH = 230;
    private static final int ROW_HEIGHT = 18;
    private static final int TOP = 46;

    private final Screen lastScreen;
    private List<AIDebugLog.Entry> entries = List.of();
    private int selected = 0;
    private int listScroll = 0;
    private int detailScroll = 0;
    private int detailContentHeight = 0;

    private final List<SelectableLine> selectableLines = new ArrayList<>();
    private boolean selecting;
    private SelectablePoint selectStart;
    private SelectablePoint selectEnd;

    public AIDebugScreen(Screen lastScreen) {
        super(Component.literal("AI Debug"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        super.init();
        this.reload();
        int bw = 60;
        int bh = 20;
        this.addRenderableWidget(new HeroScreen.ThemedButton(this.width - 208, 6, bw, bh, Component.translatable("gui.herobrine_companion.ai_debug.refresh"),
                b -> this.reload(), null));
        this.addRenderableWidget(new HeroScreen.ThemedButton(this.width - 140, 6, bw, bh, Component.translatable("gui.herobrine_companion.ai_debug.clear"),
                b -> {
                    AIDebugLog.clear();
                    this.reload();
                }, null));
        this.addRenderableWidget(new HeroScreen.ThemedButton(this.width - 72, 6, bw, bh, Component.translatable("gui.herobrine_companion.ai_debug.back"),
                b -> this.onClose(), null));
    }

    private void reload() {
        this.entries = AIDebugLog.recent();
        if (this.selected >= this.entries.size()) {
            this.selected = Math.max(0, this.entries.size() - 1);
        }
        this.listScroll = 0;
        this.detailScroll = 0;
    }

    private AIDebugLog.Entry currentEntry() {
        if (this.entries.isEmpty() || this.selected < 0 || this.selected >= this.entries.size()) {
            return null;
        }
        return this.entries.get(this.selected);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(this.font, "AI Debug", 8, 10, COL_TITLE);
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.ai_debug.subtitle"), 8, 28, COL_INFO);

        int bottom = this.height - 8;
        int listRight = 8 + LIST_WIDTH;

        // ---- 左栏：条目列表 ----
        guiGraphics.enableScissor(8, TOP, listRight, bottom);
        guiGraphics.fill(8, TOP, listRight, bottom, COL_BG);
        guiGraphics.renderOutline(8, TOP, LIST_WIDTH, bottom - TOP, COL_BORDER);
        int visibleRows = (bottom - TOP) / ROW_HEIGHT;
        for (int i = 0; i < visibleRows; i++) {
            int entryIndex = this.listScroll + i;
            if (entryIndex >= this.entries.size()) {
                break;
            }
            AIDebugLog.Entry e = this.entries.get(entryIndex);
            int y = TOP + i * ROW_HEIGHT;
            if (entryIndex == this.selected) {
                guiGraphics.fill(10, y, listRight - 2, y + ROW_HEIGHT - 2, COL_SELECT);
            }
            String status = String.valueOf(e.statusCode());
            String time = TIME.format(Instant.ofEpochMilli(e.timestamp()));
            String line = time + " [" + status + "] " + nvl(e.task()) + " " + truncate(e.reply(), 14);
            guiGraphics.drawString(this.font, line, 12, y + 5, e.isSuccess() ? COL_TEXT : COL_WARN);
        }
        guiGraphics.disableScissor();

        // ---- 右栏：详情 ----
        int detailX = listRight + 10;
        int detailW = this.width - detailX - 8;
        guiGraphics.enableScissor(detailX, TOP, detailX + detailW, bottom);
        guiGraphics.fill(detailX, TOP, detailX + detailW, bottom, COL_BG);
        guiGraphics.renderOutline(detailX, TOP, detailW, bottom - TOP, COL_BORDER);

        AIDebugLog.Entry e = currentEntry();
        int cursorY = TOP - this.detailScroll;
        if (e == null) {
            guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.ai_debug.empty"), detailX + 6, TOP + 6, COL_INFO);
        } else {
            selectableLines.clear();
            cursorY = this.drawWrapped(guiGraphics,
                    Component.translatable("gui.herobrine_companion.ai_debug.http_line",
                            nvl(e.task()), e.statusCode(), nvl(e.model())),
                    detailX + 6, cursorY, detailW - 12, COL_INFO);
            cursorY += 6;
            cursorY = this.drawSection(guiGraphics, Component.translatable("gui.herobrine_companion.ai_debug.section.reasoning"),
                    e.reasoning(), detailX + 6, cursorY, detailW - 12, COL_INFO);
            cursorY += 4;
            cursorY = this.drawSection(guiGraphics, Component.translatable("gui.herobrine_companion.ai_debug.section.reply"),
                    e.reply(), detailX + 6, cursorY, detailW - 12, COL_TITLE);
            if (e.toolCall() != null && !e.toolCall().isBlank()) {
                cursorY += 4;
                cursorY = this.drawSection(guiGraphics, Component.translatable("gui.herobrine_companion.ai_debug.section.tool_call"),
                        e.toolCall(), detailX + 6, cursorY, detailW - 12, COL_WARN);
            }
            if (e.rawBody() != null && !e.rawBody().isBlank()) {
                cursorY += 4;
                cursorY = this.drawSection(guiGraphics, Component.translatable("gui.herobrine_companion.ai_debug.section.raw"),
                        e.rawBody(), detailX + 6, cursorY, detailW - 12, COL_TITLE);
            }
            if (e.error() != null && !e.error().isBlank()) {
                cursorY += 4;
                cursorY = this.drawSection(guiGraphics, Component.translatable("gui.herobrine_companion.ai_debug.section.error"),
                        e.error(), detailX + 6, cursorY, detailW - 12, COL_WARN);
            }
        }
        this.detailContentHeight = cursorY + this.detailScroll - TOP;
        this.clampDetailScroll(bottom - TOP);
        guiGraphics.disableScissor();
    }

    private int drawSection(GuiGraphics g, Component label, String text, int x, int y, int maxW, int labelColor) {
        g.drawString(this.font, label, x, y, labelColor);
        y += 10;
        if (text == null || text.isBlank()) {
            g.drawString(this.font, Component.translatable("gui.herobrine_companion.ai_debug.empty_section"), x, y, COL_INFO);
            return y + 10;
        }
        return this.drawSelectableText(g, text, x, y, maxW, COL_TEXT);
    }

    private int drawSelectableText(GuiGraphics g, String text, int x, int y, int maxW, int color) {
        for (String line : wrapText(text, maxW)) {
            int lineIndex = selectableLines.size();
            SelectableLine selectableLine = new SelectableLine(line, x, y);
            selectableLines.add(selectableLine);
            drawSelectableLine(g, selectableLine, lineIndex, color);
            y += 10;
        }
        return y;
    }

    private void drawSelectableLine(GuiGraphics g, SelectableLine line, int lineIndex, int color) {
        if (selectStart != null && selectEnd != null) {
            int minLine = Math.min(selectStart.line(), selectEnd.line());
            int maxLine = Math.max(selectStart.line(), selectEnd.line());
            if (lineIndex >= minLine && lineIndex <= maxLine) {
                int startCol = lineIndex == minLine ? Math.min(selectStart.col(), selectEnd.col()) : 0;
                int endCol = lineIndex == maxLine ? Math.max(selectStart.col(), selectEnd.col()) : line.text().length();
                if (lineIndex == minLine && lineIndex == maxLine && selectStart.col() > selectEnd.col()) {
                    startCol = selectEnd.col();
                    endCol = selectStart.col();
                }
                if (startCol < endCol) {
                    int x1 = line.x() + this.font.width(line.text().substring(0, startCol));
                    int x2 = line.x() + this.font.width(line.text().substring(0, endCol));
                    g.fill(x1, line.y(), x2, line.y() + 10, 0x804A90D9);
                }
            }
        }
        g.drawString(this.font, line.text(), line.x(), line.y(), color);
    }

    private List<String> wrapText(String text, int maxW) {
        List<String> lines = new ArrayList<>();
        String remaining = text;
        while (!remaining.isEmpty()) {
            if (this.font.width(remaining) <= maxW) {
                lines.add(remaining);
                break;
            }
            String sub = this.font.plainSubstrByWidth(remaining, maxW);
            int cut = sub.length();
            if (cut <= 0) {
                cut = Math.max(1, remaining.length());
            }
            lines.add(remaining.substring(0, cut));
            remaining = remaining.substring(cut);
            if (remaining.startsWith(" ")) {
                remaining = remaining.substring(1);
            }
        }
        return lines;
    }

    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int maxW, int color) {
        int yy = y;
        for (FormattedCharSequence line : this.font.split(text, maxW)) {
            g.drawString(this.font, line, x, yy, color);
            yy += 10;
        }
        return yy;
    }

    private void clampDetailScroll(int paneHeight) {
        int maxScroll = Math.max(0, this.detailContentHeight - paneHeight);
        if (this.detailScroll > maxScroll) {
            this.detailScroll = maxScroll;
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        s = s.replace('\n', ' ');
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            int bottom = this.height - 8;
            int listRight = 8 + LIST_WIDTH;
            if (mouseX >= 8 && mouseX <= listRight && mouseY >= TOP && mouseY <= bottom) {
                int row = (int) ((mouseY - TOP) / ROW_HEIGHT);
                int index = this.listScroll + row;
                if (index >= 0 && index < this.entries.size()) {
                    this.selected = index;
                    this.detailScroll = 0;
                    return true;
                }
            } else if (mouseX > listRight) {
                SelectablePoint point = pointAt(mouseX, mouseY);
                if (point != null) {
                    this.selectStart = point;
                    this.selectEnd = point;
                    this.selecting = true;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.selecting) {
            SelectablePoint point = pointAt(mouseX, mouseY);
            if (point != null) {
                this.selectEnd = point;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.selecting) {
            this.selecting = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
            this.copySelection();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private SelectablePoint pointAt(double mouseX, double mouseY) {
        if (selectableLines.isEmpty()) {
            return null;
        }
        SelectableLine line = null;
        int lineIndex = -1;
        for (int i = 0; i < selectableLines.size(); i++) {
            SelectableLine candidate = selectableLines.get(i);
            if (mouseY >= candidate.y() && mouseY < candidate.y() + 10) {
                line = candidate;
                lineIndex = i;
                break;
            }
        }
        if (line == null) {
            if (mouseY < selectableLines.get(0).y()) {
                line = selectableLines.get(0);
                lineIndex = 0;
            } else {
                line = selectableLines.get(selectableLines.size() - 1);
                lineIndex = selectableLines.size() - 1;
            }
        }
        String text = line.text();
        int col = text.length();
        for (int i = 0; i <= text.length(); i++) {
            if (mouseX <= line.x() + this.font.width(text.substring(0, i))) {
                col = i;
                break;
            }
        }
        return new SelectablePoint(lineIndex, col);
    }

    private void copySelection() {
        if (selectStart == null || selectEnd == null || selectableLines.isEmpty()) {
            return;
        }
        SelectablePoint start = selectStart.line() < selectEnd.line()
                || (selectStart.line() == selectEnd.line() && selectStart.col() <= selectEnd.col())
                ? selectStart : selectEnd;
        SelectablePoint end = start == selectStart ? selectEnd : selectStart;
        StringBuilder sb = new StringBuilder();
        for (int i = start.line(); i <= end.line(); i++) {
            if (i < 0 || i >= selectableLines.size()) {
                continue;
            }
            String line = selectableLines.get(i).text();
            int from = i == start.line() ? Math.min(start.col(), line.length()) : 0;
            int to = i == end.line() ? Math.min(end.col(), line.length()) : line.length();
            if (from < to) {
                sb.append(line, from, to);
            }
            if (i < end.line()) {
                sb.append('\n');
            }
        }
        if (sb.length() > 0) {
            this.minecraft.keyboardHandler.setClipboard(sb.toString());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listRight = 8 + LIST_WIDTH;
        if (mouseX <= listRight) {
            this.listScroll = Math.max(0, this.listScroll - (int) delta);
        } else {
            this.detailScroll = Math.max(0, this.detailScroll - (int) (delta * 14));
            this.clampDetailScroll(this.height - 8 - TOP);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    private record SelectableLine(String text, int x, int y) {
    }

    private record SelectablePoint(int line, int col) {
    }
}
