package com.whitecloud233.herobrine_companion.config;

import com.whitecloud233.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 指令与工具开关子页面（单一职责：承载流式输出 / 指令调用 / 电脑 CMD / JVM 代码 / 联网查找 / JVM 优先六个开关）。
 * 从 LLM 设置页进入，保存后写回 LLMConfig 并返回上一页。
 */
public class LLMToolTogglesScreen extends Screen {
    private static final int MIN_PANEL_WIDTH = 340;
    private static final int MIN_PANEL_HEIGHT = 280;
    private static final int BUTTON_HEIGHT = 20;
    private static final int CONTROL_GAP = 8;
    private static final int COL_BG = 0xFF2B2B2B;
    private static final int COL_BORDER = 0xFF555555;
    private static final int COL_TITLE = 0xFFCC7832;

    // 每行开关采用“标签并入按钮”的全宽按钮（如“流式输出：开”），不再单独画标签行，
    // 因此 6 行开关 + 底部按钮即使放在很矮的窗口里也能完整放下，不会文字压按钮。
    private static final int ROW_COUNT = 6;
    private static final int ROW_GAP_COUNT = ROW_COUNT - 1;
    private static final int ROW1_TOP = 34;                // 第 1 行按钮上缘（标题下方）
    private static final int ROW_BOTTOM_GAP = 8;           // 最后一行按钮与底部按钮的间距
    private static final int BOTTOM_BUTTONS_OFFSET = 40;   // 底部按钮上缘（相对面板底部向上量）
    private static final int MIN_ROW_GAP = 8;              // 无独立标签行，行距只需很小的下限

    private final Screen lastScreen;
    private boolean settingsLoaded;
    private boolean streamingEnabled;
    private LLMConfig.CommandMode commandMode;
    private boolean computerControlEnabled;
    private boolean jvmCodeSkillEnabled;
    private boolean webLookupEnabled;
    private boolean jvmPreferredEnabled;

    public LLMToolTogglesScreen(Screen lastScreen) {
        super(Component.translatable("gui.herobrine_companion.llm_tool_toggles.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        super.init();
        LLMConfig.ensureLoaded();
        if (!this.settingsLoaded) {
            this.streamingEnabled = LLMConfig.isStreamingEnabled();
            this.commandMode = LLMConfig.getCommandMode();
            this.computerControlEnabled = LLMConfig.isComputerControlEnabled();
            this.jvmCodeSkillEnabled = LLMConfig.isJvmCodeSkillEnabled();
            this.webLookupEnabled = LLMConfig.isWebLookupEnabled();
            this.jvmPreferredEnabled = LLMConfig.isJvmPreferredEnabled();
            this.settingsLoaded = true;
        }
        if (this.commandMode == null) {
            this.commandMode = LLMConfig.CommandMode.NORMAL;
        }

        Layout layout = this.computeLayout();
        int contentLeft = layout.contentLeft;
        int contentWidth = layout.contentWidth;
        int[] rowYs = layout.rowYs;

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                contentLeft, rowYs[0], contentWidth, BUTTON_HEIGHT,
                this.getCommandModeButtonMessage(),
                button -> {
                    this.commandMode = this.commandMode.next();
                    button.setMessage(this.getCommandModeButtonMessage());
                },
                null));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                contentLeft, rowYs[1], contentWidth, BUTTON_HEIGHT,
                this.getStreamingButtonMessage(),
                button -> {
                    this.streamingEnabled = !this.streamingEnabled;
                    button.setMessage(this.getStreamingButtonMessage());
                },
                null));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                contentLeft, rowYs[2], contentWidth, BUTTON_HEIGHT,
                this.getComputerControlButtonMessage(),
                button -> {
                    this.computerControlEnabled = !this.computerControlEnabled;
                    button.setMessage(this.getComputerControlButtonMessage());
                },
                Tooltip.create(Component.translatable("gui.herobrine_companion.llm_settings.computer_control_tooltip"))));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                contentLeft, rowYs[3], contentWidth, BUTTON_HEIGHT,
                this.getJvmCodeButtonMessage(),
                button -> {
                    this.jvmCodeSkillEnabled = !this.jvmCodeSkillEnabled;
                    button.setMessage(this.getJvmCodeButtonMessage());
                },
                Tooltip.create(Component.translatable("gui.herobrine_companion.llm_settings.jvm_code_skill_tooltip"))));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                contentLeft, rowYs[4], contentWidth, BUTTON_HEIGHT,
                this.getWebLookupButtonMessage(),
                button -> {
                    this.webLookupEnabled = !this.webLookupEnabled;
                    button.setMessage(this.getWebLookupButtonMessage());
                },
                Tooltip.create(Component.translatable("gui.herobrine_companion.llm_settings.web_lookup_tooltip"))));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                contentLeft, rowYs[5], contentWidth, BUTTON_HEIGHT,
                this.getJvmPreferredButtonMessage(),
                button -> {
                    this.jvmPreferredEnabled = !this.jvmPreferredEnabled;
                    button.setMessage(this.getJvmPreferredButtonMessage());
                },
                Tooltip.create(Component.translatable("gui.herobrine_companion.llm_settings.jvm_preferred_tooltip"))));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.saveButtonX, layout.buttonY, layout.buttonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.api_setup.confirm_save"),
                button -> this.saveAndClose(),
                null));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.backButtonX, layout.buttonY, layout.buttonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.back"),
                button -> Minecraft.getInstance().setScreen(this.lastScreen),
                null));
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No default dim overlay.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = this.computeLayout();
        guiGraphics.fill(layout.panelLeft, layout.panelTop, layout.panelLeft + layout.panelWidth,
                layout.panelTop + layout.panelHeight, COL_BG);
        guiGraphics.renderOutline(layout.panelLeft, layout.panelTop, layout.panelWidth, layout.panelHeight,
                COL_BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, layout.panelTop + 12, COL_TITLE);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component getCommandModeButtonMessage() {
        return Component.translatable("gui.herobrine_companion.llm_settings.command_mode",
                Component.translatable(this.commandMode == LLMConfig.CommandMode.EAGER
                        ? "gui.herobrine_companion.llm_settings.command_mode.eager"
                        : "gui.herobrine_companion.llm_settings.command_mode.normal"));
    }

    private Component getStreamingButtonMessage() {
        return Component.translatable("gui.herobrine_companion.llm_settings.streaming",
                Component.translatable(this.streamingEnabled ? "options.on" : "options.off"));
    }

    private Component getComputerControlButtonMessage() {
        return this.labelledToggle("gui.herobrine_companion.llm_settings.computer_control_label",
                this.computerControlEnabled);
    }

    private Component getJvmCodeButtonMessage() {
        return this.labelledToggle("gui.herobrine_companion.llm_settings.jvm_code_skill_label",
                this.jvmCodeSkillEnabled);
    }

    private Component getWebLookupButtonMessage() {
        return this.labelledToggle("gui.herobrine_companion.llm_settings.web_lookup_label",
                this.webLookupEnabled);
    }

    private Component getJvmPreferredButtonMessage() {
        return this.labelledToggle("gui.herobrine_companion.llm_settings.jvm_preferred_label",
                this.jvmPreferredEnabled);
    }

    /** 形如“电脑 CMD：开”的按钮文案，标签与状态合并显示，省掉独立的标签行。 */
    private Component labelledToggle(String labelKey, boolean enabled) {
        return Component.translatable("gui.herobrine_companion.llm_settings.toggle_row",
                Component.translatable(labelKey),
                Component.translatable(enabled ? "options.on" : "options.off"));
    }

    /**
     * 面板尺寸随窗口大小等比缩放（长宽跟随窗口），带最小下限避免小窗口过挤。
     * init 与 render 共用同一份布局计算，避免两处各自推算导致按钮与文字错位。
     */
    private Layout computeLayout() {
        int screenMargin = 10;
        int maxW = Math.max(1, this.width - screenMargin * 2);
        int maxH = Math.max(1, this.height - screenMargin * 2);
        int panelWidth = Math.min(maxW, Math.max(MIN_PANEL_WIDTH, (int) (maxW * 0.62D)));
        int panelHeight = Math.min(maxH, Math.max(MIN_PANEL_HEIGHT, (int) (maxH * 0.66D)));

        // 除行间距外占用的固定高度，行间距在剩余空间内等分铺开，并保证不小于最小间距。
        int fixedSpace = ROW1_TOP + BUTTON_HEIGHT + ROW_BOTTOM_GAP + BOTTOM_BUTTONS_OFFSET;
        int rowGap = Math.max(MIN_ROW_GAP, (panelHeight - fixedSpace) / ROW_GAP_COUNT);

        int panelLeft = Math.max(screenMargin, (this.width - panelWidth) / 2);
        int panelTop = Math.max(screenMargin, (this.height - panelHeight) / 2);
        int contentLeft = panelLeft + 16;
        int contentWidth = panelWidth - 32;

        int[] rowYs = new int[ROW_COUNT];
        for (int i = 0; i < ROW_COUNT; i++) {
            rowYs[i] = panelTop + ROW1_TOP + i * rowGap;
        }

        int buttonY = panelTop + panelHeight - BOTTOM_BUTTONS_OFFSET;
        int buttonWidth = Math.min(160, Math.max(1, (contentWidth - CONTROL_GAP) / 2));
        int saveButtonX = panelLeft + (panelWidth - buttonWidth * 2 - CONTROL_GAP) / 2;
        return new Layout(panelLeft, panelTop, panelWidth, panelHeight, contentLeft, contentWidth,
                rowYs, buttonY, buttonWidth, saveButtonX, saveButtonX + buttonWidth + CONTROL_GAP);
    }

    private void saveAndClose() {
        LLMConfig.aiStreamingEnabled = this.streamingEnabled;
        LLMConfig.aiCommandMode = this.commandMode == null ? LLMConfig.CommandMode.NORMAL : this.commandMode;
        LLMConfig.aiComputerControlEnabled = this.computerControlEnabled;
        LLMConfig.aiJvmCodeSkillEnabled = this.jvmCodeSkillEnabled;
        LLMConfig.aiWebLookupEnabled = this.webLookupEnabled;
        LLMConfig.aiJvmPreferredEnabled = this.jvmPreferredEnabled;
        LLMConfig.save();
        Minecraft.getInstance().setScreen(this.lastScreen);
    }

    private record Layout(
            int panelLeft,
            int panelTop,
            int panelWidth,
            int panelHeight,
            int contentLeft,
            int contentWidth,
            int[] rowYs,
            int buttonY,
            int buttonWidth,
            int saveButtonX,
            int backButtonX
    ) {
    }
}
