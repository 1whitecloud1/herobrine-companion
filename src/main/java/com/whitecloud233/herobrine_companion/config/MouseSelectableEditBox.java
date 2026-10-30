package com.whitecloud233.herobrine_companion.config;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 支持鼠标拖动选择、Ctrl+A/C/X/V、Delete/方向键的单行输入框。
 *
 * <p>原版 {@code EditBox} 只支持点击定位光标，拖动与大多数编辑快捷键需要宿主界面
 * 在 {@code charTyped}/{@code keyPressed}/{@code mouseDragged}/{@code mouseReleased} 中
 * 主动转发。把这两部分封装在一起，任何页面 {@code addRenderableWidget} 后即可获得
 * 与 API Key 设置页一致的编辑体验，不必各自复制一遍转发代码。</p>
 *
 * <p>使用约定：宿主界面的 {@code charTyped}/{@code keyPressed}/{@code mouseClicked}/
 * {@code mouseDragged}/{@code mouseReleased} 需要调用本类的同名方法（见
 * {@link #dispatchKey}/{@link #dispatchChar}/{@link #dispatchDrag}/{@link #dispatchRelease}），
 * 或在界面里逐个转发给当前聚焦的输入框。</p>
 */
public class MouseSelectableEditBox extends EditBox {

    private Consumer<String> responder = ignored -> {
    };
    private boolean suppressResponder;
    private boolean draggingSelection;
    private int selectionAnchor;

    public MouseSelectableEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        super.setResponder(this::handleValueChanged);
    }

    @Override
    public void setResponder(Consumer<String> responder) {
        this.responder = responder == null ? ignored -> {
        } : responder;
        super.setResponder(this::handleValueChanged);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            this.draggingSelection = false;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        boolean handled = this.withSuppressedResponder(() -> super.mouseClicked(mouseX, mouseY, button));
        if (handled) {
            this.selectionAnchor = this.getCursorPosition();
            this.draggingSelection = true;
            return true;
        }

        this.draggingSelection = false;
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.draggingSelection || button != 0) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        int selectionEnd = this.getCursorPositionFromMouse(mouseX);
        this.setCursorPosition(this.selectionAnchor);
        this.setHighlightPos(selectionEnd);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.draggingSelection) {
            this.draggingSelection = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 拖动中把鼠标横坐标换算成光标位置。 */
    private int getCursorPositionFromMouse(double mouseX) {
        if (mouseX <= this.getX()) {
            return 0;
        }
        if (mouseX >= this.getX() + this.getWidth()) {
            return this.getValue().length();
        }

        this.withSuppressedResponder(() -> {
            this.onClick(mouseX, this.getY());
            return true;
        });
        return this.getCursorPosition();
    }

    /** 是否正在进行鼠标拖选。 */
    public boolean isDraggingSelection() {
        return this.draggingSelection;
    }

    private void handleValueChanged(String value) {
        if (!this.suppressResponder) {
            this.responder.accept(value);
        }
    }

    private boolean withSuppressedResponder(BooleanSupplier action) {
        boolean wasSuppressing = this.suppressResponder;
        this.suppressResponder = true;
        try {
            return action.getAsBoolean();
        } finally {
            this.suppressResponder = wasSuppressing;
        }
    }

    /**
     * 把一个按键转发给界面里所有可编辑输入框（聚焦者优先）。
     *
     * <p>只转给聚焦的框：快捷键（Ctrl+A/C/X/V、Delete、方向键）都作用于光标所在处，
     * 转给非聚焦框会造成"改到别的输入框"的错乱。</p>
     *
     * @return 是否已消费该按键
     */
    public static boolean dispatchKey(Iterable<? extends EditBox> boxes, int keyCode, int scanCode, int modifiers) {
        for (EditBox box : boxes) {
            if (box.isFocused() && box.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    /** 把字符输入转发给聚焦的输入框。 */
    public static boolean dispatchChar(Iterable<? extends EditBox> boxes, char codePoint, int modifiers) {
        for (EditBox box : boxes) {
            if (box.isFocused() && box.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把拖动转发给"正在拖选"的输入框。
     *
     * <p>这里不要求 {@code isFocused()}：按下鼠标时焦点可能还没落到该框上，
     * 若只认聚焦框，第一次拖动就会丢；改用"自己处于拖选状态"作为判据。</p>
     */
    public static boolean dispatchDrag(Iterable<? extends EditBox> boxes, double mouseX, double mouseY,
                                       int button, double dragX, double dragY) {
        for (EditBox box : boxes) {
            if (box instanceof MouseSelectableEditBox selectable
                    && selectable.isDraggingSelection()
                    && selectable.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    /** 把松开转发给处于拖选状态的输入框；否则交给聚焦框处理。 */
    public static boolean dispatchRelease(Iterable<? extends EditBox> boxes, double mouseX, double mouseY, int button) {
        for (EditBox box : boxes) {
            if (box instanceof MouseSelectableEditBox selectable
                    && selectable.isDraggingSelection()
                    && selectable.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        for (EditBox box : boxes) {
            if (box.isFocused() && box.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }
}
