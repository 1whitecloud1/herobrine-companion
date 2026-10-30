package com.whitecloud233.herobrine_companion.client.gui;

import com.whitecloud233.herobrine_companion.client.network.ClientTemplateLibrary;
import com.whitecloud233.herobrine_companion.config.ThemedEditBox;
import com.whitecloud233.herobrine_companion.structure.template.TemplateEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 建筑模板库：导入建筑并建立索引，让 Herobrine 能按名称加载模板。
 *
 * <p>视觉与 {@link HeroScreen} 统一：面板底、标题栏强调条、橙色字段名、主题按钮与滚动条，
 * 配色全部取自 {@link HeroTheme}。列表行高亮用主题色，选中行与原版风格彻底分开。</p>
 */
public final class TemplateLibraryScreen extends HeroPanelScreen {
    private static final String KEY = "gui.herobrine_companion.templates.";
    private static final int PANEL_WIDTH = 470;
    private static final int PANEL_HEIGHT = 340;
    private static final int MIN_WIDTH = 280;
    private static final int MIN_HEIGHT = 260;
    private static final int SCREEN_MARGIN = 8;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 2;
    private static final int CONTROL_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 18;

    private final Screen parent;
    private final List<Button> rows = new ArrayList<>();
    private EditBox pathInput;
    private EditBox nameInput;
    private Button importButton;
    private Button chooseButton;
    private Button refreshButton;
    private Button deleteButton;
    private Button copyButton;
    private Button previous;
    private Button next;
    private String draftPath = "";
    private String draftName = "";
    private String selected = "";
    private String localError = "";
    private int page;
    private int pageSize;
    private int listTop;
    private int listHeight;
    private long revision = -1;
    private boolean choosing;
    private HeroTheme.Frame frame = new HeroTheme.Frame(0, 0, PANEL_WIDTH, PANEL_HEIGHT);

    public TemplateLibraryScreen(Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        ClientTemplateLibrary.refresh();
    }

    private HeroTheme.Frame frame() {
        this.frame = HeroTheme.frame(this.width, this.height, PANEL_WIDTH, PANEL_HEIGHT,
                MIN_WIDTH, MIN_HEIGHT, SCREEN_MARGIN);
        return this.frame;
    }

    @Override
    protected void init() {
        HeroTheme.Frame frame = this.frame();
        int left = frame.contentLeft();
        int width = frame.contentWidth();
        int contentTop = frame.contentTop();
        int contentBottom = frame.contentBottom();

        // 自下而上排：底部按钮行 -> 状态行 -> 分页行 -> 列表 -> 路径行 -> 名称行。
        int actionRowY = contentBottom - BUTTON_HEIGHT;
        int statusY = actionRowY - 14;
        int pagerY = statusY - 24;
        int nameBoxY = contentTop + 12;
        int pathBoxY = nameBoxY + CONTROL_HEIGHT + 22;
        this.listTop = pathBoxY + CONTROL_HEIGHT + 20;
        int listBottom = pagerY - 6;
        this.listHeight = Math.max(ROW_HEIGHT, listBottom - this.listTop);
        this.pageSize = Math.max(1, this.listHeight / (ROW_HEIGHT + ROW_GAP));

        this.nameInput = this.addRenderableWidget(new ThemedEditBox(this.font, left, nameBoxY, width, CONTROL_HEIGHT,
                Component.translatable(KEY + "name")));
        this.nameInput.setMaxLength(80);
        this.nameInput.setValue(this.draftName);
        this.nameInput.setHint(Component.translatable(KEY + "name"));
        this.nameInput.setResponder(value -> this.draftName = value);

        int pathButtonWidth = 60;
        int pathInputWidth = Math.max(40, width - pathButtonWidth * 2 - 8);
        this.pathInput = this.addRenderableWidget(new ThemedEditBox(this.font, left, pathBoxY, pathInputWidth, CONTROL_HEIGHT,
                Component.translatable(KEY + "path")));
        this.pathInput.setMaxLength(4096);
        this.pathInput.setValue(this.draftPath);
        this.pathInput.setHint(Component.translatable(KEY + "path"));
        this.pathInput.setResponder(value -> this.draftPath = value);

        this.chooseButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + pathInputWidth + 4, pathBoxY, pathButtonWidth, CONTROL_HEIGHT,
                Component.translatable(KEY + "choose"), button -> this.choose(), null));
        this.importButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + pathInputWidth + 4 + pathButtonWidth + 4, pathBoxY, pathButtonWidth, CONTROL_HEIGHT,
                Component.translatable(KEY + "import"), button -> this.upload(),
                Tooltip.create(Component.translatable(KEY + "limits"))));

        this.rows.clear();
        for (int i = 0; i < this.pageSize; i++) {
            final int row = i;
            this.rows.add(this.addRenderableWidget(new HeroScreen.ThemedButton(
                    left, this.listTop + i * (ROW_HEIGHT + ROW_GAP), width, ROW_HEIGHT,
                    Component.empty(),
                    button -> this.selectRow(row), null)));
        }

        this.previous = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left, pagerY, 24, CONTROL_HEIGHT, Component.literal("<"),
                button -> {
                    this.page--;
                    this.updateRows();
                }, null));
        this.next = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 28, pagerY, 24, CONTROL_HEIGHT, Component.literal(">"),
                button -> {
                    this.page++;
                    this.updateRows();
                }, null));
        this.refreshButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + width - 60, pagerY, 60, CONTROL_HEIGHT,
                Component.translatable(KEY + "refresh"),
                button -> {
                    this.localError = "";
                    ClientTemplateLibrary.refresh();
                }, null));

        int buttonWidth = (width - 8) / 3;
        this.copyButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left, actionRowY, buttonWidth, BUTTON_HEIGHT,
                Component.translatable(KEY + "copy"),
                button -> this.minecraft.keyboardHandler.setClipboard(this.selected), null));
        this.deleteButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + buttonWidth + 4, actionRowY, buttonWidth, BUTTON_HEIGHT,
                Component.translatable(KEY + "delete"),
                button -> {
                    this.localError = "";
                    ClientTemplateLibrary.delete(this.selected);
                }, null));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + (buttonWidth + 4) * 2, actionRowY, buttonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.done"), button -> this.onClose(), null));
        this.updateRows();
        this.setInitialFocus(this.pathInput);
    }

    private void selectRow(int row) {
        var entries = ClientTemplateLibrary.entries();
        int index = this.page * this.pageSize + row;
        if (index >= 0 && index < entries.size()) {
            this.selected = entries.get(index).id();
            this.updateRows();
        }
    }

    private void choose() {
        this.choosing = true;
        TemplateFileChooser.choose(this.title.getString()).whenComplete((path, error) -> this.minecraft.execute(() -> {
            this.choosing = false;
            if (error != null) {
                this.localError = error.getMessage() == null ? "File dialog failed" : error.getMessage();
            } else if (path != null) {
                this.draftPath = path.toString();
                this.pathInput.setValue(this.draftPath);
            }
        }));
    }

    private void upload() {
        this.localError = "";
        try {
            ClientTemplateLibrary.importFile(Path.of(this.draftPath), this.draftName);
        } catch (RuntimeException error) {
            this.localError = error.getMessage() == null ? "Invalid template path" : error.getMessage();
        }
    }

    private void updateRows() {
        var entries = ClientTemplateLibrary.entries();
        this.page = Math.max(0, Math.min(this.page, Math.max(0, (entries.size() - 1) / Math.max(1, this.pageSize))));
        if (entries.stream().noneMatch(entry -> entry.id().equals(this.selected))) {
            this.selected = "";
        }
        int rowWidth = this.frame.contentWidth();
        for (int i = 0; i < this.rows.size(); i++) {
            Button row = this.rows.get(i);
            int index = this.page * this.pageSize + i;
            row.visible = index < entries.size();
            if (!row.visible) {
                continue;
            }
            TemplateEntry entry = entries.get(index);
            String text = (entry.id().equals(this.selected) ? "▶ " : "· ") + entry.name()
                    + " · " + entry.x() + "×" + entry.y() + "×" + entry.z() + " · " + entry.format();
            row.setMessage(Component.literal(this.font.plainSubstrByWidth(text, rowWidth - 12)));
            row.setTooltip(Tooltip.create(Component.literal(entry.name() + "\n" + entry.id() + "\n" + entry.blocks())
                    .append(Component.translatable(KEY + "blocks"))));
        }
        this.previous.active = this.page > 0;
        this.next.active = (this.page + 1) * this.pageSize < entries.size();
        this.revision = ClientTemplateLibrary.revision();
    }

    @Override
    public void tick() {
        if (this.revision != ClientTemplateLibrary.revision()) {
            this.updateRows();
        }
        boolean idle = !ClientTemplateLibrary.busy();
        this.importButton.active = idle && ClientTemplateLibrary.editable()
                && !this.draftPath.isBlank() && !this.choosing;
        this.chooseButton.active = idle && !this.choosing;
        // NeoForge 不能在未协商的通道上发包（对端没注册会直接断连），所以「刷新」只是重开检测窗口；
        // 仍然允许点击，避免客户端窗口期把玩家锁死在「不支持」上。
        this.refreshButton.active = idle;
        this.deleteButton.active = idle && ClientTemplateLibrary.editable() && !this.selected.isEmpty();
        this.copyButton.active = !this.selected.isEmpty();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        HeroTheme.Frame frame = this.frame();
        HeroTheme.panel(guiGraphics, this.font, frame, this.title);

        int left = frame.contentLeft();
        int width = frame.contentWidth();
        int nameBoxY = frame.contentTop() + 12;
        int pathBoxY = nameBoxY + CONTROL_HEIGHT + 22;

        // 字段名：与输入框同源推导，保证不压框线。
        HeroTheme.fieldLabel(guiGraphics, this.font, left, nameBoxY - 11,
                Component.translatable(KEY + "name"));
        HeroTheme.fieldLabel(guiGraphics, this.font, left, pathBoxY - 11,
                Component.translatable(KEY + "path"));

        // 列表容器底 + 外框：让滚动的行看起来是"嵌在面板里的一个区域"。
        HeroTheme.sunken(guiGraphics, left, this.listTop, width, this.listHeight);
        guiGraphics.renderOutline(left, this.listTop, width, this.listHeight, HeroTheme.BORDER);

        if (ClientTemplateLibrary.entries().isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable(KEY + "empty"),
                    left + width / 2, this.listTop + this.listHeight / 2 - 4, HeroTheme.DISABLED);
        }

        // 分页信息 + 导入后使用提示。
        int pagerY = this.listTop + this.listHeight + 6;
        int totalPages = Math.max(1, (ClientTemplateLibrary.entries().size() + this.pageSize - 1) / Math.max(1, this.pageSize));
        guiGraphics.drawString(this.font, (this.page + 1) + " / " + totalPages,
                left + 58, pagerY + 5, HeroTheme.DISABLED, false);
        guiGraphics.drawString(this.font,
                HeroTheme.clip(this.font, Component.translatable(KEY + "hint").getString(), width - 70),
                left + 70, pagerY + 5, HeroTheme.INFO, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 状态行（本地错误优先，其次服务端状态），贴在底部按钮上方。
        int statusY = frame.contentBottom() - BUTTON_HEIGHT - 14;
        String state = ClientTemplateLibrary.status();
        Component status = this.localError.isEmpty()
                ? Component.translatable(KEY + state, ClientTemplateLibrary.detail())
                : Component.literal(this.localError);
        if (state.equals("uploading")) {
            status = status.copy().append(" " + ClientTemplateLibrary.progress() + "%");
        }
        int statusColor = this.localError.isEmpty() ? HeroTheme.TEXT : HeroTheme.WARN;
        int textY = statusY;
        for (var line : this.font.split(status, width)) {
            guiGraphics.drawString(this.font, line, left, textY, statusColor, false);
            textY += 10;
        }
        if (mouseY >= statusY - 2 && mouseY < textY && mouseX >= left && mouseX <= left + width) {
            guiGraphics.renderTooltip(this.font, this.font.split(status, Math.max(120, width)), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

}
