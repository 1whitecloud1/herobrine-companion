package com.whitecloud233.modid.herobrine_companion.config;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MultilineTextEditor extends AbstractWidget {
    private static final int PADDING = 4;
    private static final int BACKGROUND_COLOR = 0xFF1E1E1E;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int FOCUSED_BORDER_COLOR = 0xFF6A9FB5;
    private static final int TEXT_COLOR = 0xFFE6E6E6;
    private static final int PLACEHOLDER_COLOR = 0xFF7A7A7A;
    private static final int SELECTION_COLOR = 0x805A8DDE;
    private static final int CURSOR_COLOR = 0xFFFFFFFF;

    private final Font font;
    private String value = "";
    private int maxLength = 16_384;
    private int cursorPos;
    private int selectionPos;
    private int scrollLine;
    private int preferredColumn = -1;
    private boolean draggingSelection;
    private Consumer<String> responder = ignored -> {};

    public MultilineTextEditor(Font font, int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
        this.font = font;
    }

    public void setResponder(Consumer<String> responder) {
        this.responder = responder == null ? ignored -> {} : responder;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        if (this.value.length() > this.maxLength) {
            this.setValue(this.value.substring(0, this.maxLength));
        }
    }

    public void setValue(String value) {
        this.value = this.sanitize(value);
        this.cursorPos = Math.min(this.cursorPos, this.value.length());
        this.selectionPos = Math.min(this.selectionPos, this.value.length());
        if (this.cursorPos == 0 && this.selectionPos == 0) {
            this.cursorPos = this.value.length();
            this.selectionPos = this.cursorPos;
        }
        this.ensureCursorVisible();
        this.responder.accept(this.value);
    }

    public String getValue() {
        return this.value;
    }

    public void tick() {
        // Cursor blinking is time-based in render.
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int left = this.getX();
        int top = this.getY();
        int right = left + this.width;
        int bottom = top + this.height;
        int borderColor = this.isFocused() ? FOCUSED_BORDER_COLOR : BORDER_COLOR;

        guiGraphics.fill(left, top, right, bottom, BACKGROUND_COLOR);
        guiGraphics.renderOutline(left, top, this.width, this.height, borderColor);

        List<WrappedLine> lines = this.buildWrappedLines();
        int visibleLineCount = Math.max(1, (this.height - PADDING * 2) / this.font.lineHeight);
        this.scrollLine = Math.max(0, Math.min(this.scrollLine, Math.max(0, lines.size() - visibleLineCount)));

        int textLeft = left + PADDING;
        int textTop = top + PADDING;
        int selectionStart = Math.min(this.cursorPos, this.selectionPos);
        int selectionEnd = Math.max(this.cursorPos, this.selectionPos);

        guiGraphics.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
        if (this.value.isEmpty() && !this.isFocused()) {
            guiGraphics.drawString(this.font, this.getMessage(), textLeft, textTop, PLACEHOLDER_COLOR, false);
        } else {
            for (int i = 0; i < visibleLineCount; i++) {
                int lineIndex = this.scrollLine + i;
                if (lineIndex >= lines.size()) {
                    break;
                }

                WrappedLine line = lines.get(lineIndex);
                int lineY = textTop + i * this.font.lineHeight;
                this.renderSelection(guiGraphics, line, lineY, textLeft, selectionStart, selectionEnd);
                guiGraphics.drawString(this.font, line.text(), textLeft, lineY, TEXT_COLOR, false);
            }

            if (this.isFocused() && ((Util.getMillis() / 500L) & 1L) == 0L) {
                int cursorLineIndex = this.findLineIndex(lines, this.cursorPos);
                if (cursorLineIndex >= this.scrollLine && cursorLineIndex < this.scrollLine + visibleLineCount) {
                    WrappedLine line = lines.get(cursorLineIndex);
                    int visibleIndex = cursorLineIndex - this.scrollLine;
                    int lineY = textTop + visibleIndex * this.font.lineHeight;
                    int column = Math.max(0, Math.min(this.cursorPos, line.end()) - line.start());
                    int cursorX = textLeft + this.font.width(line.text().substring(0, Math.min(column, line.text().length())));
                    guiGraphics.fill(cursorX, lineY - 1, cursorX + 1, lineY + this.font.lineHeight, CURSOR_COLOR);
                }
            }
        }
        guiGraphics.disableScissor();
    }

    private void renderSelection(GuiGraphics guiGraphics, WrappedLine line, int lineY, int textLeft, int selectionStart, int selectionEnd) {
        if (selectionStart == selectionEnd) {
            return;
        }

        int lineStart = line.start();
        int lineEnd = line.end();
        int overlapStart = Math.max(selectionStart, lineStart);
        int overlapEnd = Math.min(selectionEnd, lineEnd);
        if (overlapStart >= overlapEnd) {
            return;
        }

        int startColumn = overlapStart - lineStart;
        int endColumn = overlapEnd - lineStart;
        int startX = textLeft + this.font.width(line.text().substring(0, Math.min(startColumn, line.text().length())));
        int endX = textLeft + this.font.width(line.text().substring(0, Math.min(endColumn, line.text().length())));
        guiGraphics.fill(startX, lineY - 1, Math.max(startX + 1, endX), lineY + this.font.lineHeight, SELECTION_COLOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        boolean inside = this.isMouseOver(mouseX, mouseY);
        this.setFocused(inside);
        if (inside) {
            int newPos = this.getCursorPosFromMouse(mouseX, mouseY);
            boolean selecting = ScreenShiftState.isShiftDown();
            this.moveCursorTo(newPos, selecting);
            this.draggingSelection = true;
            return true;
        }

        this.draggingSelection = false;
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.draggingSelection || button != 0 || !this.isFocused()) {
            return false;
        }

        this.moveCursorTo(this.getCursorPosFromMouse(mouseX, mouseY), true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.draggingSelection = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        List<WrappedLine> lines = this.buildWrappedLines();
        int visibleLineCount = Math.max(1, (this.height - PADDING * 2) / this.font.lineHeight);
        int maxScroll = Math.max(0, lines.size() - visibleLineCount);
        this.scrollLine = Math.max(0, Math.min(maxScroll, this.scrollLine - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!this.isFocused() || !isAllowedEditorCodePoint(codePoint)) {
            return false;
        }

        this.insertText(String.valueOf(codePoint));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.isFocused()) {
            return false;
        }

        if (ScreenControlState.isSelectAll(keyCode)) {
            this.selectionPos = 0;
            this.cursorPos = this.value.length();
            this.preferredColumn = -1;
            this.ensureCursorVisible();
            return true;
        }
        if (ScreenControlState.isCopy(keyCode)) {
            this.copySelection();
            return true;
        }
        if (ScreenControlState.isPaste(keyCode)) {
            this.insertText(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        if (ScreenControlState.isCut(keyCode)) {
            this.copySelection();
            this.deleteSelection();
            return true;
        }

        boolean ctrlDown = ScreenControlState.isControlDown();
        boolean shiftDown = ScreenShiftState.isShiftDown();
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                this.insertText("\n");
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                this.insertText("    ");
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!this.deleteSelection()) {
                    this.deleteFromCursor(ctrlDown ? this.getPreviousWordBoundary(this.cursorPos) : this.cursorPos - 1, this.cursorPos);
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (!this.deleteSelection()) {
                    this.deleteFromCursor(this.cursorPos, ctrlDown ? this.getNextWordBoundary(this.cursorPos) : this.cursorPos + 1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                this.moveCursorTo(ctrlDown ? this.getPreviousWordBoundary(this.cursorPos) : this.cursorPos - 1, shiftDown);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                this.moveCursorTo(ctrlDown ? this.getNextWordBoundary(this.cursorPos) : this.cursorPos + 1, shiftDown);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                this.moveCursorVertically(-1, shiftDown);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                this.moveCursorVertically(1, shiftDown);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                this.moveCursorVertically(-this.getVisibleLineCount(), shiftDown);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                this.moveCursorVertically(this.getVisibleLineCount(), shiftDown);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                if (ctrlDown) {
                    this.moveCursorTo(0, shiftDown);
                } else {
                    this.moveCursorTo(this.getCurrentLineStart(), shiftDown);
                }
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                if (ctrlDown) {
                    this.moveCursorTo(this.value.length(), shiftDown);
                } else {
                    this.moveCursorTo(this.getCurrentLineEnd(), shiftDown);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void moveCursorVertically(int lineDelta, boolean selecting) {
        List<WrappedLine> lines = this.buildWrappedLines();
        int currentLineIndex = this.findLineIndex(lines, this.cursorPos);
        WrappedLine currentLine = lines.get(currentLineIndex);
        int currentColumn = this.font.width(currentLine.text().substring(0, Math.max(0, Math.min(this.cursorPos - currentLine.start(), currentLine.text().length()))));
        if (this.preferredColumn < 0) {
            this.preferredColumn = currentColumn;
        }

        int targetLineIndex = Math.max(0, Math.min(lines.size() - 1, currentLineIndex + lineDelta));
        WrappedLine targetLine = lines.get(targetLineIndex);
        int targetPos = this.getCursorPosFromColumn(targetLine, this.preferredColumn);
        this.moveCursorTo(targetPos, selecting);
    }

    private int getCursorPosFromColumn(WrappedLine line, int targetWidth) {
        int bestPos = line.start();
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i <= line.text().length(); i++) {
            int width = this.font.width(line.text().substring(0, i));
            int distance = Math.abs(width - targetWidth);
            if (distance <= bestDistance) {
                bestDistance = distance;
                bestPos = line.start() + i;
            } else if (width > targetWidth) {
                break;
            }
        }
        return bestPos;
    }

    private int getCurrentLineStart() {
        int pos = this.cursorPos;
        while (pos > 0 && this.value.charAt(pos - 1) != '\n') {
            pos--;
        }
        return pos;
    }

    private int getCurrentLineEnd() {
        int pos = this.cursorPos;
        while (pos < this.value.length() && this.value.charAt(pos) != '\n') {
            pos++;
        }
        return pos;
    }

    private int getPreviousWordBoundary(int pos) {
        int cursor = Math.max(0, Math.min(pos, this.value.length()));
        while (cursor > 0 && Character.isWhitespace(this.value.charAt(cursor - 1))) {
            cursor--;
        }
        while (cursor > 0 && !Character.isWhitespace(this.value.charAt(cursor - 1))) {
            cursor--;
        }
        return cursor;
    }

    private int getNextWordBoundary(int pos) {
        int cursor = Math.max(0, Math.min(pos, this.value.length()));
        while (cursor < this.value.length() && !Character.isWhitespace(this.value.charAt(cursor))) {
            cursor++;
        }
        while (cursor < this.value.length() && Character.isWhitespace(this.value.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private void moveCursorTo(int newCursorPos, boolean selecting) {
        int clamped = Math.max(0, Math.min(newCursorPos, this.value.length()));
        this.cursorPos = clamped;
        if (!selecting) {
            this.selectionPos = clamped;
        }
        this.preferredColumn = -1;
        this.ensureCursorVisible();
    }

    private void insertText(String text) {
        String sanitized = this.sanitizeFragment(text);
        if (sanitized.isEmpty()) {
            return;
        }

        int selectionStart = Math.min(this.cursorPos, this.selectionPos);
        int selectionEnd = Math.max(this.cursorPos, this.selectionPos);
        int room = this.maxLength - (this.value.length() - (selectionEnd - selectionStart));
        if (room <= 0) {
            return;
        }

        if (sanitized.length() > room) {
            sanitized = sanitized.substring(0, room);
        }

        this.value = this.value.substring(0, selectionStart) + sanitized + this.value.substring(selectionEnd);
        this.cursorPos = selectionStart + sanitized.length();
        this.selectionPos = this.cursorPos;
        this.preferredColumn = -1;
        this.ensureCursorVisible();
        this.responder.accept(this.value);
    }

    private boolean deleteSelection() {
        int selectionStart = Math.min(this.cursorPos, this.selectionPos);
        int selectionEnd = Math.max(this.cursorPos, this.selectionPos);
        if (selectionStart == selectionEnd) {
            return false;
        }

        this.value = this.value.substring(0, selectionStart) + this.value.substring(selectionEnd);
        this.cursorPos = selectionStart;
        this.selectionPos = selectionStart;
        this.preferredColumn = -1;
        this.ensureCursorVisible();
        this.responder.accept(this.value);
        return true;
    }

    private void deleteFromCursor(int start, int end) {
        int clampedStart = Math.max(0, Math.min(start, this.value.length()));
        int clampedEnd = Math.max(0, Math.min(end, this.value.length()));
        if (clampedStart >= clampedEnd) {
            return;
        }

        this.value = this.value.substring(0, clampedStart) + this.value.substring(clampedEnd);
        this.cursorPos = clampedStart;
        this.selectionPos = clampedStart;
        this.preferredColumn = -1;
        this.ensureCursorVisible();
        this.responder.accept(this.value);
    }

    private void copySelection() {
        int selectionStart = Math.min(this.cursorPos, this.selectionPos);
        int selectionEnd = Math.max(this.cursorPos, this.selectionPos);
        if (selectionStart == selectionEnd) {
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(this.value.substring(selectionStart, selectionEnd));
    }

    private void ensureCursorVisible() {
        List<WrappedLine> lines = this.buildWrappedLines();
        int visibleLineCount = this.getVisibleLineCount();
        int cursorLine = this.findLineIndex(lines, this.cursorPos);
        if (cursorLine < this.scrollLine) {
            this.scrollLine = cursorLine;
        } else if (cursorLine >= this.scrollLine + visibleLineCount) {
            this.scrollLine = cursorLine - visibleLineCount + 1;
        }
        this.scrollLine = Math.max(0, this.scrollLine);
    }

    private int getVisibleLineCount() {
        return Math.max(1, (this.height - PADDING * 2) / this.font.lineHeight);
    }

    private int getCursorPosFromMouse(double mouseX, double mouseY) {
        List<WrappedLine> lines = this.buildWrappedLines();
        int textTop = this.getY() + PADDING;
        int lineIndex = this.scrollLine + (int) ((mouseY - textTop) / this.font.lineHeight);
        lineIndex = Math.max(0, Math.min(lines.size() - 1, lineIndex));
        WrappedLine line = lines.get(lineIndex);

        double relativeX = mouseX - (this.getX() + PADDING);
        int bestPos = line.start();
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i <= line.text().length(); i++) {
            int width = this.font.width(line.text().substring(0, i));
            int distance = (int) Math.abs(width - relativeX);
            if (distance <= bestDistance) {
                bestDistance = distance;
                bestPos = line.start() + i;
            } else if (width > relativeX) {
                break;
            }
        }
        return bestPos;
    }

    private int findLineIndex(List<WrappedLine> lines, int cursor) {
        int clampedCursor = Math.max(0, Math.min(cursor, this.value.length()));
        for (int i = 0; i < lines.size(); i++) {
            WrappedLine line = lines.get(i);
            if (clampedCursor >= line.start() && clampedCursor <= line.end()) {
                return i;
            }
        }
        return Math.max(0, lines.size() - 1);
    }

    private List<WrappedLine> buildWrappedLines() {
        List<WrappedLine> lines = new ArrayList<>();
        int availableWidth = Math.max(1, this.width - PADDING * 2);
        String[] paragraphs = this.value.split("\\n", -1);
        int globalIndex = 0;

        for (int paragraphIndex = 0; paragraphIndex < paragraphs.length; paragraphIndex++) {
            String paragraph = paragraphs[paragraphIndex];
            if (paragraph.isEmpty()) {
                lines.add(new WrappedLine(globalIndex, globalIndex, ""));
            } else {
                int localIndex = 0;
                while (localIndex < paragraph.length()) {
                    int nextIndex = this.findWrapIndex(paragraph, localIndex, availableWidth);
                    lines.add(new WrappedLine(globalIndex + localIndex, globalIndex + nextIndex, paragraph.substring(localIndex, nextIndex)));
                    localIndex = nextIndex;
                }
            }

            globalIndex += paragraph.length();
            if (paragraphIndex < paragraphs.length - 1) {
                globalIndex++;
            }
        }

        if (lines.isEmpty()) {
            lines.add(new WrappedLine(0, 0, ""));
        }
        return lines;
    }

    private int findWrapIndex(String paragraph, int start, int maxWidth) {
        int low = start + 1;
        int high = paragraph.length();
        int best = low;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int width = this.font.width(paragraph.substring(start, mid));
            if (width <= maxWidth) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        if (best >= paragraph.length()) {
            return paragraph.length();
        }

        int breakAtWhitespace = best;
        while (breakAtWhitespace > start + 1 && !Character.isWhitespace(paragraph.charAt(breakAtWhitespace - 1))) {
            breakAtWhitespace--;
        }
        if (breakAtWhitespace > start + 1 && breakAtWhitespace < paragraph.length()) {
            return breakAtWhitespace;
        }
        return Math.max(start + 1, best);
    }

    private String sanitize(String value) {
        String normalized = this.sanitizeFragment(value);
        return normalized.length() > this.maxLength ? normalized.substring(0, this.maxLength) : normalized;
    }

    private String sanitizeFragment(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder builder = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            if (isAllowedEditorCodePoint(codePoint)) {
                builder.appendCodePoint(codePoint);
            }
        });
        return builder.toString();
    }

    private static boolean isAllowedEditorCodePoint(int codePoint) {
        if (!Character.isValidCodePoint(codePoint) || codePoint > Character.MAX_VALUE) {
            return false;
        }

        if (codePoint == '\n' || codePoint == '\t') {
            return true;
        }

        return !Character.isISOControl(codePoint)
                && codePoint != 0x7F
                && codePoint != 0x00A7;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, this.getMessage());
    }

    private record WrappedLine(int start, int end, String text) {
    }

    private static final class ScreenControlState {
        private static boolean isControlDown() {
            long window = Minecraft.getInstance().getWindow().getWindow();
            return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        }

        private static boolean isSelectAll(int keyCode) {
            return isControlDown() && keyCode == GLFW.GLFW_KEY_A;
        }

        private static boolean isCopy(int keyCode) {
            return isControlDown() && keyCode == GLFW.GLFW_KEY_C;
        }

        private static boolean isPaste(int keyCode) {
            return isControlDown() && keyCode == GLFW.GLFW_KEY_V;
        }

        private static boolean isCut(int keyCode) {
            return isControlDown() && keyCode == GLFW.GLFW_KEY_X;
        }
    }

    private static final class ScreenShiftState {
        private static boolean isShiftDown() {
            long window = Minecraft.getInstance().getWindow().getWindow();
            return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        }
    }
}

