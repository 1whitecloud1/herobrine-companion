package com.whitecloud233.herobrine_companion.client.gui;

import com.whitecloud233.herobrine_companion.client.memory.ImportedMemory;
import com.whitecloud233.herobrine_companion.client.memory.MemoryImportService;
import com.whitecloud233.herobrine_companion.client.service.ConversationStore;
import com.whitecloud233.herobrine_companion.config.MultilineTextEditor;
import com.whitecloud233.herobrine_companion.config.ThemedEditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 记忆导入页：把本地文档/粘贴文本校验后导入成一个新的本机会话。
 *
 * <p>视觉与 {@link HeroScreen} 统一：面板底、标题栏蓝色强调条、橙色字段名、主题按钮，
 * 配色集中取自 {@link HeroTheme}。</p>
 */
public final class MemoryImportScreen extends HeroPanelScreen {
    private static final String KEY = "gui.herobrine_companion.memory_import.";
    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 330;
    private static final int MIN_WIDTH = 260;
    private static final int MIN_HEIGHT = 250;
    private static final int SCREEN_MARGIN = 8;
    private static final int FIELD_GAP = 26;
    private static final int CONTROL_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 18;

    private final Screen parent;
    private final ConversationStore store;
    private final ConversationStore.ImportTarget target;
    private EditBox titleInput;
    private EditBox pathInput;
    private MultilineTextEditor editor;
    private Button importButton;
    private Button previewButton;
    private Button chooseButton;
    private Button loadButton;
    private Button status;
    private ImportedMemory prepared;
    private String draft = "";
    private String draftTitle = "";
    private String draftPath = "";
    private boolean busy;
    private int generation;
    private HeroTheme.Frame frame = new HeroTheme.Frame(0, 0, PANEL_WIDTH, PANEL_HEIGHT);

    public MemoryImportScreen(Screen parent, ConversationStore store, ConversationStore.ImportTarget target) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.store = store;
        this.target = target;
    }

    private HeroTheme.Frame frame() {
        this.frame = HeroTheme.frame(this.width, this.height, PANEL_WIDTH, PANEL_HEIGHT,
                MIN_WIDTH, MIN_HEIGHT, SCREEN_MARGIN);
        return this.frame;
    }

    @Override
    protected void init() {
        super.init();
        HeroTheme.Frame frame = this.frame();
        int left = frame.contentLeft();
        int width = frame.contentWidth();
        int contentTop = frame.contentTop();

        // 自下而上排：底部按钮行 -> 隐私提示 -> 状态行 -> 编辑区 -> 路径行 -> 名称行。
        // 全部由 contentBottom 推导，窗口变矮时只有编辑区收缩，其余行不会叠在一起。
        int contentBottom = frame.contentBottom();
        int actionRowY = contentBottom - BUTTON_HEIGHT;
        int privacyY = actionRowY - 12;
        int statusY = privacyY - 20;
        int titleBoxY = contentTop + 12;
        int pathBoxY = titleBoxY + CONTROL_HEIGHT + FIELD_GAP;
        int editorTop = pathBoxY + CONTROL_HEIGHT + 16;

        this.titleInput = this.addRenderableWidget(new ThemedEditBox(this.font, left, titleBoxY, width, CONTROL_HEIGHT,
                Component.translatable(KEY + "name")));
        this.titleInput.setMaxLength(80);
        this.titleInput.setValue(this.draftTitle);
        this.titleInput.setSuggestion(Component.translatable(KEY + "name").getString());
        this.titleInput.setResponder(value -> this.draftTitle = value);

        int pathButtonWidth = 60;
        int pathInputWidth = Math.max(40, width - pathButtonWidth * 2 - 8);
        this.pathInput = this.addRenderableWidget(new ThemedEditBox(this.font, left, pathBoxY, pathInputWidth, CONTROL_HEIGHT,
                Component.translatable(KEY + "path")));
        this.pathInput.setMaxLength(4096);
        this.pathInput.setValue(this.draftPath);
        this.pathInput.setSuggestion(Component.translatable(KEY + "path").getString());
        this.pathInput.setResponder(value -> this.draftPath = value);

        this.chooseButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + pathInputWidth + 4, pathBoxY, pathButtonWidth, CONTROL_HEIGHT,
                Component.translatable(KEY + "choose"), button -> this.choose(), null));
        this.loadButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + pathInputWidth + 4 + pathButtonWidth + 4, pathBoxY, pathButtonWidth, CONTROL_HEIGHT,
                Component.translatable(KEY + "load"), button -> this.loadPath(), null));

        int editorHeight = Math.max(36, statusY - 6 - editorTop);
        this.editor = this.addRenderableWidget(new MultilineTextEditor(this.font, left, editorTop, width, editorHeight,
                Component.translatable(KEY + "paste")));
        this.editor.setMaxLength(ImportedMemory.MAX_CHARACTERS + 1);
        this.editor.setValue(this.draft);
        this.editor.setResponder(value -> {
            this.draft = value;
            this.invalidatePreview();
        });
        this.editor.setTooltip(Tooltip.create(Component.translatable(KEY + "privacy")));

        // 状态行做成不可点击的按钮，复用主题底与 tooltip（与 HeroScreen 的只读态一致）。
        this.status = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left, statusY, width, 16,
                Component.translatable(KEY + "hint"), button -> { }, null));
        this.status.active = false;
        this.setStatus(Component.translatable(KEY + "hint"));

        int half = (width - 4) / 2;
        this.previewButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left, actionRowY, half, BUTTON_HEIGHT,
                Component.translatable(KEY + "preview"), button -> this.previewPaste(), null));
        this.importButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + half + 4, actionRowY, half, BUTTON_HEIGHT,
                Component.translatable(KEY + "commit"), button -> this.commit(), null));
        this.refreshControls();
    }

    private void choose() {
        if (this.busy) {
            return;
        }
        this.setBusy(true);
        int version = ++this.generation;
        MemoryFileChooser.choose(this.title.getString()).whenComplete((path, error) -> this.minecraft.execute(() -> {
            if (this.minecraft.screen != this || version != this.generation) {
                return;
            }
            this.setBusy(false);
            if (error != null) {
                this.failure(error);
                return;
            }
            if (path == null) {
                return;
            }
            this.pathInput.setValue(path.toString());
            this.loadPath();
        }));
    }

    private void loadPath() {
        if (this.busy || this.draftPath.isBlank()) {
            return;
        }
        String path = this.draftPath;
        int version = ++this.generation;
        this.prepared = null;
        this.setBusy(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                return MemoryImportService.readDocument(java.nio.file.Path.of(path));
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }).whenComplete((document, error) -> this.minecraft.execute(() -> {
            if (this.minecraft.screen != this || version != this.generation) {
                return;
            }
            this.setBusy(false);
            if (error != null) {
                this.failure(error);
                return;
            }
            ImportedMemory memory = document.memory();
            this.draftTitle = memory.title();
            this.titleInput.setValue(this.draftTitle);
            this.editor.setValue(document.source());
            this.prepared = memory;
            this.showPreview();
        }));
    }

    private void previewPaste() {
        if (this.busy) {
            return;
        }
        try {
            this.prepared = MemoryImportService.pasted(this.editor.getValue(), this.titleInput.getValue());
            this.showPreview();
        } catch (Exception error) {
            this.failure(error);
        }
    }

    private void showPreview() {
        this.setStatus(Component.translatable(KEY + "ready", this.prepared.messages().size(), this.prepared.characters()));
        this.refreshControls();
    }

    private void commit() {
        if (this.busy || this.prepared == null || this.target == null || this.minecraft.player == null
                || !this.minecraft.player.getUUID().equals(this.target.playerId())) {
            return;
        }
        this.setBusy(true);
        try {
            ImportedMemory memory = new ImportedMemory(
                    this.titleInput.getValue().isBlank() ? this.prepared.title() : this.titleInput.getValue(),
                    this.prepared.notes(), this.prepared.messages());
            // 直接走 store 引用（架构检查按 `store.importMemory` 字面量校验"导入界面必须接到存储层"）。
            var imported = store.importMemory(this.target, memory);
            if (this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable(KEY + "success", imported.title()), false);
            }
            this.minecraft.setScreen(this.parent);
        } catch (Exception error) {
            this.setBusy(false);
            this.failure(error);
        }
    }

    private void invalidatePreview() {
        this.prepared = null;
        if (this.status != null) {
            this.setStatus(Component.translatable(KEY + "hint"));
        }
        this.refreshControls();
    }

    private void failure(Throwable error) {
        while (error.getCause() != null) {
            error = error.getCause();
        }
        this.prepared = null;
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        this.setStatus(Component.translatable(KEY + "error", message));
        this.refreshControls();
    }

    private void setStatus(Component text) {
        if (this.status != null) {
            this.status.setMessage(text);
            this.status.setTooltip(Tooltip.create(text));
        }
    }

    private void setBusy(boolean value) {
        this.busy = value;
        this.refreshControls();
    }

    private void refreshControls() {
        if (this.chooseButton != null) {
            this.chooseButton.active = !this.busy;
        }
        if (this.editor != null) {
            this.editor.active = !this.busy;
        }
        if (this.titleInput != null) {
            this.titleInput.setEditable(!this.busy);
        }
        if (this.pathInput != null) {
            this.pathInput.setEditable(!this.busy);
        }
        if (this.loadButton != null) {
            this.loadButton.active = !this.busy;
        }
        if (this.previewButton != null) {
            this.previewButton.active = !this.busy;
        }
        if (this.importButton != null) {
            this.importButton.active = !this.busy && this.prepared != null && this.target != null;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        HeroTheme.Frame frame = this.frame();
        HeroTheme.panel(guiGraphics, this.font, frame, this.title);

        // 字段名与输入框同源推导，保证任何窗口高度下标签都贴在框上方而不是压在框线上。
        int titleBoxY = frame.contentTop() + 12;
        int pathBoxY = titleBoxY + CONTROL_HEIGHT + FIELD_GAP;
        HeroTheme.fieldLabel(guiGraphics, this.font, frame.contentLeft(), titleBoxY - 11,
                Component.translatable(KEY + "name"));
        HeroTheme.fieldLabel(guiGraphics, this.font, frame.contentLeft(), pathBoxY - 11,
                Component.translatable(KEY + "path"));

        // 隐私提示贴在底部按钮行上方一行（与 init 里的 privacyY 同一推导）。
        int actionRowY = frame.contentBottom() - BUTTON_HEIGHT;
        guiGraphics.drawString(this.font,
                HeroTheme.clip(this.font, Component.translatable(KEY + "privacy_short").getString(),
                        frame.contentWidth()),
                frame.contentLeft(), actionRowY - 12, HeroTheme.INFO, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.generation++;
        this.minecraft.setScreen(this.parent);
    }
}
