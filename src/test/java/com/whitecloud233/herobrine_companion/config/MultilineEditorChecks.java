package com.whitecloud233.herobrine_companion.config;

import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs the actual vanilla text model with a deterministic font; no Minecraft window needed. */
public final class MultilineEditorChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Font font = new FixedFont();
        var field = new MultilineTextEditor.EditorTextField(font, 36);
        field.setCharacterLimit(8);
        field.setPreservingCursor("中文\r\nab\rc\t\u00a7\u0001😀");
        check("CRLF/control filtering/truncation", "中文\nab\nc\t".equals(field.value()));
        check("initial value moves cursor to end", field.cursor() == 8);
        field.restoreSelection(6, 2);
        field.insertText("XY");
        check("selection replacement when already at limit", "中文XYc\t".equals(field.value()));
        check("replacement collapses selection", field.cursor() == 4 && !field.hasSelection());
        field.restoreSelection(4, 2);
        field.deleteText(-1);
        check("native deletion removes selected range", "中文c\t".equals(field.value()) && field.cursor() == 2);
        field.deleteText(-1);
        check("native backspace", "中c\t".equals(field.value()));
        field.setPreservingCursor("12345678");
        field.seekCursor(Whence.END, 0);
        field.insertText("9");
        check("full limit prevents insertion", "12345678".equals(field.value()));
        field.restoreSelection(5, 2);
        field.insertText("ABCDE");
        check("replacement truncates only inserted text", "12ABC678".equals(field.value()));
        field.restoreSelection(4, 2);
        field.insertText("\u00a7\u0000");
        check("filtered paste preserves selection", "12ABC678".equals(field.value()) && field.hasSelection());
        field.insertText("");
        check("native cut empty insertion deletes selection", "12C678".equals(field.value()));
        field.restoreSelection(2, 5);
        field.setPreservingCursor("abcdef");
        check("setValue preserves reverse selection", field.cursor() == 2 && field.anchor() == 5);
        field.setPreservingCursor("a");
        check("setValue clamps both cursors", field.cursor() == 1 && field.anchor() == 1);
        field.setCharacterLimit(100);
        field.setPreservingCursor("abcdefghi\n中文\n");
        check("vanilla wraps long lines", field.getLineCount() == 4);
        check("trailing newline retains empty line", field.line(3).start() == field.value().length());
        field.setSelecting(false);
        field.seekCursorToPoint(12, 9);
        check("native mouse maps wrapped second line", field.cursor() == 8);
        field.seekCursor(Whence.ABSOLUTE, 1);
        field.seekCursorLine(1);
        check("native vertical cursor navigation", field.cursor() == 7);
        AtomicInteger callbacks = new AtomicInteger();
        MultilineTextEditor editor = new MultilineTextEditor(font, 0, 0, 44, 26, Component.literal("placeholder"));
        editor.setResponder(text -> callbacks.incrementAndGet());
        editor.setMaxLength(100);
        editor.setValue("abcdefghi\n中文\nlast");
        check("widget responder called once", callbacks.get() == 1);
        Field model = MultilineTextEditor.class.getDeclaredField("textField");
        model.setAccessible(true);
        var actual = (MultilineTextEditor.EditorTextField) model.get(editor);
        actual.restoreSelection(7, 2);
        editor.setWidth(80);
        actual = (MultilineTextEditor.EditorTextField) model.get(editor);
        check("resize reflows native model", actual.getLineCount() == 3);
        check("resize preserves selection", actual.cursor() == 7 && actual.anchor() == 2);
        check("resize does not emit content change", callbacks.get() == 1);
        actual.restoreSelection(0, 0);
        editor.editingKey(GLFW.GLFW_KEY_END, false, false);
        check("End goes to logical paragraph end", actual.cursor() == 9);
        editor.editingKey(GLFW.GLFW_KEY_HOME, false, true);
        check("Shift Home selects paragraph", actual.cursor() == 0 && actual.anchor() == 9);
        editor.editingKey(GLFW.GLFW_KEY_TAB, false, false);
        check("Tab inserts four spaces and replaces selection", editor.getValue().startsWith("    \n中文"));
        editor.editingKey(GLFW.GLFW_KEY_END, true, false);
        check("Ctrl End goes to document end", actual.cursor() == editor.getValue().length());
        editor.editingKey(GLFW.GLFW_KEY_HOME, true, false);
        check("Ctrl Home goes to document start", actual.cursor() == 0);
        editor.editingKey(GLFW.GLFW_KEY_PAGE_DOWN, false, true);
        check("Page Down selects a visible page", actual.cursor() > 0 && actual.hasSelection());
        Field scroll = MultilineTextEditor.class.getDeclaredField("scrollLine");
        scroll.setAccessible(true);
        check("page cursor remains visible", scroll.getInt(editor) > 0);
        editor.setMaxLength(3);
        check("lowered max immediately truncates", editor.getValue().length() == 3);
        editor.setMaxLength(0);
        check("nonpositive max retains minimum one", editor.getValue().length() == 1);
        check("normalization retains allowed tabs", "a\nb\nc\td".equals(MultilineTextEditor.sanitizeFragment("a\r\nb\rc\td")));
        check("null input normalized", MultilineTextEditor.sanitizeFragment(null).isEmpty());
        System.out.println("MultilineEditorChecks: " + checks + " checks passed");
    }
    private static final class FixedFont extends Font {
        private final StringSplitter splitter = new StringSplitter((codePoint, style) -> 6F);
        FixedFont() { super(id -> null, false); }
        @Override public StringSplitter getSplitter() { return this.splitter; }
        @Override public int width(String text) { return text.length() * 6; }
        @Override public String plainSubstrByWidth(String text, int width) { return text.substring(0, Math.min(text.length(), Math.max(0, width / 6))); }
    }
    private static void check(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
