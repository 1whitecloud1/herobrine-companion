package com.whitecloud233.modid.herobrine_companion.config;

import com.whitecloud233.modid.herobrine_companion.client.service.LLMConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class ApiKeyInputScreen extends Screen {
    private final Screen lastScreen;
    private LLMConfig.Provider selectedProvider;
    private Button deepseekButton;
    private Button openrouterButton;
    private Button qiniuButton;
    private Button customButton;
    private Button saveButton;
    private EditBox apiKeyBox;
    private EditBox providerIdBox;
    private EditBox endpointBox;
    private EditBox modelBox;
    private String customProviderIdDraft;
    private String customEndpointDraft;
    private String customModelDraft;

    public ApiKeyInputScreen(Screen lastScreen) {
        // 使用翻译键替换纯文本
        super(Component.translatable("gui.herobrine_companion.api_setup.title"));
        this.lastScreen = lastScreen;
        this.selectedProvider = LLMConfig.getProvider();
        this.customProviderIdDraft = this.selectedProvider == LLMConfig.Provider.CUSTOM ? LLMConfig.getStoredProviderId() : LLMConfig.Provider.CUSTOM.getId();
        this.customEndpointDraft = this.selectedProvider == LLMConfig.Provider.CUSTOM ? LLMConfig.getResolvedEndpoint() : "";
        this.customModelDraft = this.selectedProvider == LLMConfig.Provider.CUSTOM ? LLMConfig.getResolvedModel() : "";
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int contentWidth = 220;
        int providerButtonWidth = 52;
        int providerButtonSpacing = 4;
        int providerStartX = centerX - (contentWidth / 2);

        if (this.selectedProvider == null) {
            this.selectedProvider = LLMConfig.Provider.QINIU_CLOUD;
        }

        this.deepseekButton = this.addRenderableWidget(this.createProviderButton(providerStartX, 60, providerButtonWidth, LLMConfig.Provider.DEEPSEEK_OFFICIAL));
        this.openrouterButton = this.addRenderableWidget(this.createProviderButton(providerStartX + providerButtonWidth + providerButtonSpacing, 60, providerButtonWidth, LLMConfig.Provider.OPENROUTER));
        this.qiniuButton = this.addRenderableWidget(this.createProviderButton(providerStartX + (providerButtonWidth + providerButtonSpacing) * 2, 60, providerButtonWidth, LLMConfig.Provider.QINIU_CLOUD));
        this.customButton = this.addRenderableWidget(this.createProviderButton(providerStartX + (providerButtonWidth + providerButtonSpacing) * 3, 60, providerButtonWidth, LLMConfig.Provider.CUSTOM));

        // 创建 API Key 输入框，使用翻译键
        this.apiKeyBox = new EditBox(this.font, centerX - (contentWidth / 2), 100, contentWidth, 20, Component.translatable("gui.herobrine_companion.api_setup.api_key"));
        this.apiKeyBox.setMaxLength(256);
        this.apiKeyBox.setValue(LLMConfig.isKeyMissing() ? "" : LLMConfig.aiApiKey);
        this.apiKeyBox.setResponder(value -> this.updateSaveButtonState());
        this.addRenderableWidget(this.apiKeyBox);

        this.providerIdBox = new EditBox(this.font, centerX - (contentWidth / 2), 130, contentWidth, 20, Component.translatable("gui.herobrine_companion.api_setup.provider_id"));
        this.providerIdBox.setMaxLength(256);
        this.providerIdBox.setValue(this.isCustomProviderSelected() ? this.customProviderIdDraft : LLMConfig.getStoredProviderId());
        this.providerIdBox.setResponder(value -> {
            if (this.isCustomProviderSelected()) {
                this.customProviderIdDraft = value.trim();
            }
            this.updateSaveButtonState();
        });
        this.addRenderableWidget(this.providerIdBox);

        this.endpointBox = new EditBox(this.font, centerX - (contentWidth / 2), 160, contentWidth, 20, Component.translatable("gui.herobrine_companion.api_setup.endpoint_input"));
        this.endpointBox.setMaxLength(512);
        this.endpointBox.setValue(this.isCustomProviderSelected() ? this.customEndpointDraft : LLMConfig.getResolvedEndpoint());
        this.endpointBox.setResponder(value -> {
            if (this.isCustomProviderSelected()) {
                this.customEndpointDraft = value.trim();
            }
            this.updateSaveButtonState();
        });
        this.addRenderableWidget(this.endpointBox);

        this.modelBox = new EditBox(this.font, centerX - (contentWidth / 2), 190, contentWidth, 20, Component.translatable("gui.herobrine_companion.api_setup.model"));
        this.modelBox.setMaxLength(256);
        this.modelBox.setValue(this.isCustomProviderSelected() ? this.customModelDraft : LLMConfig.getResolvedModel());
        this.modelBox.setResponder(value -> {
            if (this.isCustomProviderSelected()) {
                this.customModelDraft = value.trim();
            }
            this.updateSaveButtonState();
        });
        this.addRenderableWidget(this.modelBox);

        this.refreshProviderButtons();
        this.refreshInputMode();

        // 保存按钮，使用翻译键
        this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.api_setup.confirm_save"), (button) -> {
            if (this.isCustomProviderSelected()) {
                LLMConfig.setCustomProviderId(this.providerIdBox.getValue().trim());
                LLMConfig.aiEndpoint = this.endpointBox.getValue().trim();
            } else {
                LLMConfig.setProvider(this.selectedProvider);
            }
            LLMConfig.aiApiKey = this.apiKeyBox.getValue().trim();
            LLMConfig.aiModel = this.modelBox.getValue().trim();
            LLMConfig.save(); // 写入根目录私密文件
            this.minecraft.setScreen(this.lastScreen);
        }).pos(centerX - (contentWidth / 2), 220).size(106, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.api_setup.cancel"), (button) ->
                this.minecraft.setScreen(this.lastScreen))
                .pos(centerX + 4, 220)
                .size(106, 20)
                .build());

        this.setInitialFocus(this.apiKeyBox);
        this.updateSaveButtonState();
    }

    @Override
    public void tick() {
        this.apiKeyBox.tick();
        this.providerIdBox.tick();
        this.endpointBox.tick();
        this.modelBox.tick();
    }

    private Button createProviderButton(int x, int y, int width, LLMConfig.Provider provider) {
        return Button.builder(this.getProviderButtonMessage(provider), (button) -> this.selectProvider(provider))
                .pos(x, y)
                .size(width, 20)
                .build();
    }

    private void selectProvider(LLMConfig.Provider provider) {
        if (provider == null) {
            provider = LLMConfig.Provider.QINIU_CLOUD;
        }

        if (this.isCustomProviderSelected()) {
            if (this.providerIdBox != null) {
                this.customProviderIdDraft = this.providerIdBox.getValue().trim();
            }
            if (this.endpointBox != null) {
                this.customEndpointDraft = this.endpointBox.getValue().trim();
            }
            if (this.modelBox != null) {
                this.customModelDraft = this.modelBox.getValue().trim();
            }
        }

        LLMConfig.Provider previousProvider = this.selectedProvider;
        this.selectedProvider = provider;

        if (this.modelBox != null) {
            String currentModel = this.modelBox.getValue().trim();
            if (this.isCustomProviderSelected()) {
                this.modelBox.setValue(this.customModelDraft);
            } else if (currentModel.isEmpty() || previousProvider == null || currentModel.equals(previousProvider.getDefaultModel())) {
                this.modelBox.setValue(this.selectedProvider.getDefaultModel());
            }
        }

        if (this.providerIdBox != null) {
            if (this.isCustomProviderSelected()) {
                this.providerIdBox.setValue(this.customProviderIdDraft);
            } else {
                this.providerIdBox.setValue(this.selectedProvider.getId());
            }
        }

        if (this.endpointBox != null) {
            if (this.isCustomProviderSelected()) {
                this.endpointBox.setValue(this.customEndpointDraft);
            } else {
                this.endpointBox.setValue(this.selectedProvider.getEndpoint());
            }
        }

        this.refreshProviderButtons();
        this.refreshInputMode();
        this.updateSaveButtonState();
    }

    private void refreshProviderButtons() {
        this.updateProviderButton(this.deepseekButton, LLMConfig.Provider.DEEPSEEK_OFFICIAL);
        this.updateProviderButton(this.openrouterButton, LLMConfig.Provider.OPENROUTER);
        this.updateProviderButton(this.qiniuButton, LLMConfig.Provider.QINIU_CLOUD);
        this.updateProviderButton(this.customButton, LLMConfig.Provider.CUSTOM);
    }

    private void refreshInputMode() {
        boolean customSelected = this.isCustomProviderSelected();
        if (this.providerIdBox != null) {
            this.providerIdBox.setEditable(customSelected);
            this.providerIdBox.active = customSelected;
        }
        if (this.endpointBox != null) {
            this.endpointBox.setEditable(customSelected);
            this.endpointBox.active = customSelected;
        }
    }

    private void updateProviderButton(Button button, LLMConfig.Provider provider) {
        if (button != null) {
            button.setMessage(this.getProviderButtonMessage(provider));
        }
    }

    private Component getProviderButtonMessage(LLMConfig.Provider provider) {
        if (provider == this.selectedProvider) {
            return Component.literal("✓ ").append(this.getProviderName(provider)).withStyle(ChatFormatting.GREEN);
        }
        return this.getProviderName(provider);
    }

    private Component getProviderName(LLMConfig.Provider provider) {
        return switch (provider) {
            case DEEPSEEK_OFFICIAL -> Component.translatable("gui.herobrine_companion.api_setup.provider.deepseek");
            case OPENROUTER -> Component.translatable("gui.herobrine_companion.api_setup.provider.openrouter");
            case QINIU_CLOUD -> Component.translatable("gui.herobrine_companion.api_setup.provider.qiniu");
            case CUSTOM -> Component.translatable("gui.herobrine_companion.api_setup.provider.custom");
        };
    }

    private Component getProviderHint() {
        return switch (this.selectedProvider) {
            case DEEPSEEK_OFFICIAL -> Component.translatable("gui.herobrine_companion.api_setup.provider_hint.deepseek", this.selectedProvider.getDefaultModel());
            case OPENROUTER -> Component.translatable("gui.herobrine_companion.api_setup.provider_hint.openrouter", this.selectedProvider.getDefaultModel());
            case QINIU_CLOUD -> Component.translatable("gui.herobrine_companion.api_setup.provider_hint.qiniu", this.selectedProvider.getDefaultModel());
            case CUSTOM -> Component.translatable("gui.herobrine_companion.api_setup.provider_hint.custom");
        };
    }

    private void updateSaveButtonState() {
        if (this.saveButton != null) {
            boolean hasRequiredCoreFields = !this.apiKeyBox.getValue().trim().isEmpty() && !this.modelBox.getValue().trim().isEmpty();
            if (this.isCustomProviderSelected()) {
                hasRequiredCoreFields = hasRequiredCoreFields
                        && !this.providerIdBox.getValue().trim().isEmpty()
                        && !this.endpointBox.getValue().trim().isEmpty();
            }
            this.saveButton.active = hasRequiredCoreFields;
        }
    }

    private boolean isCustomProviderSelected() {
        return this.selectedProvider == LLMConfig.Provider.CUSTOM;
    }

    private int drawWrappedCenteredText(GuiGraphics guiGraphics, Component text, int centerX, int startY, int maxWidth, int color) {
        int y = startY;
        for (FormattedCharSequence line : this.font.split(text, maxWidth)) {
            guiGraphics.drawString(this.font, line, centerX - this.font.width(line) / 2, y, color);
            y += 10;
        }
        return y;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // 渲染提示文字，使用翻译键
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.api_setup.prompt_input"), this.width / 2, 35, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.api_setup.prompt_provider"), this.width / 2, 48, 0xAAAAAA);

        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.api_setup.api_key"), this.width / 2 - 110, 88, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.api_setup.provider_id"), this.width / 2 - 110, 118, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.api_setup.endpoint_input"), this.width / 2 - 110, 148, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.api_setup.model"), this.width / 2 - 110, 178, 0xFFFFFF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int textY = 252;
        textY = this.drawWrappedCenteredText(guiGraphics,
                Component.translatable("gui.herobrine_companion.api_setup.endpoint", this.endpointBox.getValue().trim().isEmpty() ? this.selectedProvider.getEndpoint() : this.endpointBox.getValue().trim()),
                this.width / 2, textY, 260, 0x9FD2FF);
        textY = this.drawWrappedCenteredText(guiGraphics, this.getProviderHint(), this.width / 2, textY + 2, 260, 0xCFCFCF);
        this.drawWrappedCenteredText(guiGraphics,
                Component.translatable("gui.herobrine_companion.api_setup.prompt_safe"),
                this.width / 2, textY + 4, 260, 0xAAAAAA);

        if (!this.saveButton.active) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.api_setup.warning_required"), this.width / 2, 210, 0xFF8080);
        }
    }
}