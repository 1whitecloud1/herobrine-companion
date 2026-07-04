package com.whitecloud233.herobrine_companion.config;

import com.whitecloud233.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LLMSettingsScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 500;
    private static final int MAX_PANEL_HEIGHT = 340;
    private static final int BUTTON_HEIGHT = 20;
    private static final int CONTROL_GAP = 8;
    private static final int COL_BG = 0xFF2B2B2B;
    private static final int COL_BORDER = 0xFF555555;
    private static final int COL_TITLE = 0xFFCC7832;
    private static final int COL_TEXT = 0xFFA9B7C6;
    private static final int COL_INFO = 0xFF6A8759;
    private static final int COL_WARN = 0xFFFF8080;

    private final Screen lastScreen;
    private MultilineTextEditor systemPromptBox;
    private EditBox temperatureBox;
    private EditBox topPBox;
    private EditBox maxOutputTokensBox;
    private HeroScreen.ThemedButton saveButton;
    private boolean streamingEnabled;
    private LLMConfig.CommandMode commandMode;
    private Component validationMessage;
    private boolean settingsLoaded;
    private String systemPromptDraft;
    private String temperatureDraft;
    private String topPDraft;
    private String maxOutputTokensDraft;

    public LLMSettingsScreen(Screen lastScreen) {
        super(Component.translatable("gui.herobrine_companion.llm_settings.title"));
        this.lastScreen = lastScreen;
        this.validationMessage = Component.empty();
    }

    @Override
    protected void init() {
        this.captureDrafts();
        super.init();
        LLMConfig.ensureLoaded();
        if (!this.settingsLoaded) {
            this.streamingEnabled = LLMConfig.isStreamingEnabled();
            this.commandMode = LLMConfig.getCommandMode();
            this.systemPromptDraft = LLMConfig.getSystemPrompt();
            this.temperatureDraft = Double.toString(LLMConfig.getConfiguredTemperature());
            this.topPDraft = Double.toString(LLMConfig.getConfiguredTopP());
            this.maxOutputTokensDraft = Integer.toString(LLMConfig.getConfiguredMaxOutputTokens());
            this.settingsLoaded = true;
        }
        if (this.commandMode == null) {
            this.commandMode = LLMConfig.CommandMode.NORMAL;
        }

        Layout layout = this.createLayout();

        this.systemPromptBox = this.addRenderableWidget(new MultilineTextEditor(this.font,
                layout.editorX(), layout.editorY(), layout.editorWidth(), layout.editorHeight(),
                Component.translatable("gui.herobrine_companion.llm_settings.system_prompt")));
        this.systemPromptBox.setMaxLength(12000);
        this.systemPromptBox.setValue(this.systemPromptDraft);
        this.systemPromptBox.setResponder(value -> {
            this.systemPromptDraft = value;
            this.updateSaveState();
        });

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.streamingButtonX(), layout.toggleButtonY(), layout.toggleButtonWidth(), BUTTON_HEIGHT,
                this.getStreamingButtonMessage(),
                button -> {
                    this.streamingEnabled = !this.streamingEnabled;
                    button.setMessage(this.getStreamingButtonMessage());
                },
                null
        ));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.commandButtonX(), layout.toggleButtonY(), layout.toggleButtonWidth(), BUTTON_HEIGHT,
                this.getCommandModeButtonMessage(),
                button -> {
                    this.commandMode = this.commandMode.next();
                    button.setMessage(this.getCommandModeButtonMessage());
                },
                null
        ));

        this.temperatureBox = this.createNumberBox(layout.temperatureBoxX(), layout.numberBoxY(), layout.numberBoxWidth(),
                "gui.herobrine_companion.llm_settings.temperature", this.temperatureDraft);
        this.topPBox = this.createNumberBox(layout.topPBoxX(), layout.numberBoxY(), layout.numberBoxWidth(),
                "gui.herobrine_companion.llm_settings.top_p", this.topPDraft);
        this.maxOutputTokensBox = this.createNumberBox(layout.maxOutputTokensBoxX(), layout.numberBoxY(), layout.numberBoxWidth(),
                "gui.herobrine_companion.llm_settings.max_output_tokens", this.maxOutputTokensDraft);

        this.saveButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.saveButtonX(), layout.actionButtonY(), layout.actionButtonWidth(), BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.api_setup.confirm_save"),
                button -> this.saveAndClose(),
                null
        ));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.backButtonX(), layout.actionButtonY(), layout.actionButtonWidth(), BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.back"),
                button -> Minecraft.getInstance().setScreen(this.lastScreen),
                null
        ));

        this.setInitialFocus(this.systemPromptBox);
        this.updateSaveState();
    }

    private void captureDrafts() {
        if (this.systemPromptBox != null) {
            this.systemPromptDraft = this.systemPromptBox.getValue();
        }
        if (this.temperatureBox != null) {
            this.temperatureDraft = this.temperatureBox.getValue();
        }
        if (this.topPBox != null) {
            this.topPDraft = this.topPBox.getValue();
        }
        if (this.maxOutputTokensBox != null) {
            this.maxOutputTokensDraft = this.maxOutputTokensBox.getValue();
        }
    }

    private EditBox createNumberBox(int x, int y, int width, String translationKey, String initialValue) {
        EditBox box = this.addRenderableWidget(new EditBox(this.font, x, y, width, 20, Component.translatable(translationKey)));
        box.setMaxLength(16);
        box.setValue(initialValue);
        box.setResponder(value -> {
            if (box == this.temperatureBox) {
                this.temperatureDraft = value;
            } else if (box == this.topPBox) {
                this.topPDraft = value;
            } else if (box == this.maxOutputTokensBox) {
                this.maxOutputTokensDraft = value;
            }
            this.updateSaveState();
        });
        return box;
    }

    private Layout createLayout() {
        int screenMargin = (this.width < 340 || this.height < 280) ? 6 : 10;
        int panelWidth = Math.max(1, Math.min(MAX_PANEL_WIDTH, this.width - screenMargin * 2));
        int panelHeight = Math.max(1, Math.min(MAX_PANEL_HEIGHT, this.height - screenMargin * 2));
        int startX = Math.max(screenMargin, (this.width - panelWidth) / 2);
        int startY = Math.max(screenMargin, (this.height - panelHeight) / 2);
        int contentMargin = panelWidth < 330 ? 10 : 16;
        int contentWidth = Math.max(1, panelWidth - contentMargin * 2);
        int editorX = startX + contentMargin;
        int editorY = startY + 50;
        int bottomMargin = panelHeight < 300 ? 8 : 12;
        int actionButtonY = startY + panelHeight - bottomMargin - BUTTON_HEIGHT;
        int validationY = actionButtonY - 14;
        int numericHintY = validationY - 12;
        int numberBoxY = numericHintY - 30;
        int numberLabelY = numberBoxY - 12;
        int editorHintY = numberLabelY - 14;
        int toggleButtonY = editorHintY - 26;
        int toggleLabelY = toggleButtonY - 12;
        int editorHeight = Math.max(42, toggleLabelY - editorY - 8);
        int toggleButtonWidth = Math.max(1, (contentWidth - CONTROL_GAP) / 2);
        int commandButtonX = editorX + toggleButtonWidth + CONTROL_GAP;
        int numberBoxWidth = Math.max(1, (contentWidth - CONTROL_GAP * 2) / 3);
        int topPBoxX = editorX + numberBoxWidth + CONTROL_GAP;
        int maxOutputTokensBoxX = topPBoxX + numberBoxWidth + CONTROL_GAP;
        int actionButtonWidth = Math.min(120, Math.max(1, (contentWidth - CONTROL_GAP) / 2));
        int actionGroupWidth = actionButtonWidth * 2 + CONTROL_GAP;
        int saveButtonX = startX + (panelWidth - actionGroupWidth) / 2;
        int backButtonX = saveButtonX + actionButtonWidth + CONTROL_GAP;
        return new Layout(startX, startY, panelWidth, panelHeight, editorX, editorY, contentWidth, editorHeight,
                toggleLabelY, toggleButtonY, toggleButtonWidth, commandButtonX, editorHintY, numberLabelY, numberBoxY,
                numberBoxWidth, topPBoxX, maxOutputTokensBoxX, numericHintY, validationY, saveButtonX, backButtonX,
                actionButtonY, actionButtonWidth);
    }

    private Component getStreamingButtonMessage() {
        return Component.translatable("gui.herobrine_companion.llm_settings.streaming",
                Component.translatable(this.streamingEnabled ? "options.on" : "options.off"));
    }

    private Component getCommandModeButtonMessage() {
        String modeKey = this.commandMode == LLMConfig.CommandMode.EAGER
                ? "gui.herobrine_companion.llm_settings.command_mode.eager"
                : "gui.herobrine_companion.llm_settings.command_mode.normal";
        return Component.translatable("gui.herobrine_companion.llm_settings.command_mode",
                Component.translatable(modeKey));
    }

    private void updateSaveState() {
        Component message = Component.empty();
        boolean valid = true;

        if (!this.isDoubleInRange(this.temperatureBox, 0.0D, 2.0D)) {
            valid = false;
            message = Component.translatable("gui.herobrine_companion.llm_settings.validation.temperature");
        } else if (!this.isDoubleInRange(this.topPBox, 0.1D, 1.0D)) {
            valid = false;
            message = Component.translatable("gui.herobrine_companion.llm_settings.validation.top_p");
        } else if (!this.isIntInRange(this.maxOutputTokensBox, 64, 4096)) {
            valid = false;
            message = Component.translatable("gui.herobrine_companion.llm_settings.validation.max_output_tokens");
        }

        this.validationMessage = message;
        if (this.saveButton != null) {
            this.saveButton.active = valid;
        }
    }

    private boolean isDoubleInRange(EditBox box, double min, double max) {
        if (box == null) {
            return false;
        }

        try {
            double value = Double.parseDouble(box.getValue().trim());
            return !Double.isNaN(value) && !Double.isInfinite(value) && value >= min && value <= max;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean isIntInRange(EditBox box, int min, int max) {
        if (box == null) {
            return false;
        }

        try {
            int value = Integer.parseInt(box.getValue().trim());
            return value >= min && value <= max;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void saveAndClose() {
        LLMConfig.aiSystemPrompt = this.systemPromptBox.getValue();
        LLMConfig.aiStreamingEnabled = this.streamingEnabled;
        LLMConfig.aiCommandMode = this.commandMode == null ? LLMConfig.CommandMode.NORMAL : this.commandMode;
        LLMConfig.aiTemperature = Double.parseDouble(this.temperatureBox.getValue().trim());
        LLMConfig.aiTopP = Double.parseDouble(this.topPBox.getValue().trim());
        LLMConfig.aiMaxOutputTokens = Integer.parseInt(this.maxOutputTokensBox.getValue().trim());
        LLMConfig.save();
        Minecraft.getInstance().setScreen(this.lastScreen);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.systemPromptBox != null) {
            this.systemPromptBox.tick();
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No default dim overlay.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = this.createLayout();
        int centerX = layout.startX() + layout.panelWidth() / 2;

        guiGraphics.fill(layout.startX(), layout.startY(), layout.startX() + layout.panelWidth(), layout.startY() + layout.panelHeight(), COL_BG);
        guiGraphics.renderOutline(layout.startX(), layout.startY(), layout.panelWidth(), layout.panelHeight(), COL_BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, centerX, layout.startY() + 10, COL_TITLE);

        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.system_prompt"),
                layout.editorX(), layout.editorY() - 12, 0xFFFFFF, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.streaming_label"),
                layout.editorX(), layout.toggleLabelY(), 0xFFFFFF, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.command_mode_label"),
                layout.commandButtonX(), layout.toggleLabelY(), 0xFFFFFF, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.temperature"),
                layout.editorX(), layout.numberLabelY(), 0xFFFFFF, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.top_p"),
                layout.topPBoxX(), layout.numberLabelY(), 0xFFFFFF, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.max_output_tokens"),
                layout.maxOutputTokensBoxX(), layout.numberLabelY(), 0xFFFFFF, false);

        guiGraphics.drawWordWrap(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.system_prompt_hint"),
                layout.editorX(), layout.editorY() + 32, Math.max(1, layout.editorWidth() - 12), COL_TEXT);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.editor_hint"),
                centerX, layout.editorHintY(), COL_INFO);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.numeric_hint"),
                centerX, layout.numericHintY(), COL_INFO);

        if (!this.validationMessage.getString().isEmpty()) {
            guiGraphics.drawCenteredString(this.font, this.validationMessage, centerX, layout.validationY(), COL_WARN);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Layout(
            int startX,
            int startY,
            int panelWidth,
            int panelHeight,
            int editorX,
            int editorY,
            int editorWidth,
            int editorHeight,
            int toggleLabelY,
            int toggleButtonY,
            int toggleButtonWidth,
            int commandButtonX,
            int editorHintY,
            int numberLabelY,
            int numberBoxY,
            int numberBoxWidth,
            int topPBoxX,
            int maxOutputTokensBoxX,
            int numericHintY,
            int validationY,
            int saveButtonX,
            int backButtonX,
            int actionButtonY,
            int actionButtonWidth
    ) {
        int streamingButtonX() {
            return this.editorX;
        }

        int temperatureBoxX() {
            return this.editorX;
        }
    }
}


