package com.whitecloud233.herobrine_companion.config;

import com.whitecloud233.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LLMSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 390;
    private static final int PANEL_HEIGHT = 330;
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
    private Component validationMessage;

    public LLMSettingsScreen(Screen lastScreen) {
        super(Component.translatable("gui.herobrine_companion.llm_settings.title"));
        this.lastScreen = lastScreen;
        this.validationMessage = Component.empty();
    }

    @Override
    protected void init() {
        super.init();
        LLMConfig.ensureLoaded();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startX = centerX - PANEL_WIDTH / 2;
        int startY = centerY - PANEL_HEIGHT / 2;

        this.streamingEnabled = LLMConfig.isStreamingEnabled();

        this.systemPromptBox = this.addRenderableWidget(new MultilineTextEditor(this.font,
                startX + 16, startY + 50, PANEL_WIDTH - 32, 128,
                Component.translatable("gui.herobrine_companion.llm_settings.system_prompt")));
        this.systemPromptBox.setMaxLength(12000);
        this.systemPromptBox.setValue(LLMConfig.getSystemPrompt());
        this.systemPromptBox.setResponder(value -> this.updateSaveState());

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                startX + 16, startY + 186, PANEL_WIDTH - 32, 20,
                this.getStreamingButtonMessage(),
                button -> {
                    this.streamingEnabled = !this.streamingEnabled;
                    button.setMessage(this.getStreamingButtonMessage());
                },
                null
        ));

        int rowY = startY + 232;
        this.temperatureBox = this.createNumberBox(startX + 16, rowY, "gui.herobrine_companion.llm_settings.temperature",
                Double.toString(LLMConfig.getConfiguredTemperature()));
        this.topPBox = this.createNumberBox(startX + 126, rowY, "gui.herobrine_companion.llm_settings.top_p",
                Double.toString(LLMConfig.getConfiguredTopP()));
        this.maxOutputTokensBox = this.createNumberBox(startX + 236, rowY, "gui.herobrine_companion.llm_settings.max_output_tokens",
                Integer.toString(LLMConfig.getConfiguredMaxOutputTokens()));

        this.saveButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                centerX - 106, startY + PANEL_HEIGHT - 28, 100, 20,
                Component.translatable("gui.herobrine_companion.api_setup.confirm_save"),
                button -> this.saveAndClose(),
                null
        ));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                centerX + 6, startY + PANEL_HEIGHT - 28, 100, 20,
                Component.translatable("gui.herobrine_companion.back"),
                button -> Minecraft.getInstance().setScreen(this.lastScreen),
                null
        ));

        this.setInitialFocus(this.systemPromptBox);
        this.updateSaveState();
    }

    private EditBox createNumberBox(int x, int y, String translationKey, String initialValue) {
        EditBox box = this.addRenderableWidget(new EditBox(this.font, x, y, 88, 20, Component.translatable(translationKey)));
        box.setMaxLength(16);
        box.setValue(initialValue);
        box.setResponder(value -> this.updateSaveState());
        return box;
    }

    private Component getStreamingButtonMessage() {
        return Component.translatable("gui.herobrine_companion.llm_settings.streaming",
                Component.translatable(this.streamingEnabled ? "options.on" : "options.off"));
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
        LLMConfig.aiTemperature = Double.parseDouble(this.temperatureBox.getValue().trim());
        LLMConfig.aiTopP = Double.parseDouble(this.topPBox.getValue().trim());
        LLMConfig.aiMaxOutputTokens = Integer.parseInt(this.maxOutputTokensBox.getValue().trim());
        LLMConfig.save();
        Minecraft.getInstance().setScreen(this.lastScreen);
    }

    @Override
    public void tick() {
        super.tick();
        this.systemPromptBox.tick();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No default dim overlay.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startX = centerX - PANEL_WIDTH / 2;
        int startY = centerY - PANEL_HEIGHT / 2;

        guiGraphics.fill(startX, startY, startX + PANEL_WIDTH, startY + PANEL_HEIGHT, COL_BG);
        guiGraphics.renderOutline(startX, startY, PANEL_WIDTH, PANEL_HEIGHT, COL_BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, centerX, startY + 10, COL_TITLE);

        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.system_prompt"),
                startX + 16, startY + 38, 0xFFFFFF, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.streaming_label"),
                startX + 16, startY + 174, 0xFFFFFF, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.temperature"),
                startX + 16, startY + 220, 0xFFFFFF, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.top_p"),
                startX + 126, startY + 220, 0xFFFFFF, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.max_output_tokens"),
                startX + 236, startY + 220, 0xFFFFFF, false);

        guiGraphics.drawWordWrap(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.system_prompt_hint"),
                startX + 16, startY + 82, PANEL_WIDTH - 44, COL_TEXT);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.editor_hint"),
                centerX, startY + 211, COL_INFO);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.herobrine_companion.llm_settings.numeric_hint"),
                centerX, startY + 278, COL_INFO);

        if (!this.validationMessage.getString().isEmpty()) {
            guiGraphics.drawCenteredString(this.font, this.validationMessage, centerX, startY + 290, COL_WARN);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}


