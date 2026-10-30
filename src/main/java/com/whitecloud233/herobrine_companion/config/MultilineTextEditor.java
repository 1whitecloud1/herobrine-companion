package com.whitecloud233.herobrine_companion.config;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Theme and editor-specific policy around Minecraft's multiline editing/layout model. */
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
    private EditorTextField textField;
    private int scrollLine;
    private boolean draggingSelection;
    private Consumer<String> responder = ignored -> {};

    public MultilineTextEditor(Font font, int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
        this.font = font;
        this.textField = createTextField();
        this.textField.setCharacterLimit(16_384);
    }

    private EditorTextField createTextField() {
        EditorTextField field = new EditorTextField(this.font, Math.max(1, this.width - PADDING * 2));
        field.setValueListener(value -> this.responder.accept(value));
        field.setCursorListener(this::ensureCursorVisible);
        return field;
    }

    public void setResponder(Consumer<String> responder) {
        this.responder = responder == null ? ignored -> {} : responder;
    }

    public void setMaxLength(int maxLength) {
        this.textField.setCharacterLimit(Math.max(1, maxLength));
        if (getValue().length() > this.textField.characterLimit()) setValue(getValue());
    }

    public void setValue(String value) { this.textField.setPreservingCursor(value); }

    public String getValue() { return this.textField.value(); }

    public void tick() { /* Cursor blinking is time-based in render. */ }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        if (this.textField == null) return;
        EditorTextField previous = this.textField;
        this.textField = createTextField();
        this.textField.setCharacterLimit(previous.characterLimit());
        // Width is final in the vanilla model. Reflow once on resize, preserving the selection.
        this.textField.setValueListener(ignored -> {});
        this.textField.setValue(previous.value());
        this.textField.restoreSelection(previous.cursor(), previous.anchor());
        this.textField.setValueListener(value -> this.responder.accept(value));
        ensureCursorVisible();
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

        int lineCount = this.textField.getLineCount();
        int visibleLineCount = Math.max(1, (this.height - PADDING * 2) / this.font.lineHeight);
        this.scrollLine = Mth.clamp(this.scrollLine, 0, Math.max(0, lineCount - visibleLineCount));

        int textLeft = left + PADDING;
        int textTop = top + PADDING;
        Range selection = this.textField.selection();
        int selectionStart = selection.start();
        int selectionEnd = selection.end();

        guiGraphics.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
        if (this.getValue().isEmpty() && !this.isFocused()) {
            guiGraphics.drawString(this.font, this.getMessage(), textLeft, textTop, PLACEHOLDER_COLOR, false);
        } else {
            for (int i = 0; i < visibleLineCount; i++) {
                int lineIndex = this.scrollLine + i;
                if (lineIndex >= lineCount) {
                    break;
                }

                Range line = this.textField.line(lineIndex);
                int lineY = textTop + i * this.font.lineHeight;
                this.renderSelection(guiGraphics, line, lineY, textLeft, selectionStart, selectionEnd);
                guiGraphics.drawString(this.font, this.getValue().substring(line.start(), line.end()), textLeft, lineY, TEXT_COLOR, false);
            }

            if (this.isFocused() && ((Util.getMillis() / 500L) & 1L) == 0L) {
                int cursorLineIndex = this.textField.getLineAtCursor();
                if (cursorLineIndex >= this.scrollLine && cursorLineIndex < this.scrollLine + visibleLineCount) {
                    Range line = this.textField.line(cursorLineIndex);
                    int visibleIndex = cursorLineIndex - this.scrollLine;
                    int lineY = textTop + visibleIndex * this.font.lineHeight;
                    int cursorX = textLeft + this.font.width(getValue().substring(line.start(),
                            Mth.clamp(this.textField.cursor(), line.start(), line.end())));
                    guiGraphics.fill(cursorX, lineY - 1, cursorX + 1, lineY + this.font.lineHeight, CURSOR_COLOR);
                }
            }
        }
        guiGraphics.disableScissor();
    }

    private void renderSelection(GuiGraphics guiGraphics, Range line, int lineY, int textLeft, int selectionStart, int selectionEnd) {
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

        int startX = textLeft + this.font.width(getValue().substring(lineStart, overlapStart));
        int endX = textLeft + this.font.width(getValue().substring(lineStart, overlapEnd));
        guiGraphics.fill(startX, lineY - 1, Math.max(startX + 1, endX), lineY + this.font.lineHeight, SELECTION_COLOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        boolean inside = this.isMouseOver(mouseX, mouseY);
        this.setFocused(inside);
        this.draggingSelection = inside;
        if (inside) seekMouse(mouseX, mouseY, Screen.hasShiftDown());
        return inside;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.draggingSelection || button != 0 || !this.isFocused()) return false;
        seekMouse(mouseX, mouseY, true);
        return true;
    }

    private void seekMouse(double mouseX, double mouseY, boolean selecting) {
        this.textField.setSelecting(selecting);
        this.textField.seekCursorToPoint(mouseX - this.getX() - PADDING,
                mouseY - this.getY() - PADDING + this.scrollLine * this.font.lineHeight);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) this.draggingSelection = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.isMouseOver(mouseX, mouseY)) return false;
        this.scrollLine = Mth.clamp(this.scrollLine - (int) Math.signum(verticalAmount), 0,
                Math.max(0, this.textField.getLineCount() - getVisibleLineCount()));
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!this.isFocused() || !isAllowedEditorCodePoint(codePoint)) return false;
        this.textField.insertText(String.valueOf(codePoint));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.isFocused()) return false;
        // Empty paste/copy must not clear an existing selection/clipboard.
        if (Screen.isPaste(keyCode)) {
            String text = sanitizeFragment(Minecraft.getInstance().keyboardHandler.getClipboard());
            if (!text.isEmpty()) this.textField.insertText(text);
            return true;
        }
        if ((Screen.isCopy(keyCode) || Screen.isCut(keyCode)) && !this.textField.hasSelection()) return true;
        if (editingKey(keyCode, Screen.hasControlDown(), Screen.hasShiftDown())) return true;
        boolean handled = this.textField.keyPressed(keyCode);
        ensureCursorVisible();
        return handled;
    }

    boolean editingKey(int keyCode, boolean control, boolean shift) {
        this.textField.setSelecting(shift);
        switch (keyCode) {
            case GLFW.GLFW_KEY_TAB -> this.textField.insertText("    ");
            case GLFW.GLFW_KEY_UP -> this.textField.seekCursorLine(-1);
            case GLFW.GLFW_KEY_DOWN -> this.textField.seekCursorLine(1);
            case GLFW.GLFW_KEY_PAGE_UP -> this.textField.seekCursorLine(-getVisibleLineCount());
            case GLFW.GLFW_KEY_PAGE_DOWN -> this.textField.seekCursorLine(getVisibleLineCount());
            case GLFW.GLFW_KEY_HOME -> this.textField.seekCursor(Whence.ABSOLUTE,
                    control ? 0 : getValue().lastIndexOf('\n', this.textField.cursor() - 1) + 1);
            case GLFW.GLFW_KEY_END -> {
                int end = getValue().indexOf('\n', this.textField.cursor());
                this.textField.seekCursor(Whence.ABSOLUTE,
                        control || end < 0 ? getValue().length() : end);
            }
            default -> {
                return false;
            }
        }
        ensureCursorVisible();
        return true;
    }

    private void ensureCursorVisible() {
        if (this.textField == null) return;
        int cursorLine = Math.max(0, this.textField.getLineAtCursor());
        int visible = getVisibleLineCount();
        if (cursorLine < this.scrollLine) this.scrollLine = cursorLine;
        else if (cursorLine >= this.scrollLine + visible) this.scrollLine = cursorLine - visible + 1;
        this.scrollLine = Mth.clamp(this.scrollLine, 0, Math.max(0, this.textField.getLineCount() - visible));
    }

    private int getVisibleLineCount() { return Math.max(1, (this.height - PADDING * 2) / this.font.lineHeight); }

    static String sanitizeFragment(String value) {
        if (value == null || value.isEmpty()) return "";
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder builder = new StringBuilder(normalized.length());
        normalized.codePoints().filter(MultilineTextEditor::isAllowedEditorCodePoint).forEach(builder::appendCodePoint);
        return builder.toString();
    }

    private static boolean isAllowedEditorCodePoint(int codePoint) {
        return Character.isValidCodePoint(codePoint) && codePoint <= Character.MAX_VALUE
                && (codePoint == '\n' || codePoint == '\t'
                || (!Character.isISOControl(codePoint) && codePoint != 0x7F && codePoint != 0x00A7));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }

    record Range(int start, int end) { }

    /** Keep project input policy; delegate wrapping, selection, deletion and navigation to vanilla. */
    static final class EditorTextField extends MultilineTextField {
        EditorTextField(Font font, int width) { super(font, width); }

        Range line(int index) {
            StringView line = getLineView(index);
            return new Range(line.beginIndex(), line.endIndex());
        }

        Range selection() {
            StringView selected = getSelected();
            return new Range(selected.beginIndex(), selected.endIndex());
        }

        int anchor() {
            Range selected = selection();
            return cursor() == selected.start() ? selected.end() : selected.start();
        }

        void restoreSelection(int cursor, int anchor) {
            setSelecting(false);
            seekCursor(Whence.ABSOLUTE, anchor);
            setSelecting(true);
            seekCursor(Whence.ABSOLUTE, cursor);
            setSelecting(false);
        }

        void setPreservingCursor(String text) {
            int cursor = cursor(), anchor = anchor();
            super.setValue(sanitizeFragment(text));
            if (cursor != 0 || anchor != 0) restoreSelection(cursor, anchor);
        }

        @Override
        public void insertText(String text) {
            String fragment = sanitizeFragment(text);
            // Native deleteText/cut use an empty insertion to remove the selected range.
            if (fragment.isEmpty() && (!text.isEmpty() || !hasSelection())) return;
            Range selected = selection();
            int room = characterLimit() - (value().length() - (selected.end() - selected.start()));
            if (room <= 0 && !fragment.isEmpty()) return;
            if (fragment.length() > room) fragment = fragment.substring(0, Math.max(0, room));
            String replaced = value().substring(0, selected.start()) + fragment + value().substring(selected.end());
            super.setValue(replaced);
            setSelecting(false);
            seekCursor(Whence.ABSOLUTE, selected.start() + fragment.length());
        }
    }
}
