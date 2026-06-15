package com.whitecloud233.herobrine_companion.config;

import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.herobrine_companion.client.service.LLMModelDiscovery;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class ApiKeyInputScreen extends Screen {
    private static final int MODEL_DROPDOWN_ROW_HEIGHT = 18;
    private static final int MODEL_DROPDOWN_MAX_ROWS = 6;

    private final Screen lastScreen;
    private LLMConfig.Provider selectedProvider;
    private Button deepseekButton;
    private Button openrouterButton;
    private Button qiniuButton;
    private Button customButton;
    private Button saveButton;
    private Button cancelButton;
    private Button fetchModelsButton;
    private EditBox apiKeyBox;
    private EditBox providerIdBox;
    private EditBox endpointBox;
    private EditBox modelBox;
    private String customProviderIdDraft;
    private String customEndpointDraft;
    private String customModelDraft;
    private List<String> discoveredModels = List.of();
    private int discoveredModelIndex = -1;
    private boolean modelDiscoveryInFlight = false;
    private Component modelDiscoveryStatus = Component.literal("");
    private int modelDiscoveryStatusColor = 0xAAAAAA;
    private String lastModelDiscoveryKey = "";
    private boolean modelDropdownOpen = false;
    private int modelDropdownScrollIndex = 0;
    private int modelDropdownX;
    private int modelDropdownY;
    private int modelDropdownWidth;

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
        int modelButtonWidth = 64;
        int modelButtonSpacing = 4;
        int modelBoxWidth = contentWidth - modelButtonWidth - modelButtonSpacing;
        int providerStartX = centerX - (contentWidth / 2);
        this.modelDropdownX = centerX - (contentWidth / 2);
        this.modelDropdownY = 211;
        this.modelDropdownWidth = contentWidth;

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
        this.apiKeyBox.setResponder(value -> {
            this.clearDiscoveredModels();
            this.updateSaveButtonState();
        });
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
            this.clearDiscoveredModels();
            this.updateSaveButtonState();
        });
        this.addRenderableWidget(this.endpointBox);

        this.modelBox = new EditBox(this.font, centerX - (contentWidth / 2), 190, modelBoxWidth, 20, Component.translatable("gui.herobrine_companion.api_setup.model"));
        this.modelBox.setMaxLength(256);
        this.modelBox.setValue(this.isCustomProviderSelected() ? this.customModelDraft : LLMConfig.getResolvedModel());
        this.modelBox.setResponder(value -> {
            if (this.isCustomProviderSelected()) {
                this.customModelDraft = value.trim();
            }
            this.syncDiscoveredModelIndex(value.trim());
            this.updateSaveButtonState();
        });
        this.addRenderableWidget(this.modelBox);

        this.fetchModelsButton = this.addRenderableWidget(Button.builder(this.getFetchModelsButtonMessage(), (button) -> this.handleFetchModelsButton())
                .pos(centerX - (contentWidth / 2) + modelBoxWidth + modelButtonSpacing, 190)
                .size(modelButtonWidth, 20)
                .build());

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
        }).pos(centerX - (contentWidth / 2), 224).size(106, 20).build());

        this.cancelButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.api_setup.cancel"), (button) ->
                        this.minecraft.setScreen(this.lastScreen))
                .pos(centerX + 4, 224)
                .size(106, 20)
                .build());

        this.setInitialFocus(this.apiKeyBox);
        this.updateSaveButtonState();
    }

    @Override
    public void tick() {
        super.tick();
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
        this.clearDiscoveredModels();
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
        this.updateFetchModelsButtonState();
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
            this.saveButton.visible = !this.modelDropdownOpen;
        }
        if (this.cancelButton != null) {
            this.cancelButton.active = !this.modelDropdownOpen;
            this.cancelButton.visible = !this.modelDropdownOpen;
        }
        this.updateFetchModelsButtonState();
    }

    private void setModelDropdownOpen(boolean open) {
        this.modelDropdownOpen = open && !this.discoveredModels.isEmpty();
        this.updateSaveButtonState();
    }

    private void updateFetchModelsButtonState() {
        if (this.fetchModelsButton != null) {
            this.fetchModelsButton.setMessage(this.getFetchModelsButtonMessage());
            this.fetchModelsButton.active = !this.modelDiscoveryInFlight
                    && (!this.discoveredModels.isEmpty()
                    || (!this.apiKeyBox.getValue().trim().isEmpty()
                    && !this.getCurrentEndpointInput().isEmpty()));
        }
    }

    private Component getFetchModelsButtonMessage() {
        if (this.modelDiscoveryInFlight) {
            return Component.translatable("gui.herobrine_companion.api_setup.fetching_models");
        }
        if (!this.discoveredModels.isEmpty()) {
            return Component.translatable("gui.herobrine_companion.api_setup.model_dropdown");
        }
        return Component.translatable("gui.herobrine_companion.api_setup.fetch_models");
    }

    private void handleFetchModelsButton() {
        if (this.modelDiscoveryInFlight) {
            return;
        }

        String discoveryKey = this.buildModelDiscoveryKey();
        if (!this.discoveredModels.isEmpty() && discoveryKey.equals(this.lastModelDiscoveryKey)) {
            this.toggleModelDropdown();
            return;
        }

        this.fetchModels(discoveryKey);
    }

    private void fetchModels(String discoveryKey) {
        String endpoint = this.getCurrentEndpointInput();
        String apiKey = this.apiKeyBox.getValue().trim();
        if (endpoint.isEmpty() || apiKey.isEmpty() || apiKey.equals(LLMConfig.getDefaultApiKeyPlaceholder())) {
            this.modelDiscoveryStatus = Component.translatable("gui.herobrine_companion.api_setup.model_discovery_required");
            this.modelDiscoveryStatusColor = 0xFF8080;
            this.clearDiscoveredModelListOnly();
            this.updateFetchModelsButtonState();
            return;
        }

        LLMConfig.Provider provider = this.selectedProvider == null ? LLMConfig.Provider.QINIU_CLOUD : this.selectedProvider;
        LLMConfig.EndpointFormat endpointFormat = LLMConfig.detectEndpointFormat(endpoint);
        this.modelDiscoveryInFlight = true;
        this.modelDiscoveryStatus = Component.translatable("gui.herobrine_companion.api_setup.model_discovery_fetching");
        this.modelDiscoveryStatusColor = 0xAAAAAA;
        this.clearDiscoveredModelListOnly();
        this.updateFetchModelsButtonState();

        LLMModelDiscovery.fetchModels(endpoint, apiKey, provider, endpointFormat)
                .thenAccept(result -> {
                    if (this.minecraft != null) {
                        this.minecraft.execute(() -> {
                            if (this.minecraft.screen == this) {
                                this.handleModelDiscoveryResult(discoveryKey, result);
                            }
                        });
                    }
                });
    }

    private void handleModelDiscoveryResult(String discoveryKey, LLMModelDiscovery.ModelDiscoveryResult result) {
        this.modelDiscoveryInFlight = false;
        if (!discoveryKey.equals(this.buildModelDiscoveryKey())) {
            this.modelDiscoveryStatus = Component.translatable("gui.herobrine_companion.api_setup.model_discovery_changed");
            this.modelDiscoveryStatusColor = 0xAAAAAA;
            this.updateFetchModelsButtonState();
            return;
        }

        if (result.success()) {
            this.discoveredModels = result.models();
            this.lastModelDiscoveryKey = discoveryKey;
            if (this.discoveredModels.isEmpty()) {
                this.discoveredModelIndex = -1;
                this.modelDiscoveryStatus = Component.translatable("gui.herobrine_companion.api_setup.model_discovery_empty");
                this.modelDiscoveryStatusColor = 0xFFB366;
            } else {
                String currentModel = this.modelBox.getValue().trim();
                int currentIndex = this.discoveredModels.indexOf(currentModel);
                this.discoveredModelIndex = currentIndex;
                if (currentModel.isEmpty()) {
                    this.discoveredModelIndex = 0;
                    this.applyDiscoveredModel(this.discoveredModelIndex);
                }
                this.setModelDropdownOpen(true);
                this.ensureSelectedModelVisible();
                this.modelDiscoveryStatus = Component.translatable(
                        "gui.herobrine_companion.api_setup.model_discovery_success",
                        this.discoveredModels.size());
                this.modelDiscoveryStatusColor = 0x80FF80;
            }
        } else if (result.httpStatus() > 0) {
            this.modelDiscoveryStatus = Component.translatable("gui.herobrine_companion.api_setup.model_discovery_http_error", result.httpStatus());
            this.modelDiscoveryStatusColor = 0xFF8080;
            this.clearDiscoveredModelListOnly();
        } else {
            this.modelDiscoveryStatus = Component.translatable("gui.herobrine_companion.api_setup.model_discovery_failed", result.error());
            this.modelDiscoveryStatusColor = 0xFF8080;
            this.clearDiscoveredModelListOnly();
        }

        this.updateSaveButtonState();
    }

    private void toggleModelDropdown() {
        if (this.discoveredModels.isEmpty()) {
            return;
        }
        this.syncDiscoveredModelIndex(this.modelBox.getValue().trim());
        this.ensureSelectedModelVisible();
        this.setModelDropdownOpen(!this.modelDropdownOpen);
    }

    private void selectDiscoveredModel(int index) {
        if (index < 0 || index >= this.discoveredModels.size()) {
            return;
        }
        this.discoveredModelIndex = index;
        this.applyDiscoveredModel(index);
        this.setModelDropdownOpen(false);
        this.modelDiscoveryStatus = Component.translatable(
                "gui.herobrine_companion.api_setup.model_discovery_selected",
                index + 1,
                this.discoveredModels.size(),
                this.discoveredModels.get(index));
        this.modelDiscoveryStatusColor = 0x9FD2FF;
        this.updateSaveButtonState();
    }

    private void applyDiscoveredModel(int index) {
        if (index < 0 || index >= this.discoveredModels.size()) {
            return;
        }
        String model = this.discoveredModels.get(index);
        this.modelBox.setValue(model);
        if (this.isCustomProviderSelected()) {
            this.customModelDraft = model;
        }
    }

    private void syncDiscoveredModelIndex(String model) {
        if (this.discoveredModels.isEmpty()) {
            this.discoveredModelIndex = -1;
            return;
        }
        this.discoveredModelIndex = this.discoveredModels.indexOf(model);
    }

    private void clearDiscoveredModels() {
        this.clearDiscoveredModelListOnly();
        this.modelDiscoveryStatus = Component.literal("");
        this.modelDiscoveryStatusColor = 0xAAAAAA;
        this.updateFetchModelsButtonState();
    }

    private void clearDiscoveredModelListOnly() {
        this.discoveredModels = List.of();
        this.discoveredModelIndex = -1;
        this.lastModelDiscoveryKey = "";
        this.setModelDropdownOpen(false);
        this.modelDropdownScrollIndex = 0;
    }

    private String buildModelDiscoveryKey() {
        String apiKey = this.apiKeyBox == null ? "" : this.apiKeyBox.getValue().trim();
        String apiKeyHash = apiKey.isEmpty() ? "" : Integer.toHexString(apiKey.hashCode());
        LLMConfig.Provider provider = this.selectedProvider == null ? LLMConfig.Provider.QINIU_CLOUD : this.selectedProvider;
        return provider.getId() + "|" + this.getCurrentEndpointInput() + "|" + apiKeyHash;
    }

    private String getCurrentEndpointInput() {
        if (this.endpointBox != null && !this.endpointBox.getValue().trim().isEmpty()) {
            return this.endpointBox.getValue().trim();
        }
        LLMConfig.Provider provider = this.selectedProvider == null ? LLMConfig.Provider.QINIU_CLOUD : this.selectedProvider;
        return provider.getEndpoint();
    }

    private int getModelDropdownVisibleRows() {
        return Math.min(MODEL_DROPDOWN_MAX_ROWS, this.discoveredModels.size());
    }

    private int getModelDropdownHeight() {
        return this.getModelDropdownVisibleRows() * MODEL_DROPDOWN_ROW_HEIGHT + 2;
    }

    private boolean isMouseInsideModelDropdown(double mouseX, double mouseY) {
        return this.modelDropdownOpen
                && mouseX >= this.modelDropdownX
                && mouseX < this.modelDropdownX + this.modelDropdownWidth
                && mouseY >= this.modelDropdownY
                && mouseY < this.modelDropdownY + this.getModelDropdownHeight();
    }

    private void ensureSelectedModelVisible() {
        int visibleRows = this.getModelDropdownVisibleRows();
        if (visibleRows <= 0) {
            this.modelDropdownScrollIndex = 0;
            return;
        }

        int maxScroll = Math.max(0, this.discoveredModels.size() - visibleRows);
        if (this.discoveredModelIndex < 0) {
            this.modelDropdownScrollIndex = Math.min(this.modelDropdownScrollIndex, maxScroll);
            return;
        }
        if (this.discoveredModelIndex < this.modelDropdownScrollIndex) {
            this.modelDropdownScrollIndex = this.discoveredModelIndex;
        } else if (this.discoveredModelIndex >= this.modelDropdownScrollIndex + visibleRows) {
            this.modelDropdownScrollIndex = this.discoveredModelIndex - visibleRows + 1;
        }
        this.modelDropdownScrollIndex = Math.max(0, Math.min(maxScroll, this.modelDropdownScrollIndex));
    }

    private void scrollModelDropdown(int direction) {
        int visibleRows = this.getModelDropdownVisibleRows();
        int maxScroll = Math.max(0, this.discoveredModels.size() - visibleRows);
        this.modelDropdownScrollIndex = Math.max(0, Math.min(maxScroll, this.modelDropdownScrollIndex + direction));
    }

    private void renderModelDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.modelDropdownOpen || this.discoveredModels.isEmpty()) {
            return;
        }

        int visibleRows = this.getModelDropdownVisibleRows();
        int dropdownHeight = this.getModelDropdownHeight();
        guiGraphics.fill(this.modelDropdownX, this.modelDropdownY,
                this.modelDropdownX + this.modelDropdownWidth, this.modelDropdownY + dropdownHeight, 0xFF101010);
        guiGraphics.renderOutline(this.modelDropdownX, this.modelDropdownY, this.modelDropdownWidth, dropdownHeight, 0xFF6C6C6C);

        for (int row = 0; row < visibleRows; row++) {
            int modelIndex = this.modelDropdownScrollIndex + row;
            if (modelIndex >= this.discoveredModels.size()) {
                break;
            }

            int rowY = this.modelDropdownY + 1 + row * MODEL_DROPDOWN_ROW_HEIGHT;
            boolean hovered = mouseX >= this.modelDropdownX
                    && mouseX < this.modelDropdownX + this.modelDropdownWidth
                    && mouseY >= rowY
                    && mouseY < rowY + MODEL_DROPDOWN_ROW_HEIGHT;
            boolean selected = modelIndex == this.discoveredModelIndex;
            if (selected) {
                guiGraphics.fill(this.modelDropdownX + 1, rowY,
                        this.modelDropdownX + this.modelDropdownWidth - 1, rowY + MODEL_DROPDOWN_ROW_HEIGHT, 0xD0406040);
            } else if (hovered) {
                guiGraphics.fill(this.modelDropdownX + 1, rowY,
                        this.modelDropdownX + this.modelDropdownWidth - 1, rowY + MODEL_DROPDOWN_ROW_HEIGHT, 0xD0353535);
            }

            String model = this.discoveredModels.get(modelIndex);
            String prefix = selected ? "> " : "  ";
            String display = this.trimToWidth(prefix + model, this.modelDropdownWidth - 10);
            guiGraphics.drawString(this.font, display, this.modelDropdownX + 5, rowY + 5,
                    selected ? 0xB8FFB8 : 0xE6E6E6, false);
        }

        if (this.discoveredModels.size() > visibleRows) {
            int trackX = this.modelDropdownX + this.modelDropdownWidth - 4;
            int trackTop = this.modelDropdownY + 3;
            int trackHeight = dropdownHeight - 6;
            int maxScroll = this.discoveredModels.size() - visibleRows;
            int thumbHeight = Math.max(8, trackHeight * visibleRows / this.discoveredModels.size());
            int thumbTop = trackTop + (trackHeight - thumbHeight) * this.modelDropdownScrollIndex / Math.max(1, maxScroll);
            guiGraphics.fill(trackX, trackTop, trackX + 2, trackTop + trackHeight, 0xFF333333);
            guiGraphics.fill(trackX, thumbTop, trackX + 2, thumbTop + thumbHeight, 0xFFAAAAAA);
        }
    }

    private String trimToWidth(String value, int maxWidth) {
        if (this.font.width(value) <= maxWidth) {
            return value;
        }

        String trimmed = value;
        while (trimmed.length() > 1 && this.font.width(trimmed + "...") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    private boolean isCustomProviderSelected() {
        return this.selectedProvider == LLMConfig.Provider.CUSTOM;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1.21.1 留空：禁用原版自带的世界模糊和黑色背景遮罩，保持和 HeroScreen 视觉一致
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
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 渲染提示文字，使用翻译键
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.api_setup.prompt_input"), this.width / 2, 35, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.api_setup.prompt_provider"), this.width / 2, 48, 0xAAAAAA);

        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.api_setup.api_key"), this.width / 2 - 110, 88, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.api_setup.provider_id"), this.width / 2 - 110, 118, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.api_setup.endpoint_input"), this.width / 2 - 110, 148, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.api_setup.model"), this.width / 2 - 110, 178, 0xFFFFFF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (this.modelDropdownOpen) {
            this.renderModelDropdown(guiGraphics, mouseX, mouseY);
            return;
        }

        int textY = 250;
        if (!this.modelDiscoveryStatus.getString().isBlank()) {
            textY = this.drawWrappedCenteredText(guiGraphics, this.modelDiscoveryStatus, this.width / 2, textY, 260, this.modelDiscoveryStatusColor) + 2;
        } else if (!this.saveButton.active) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.api_setup.warning_required"), this.width / 2, 212, 0xFF8080);
        }

        textY = this.drawWrappedCenteredText(guiGraphics,
                Component.translatable("gui.herobrine_companion.api_setup.endpoint", this.endpointBox.getValue().trim().isEmpty() ? this.selectedProvider.getEndpoint() : this.endpointBox.getValue().trim()),
                this.width / 2, textY, 260, 0x9FD2FF);
        textY = this.drawWrappedCenteredText(guiGraphics, this.getProviderHint(), this.width / 2, textY + 2, 260, 0xCFCFCF);
        this.drawWrappedCenteredText(guiGraphics,
                Component.translatable("gui.herobrine_companion.api_setup.prompt_safe"),
                this.width / 2, textY + 4, 260, 0xAAAAAA);

        this.renderModelDropdown(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.modelDropdownOpen) {
            if (button == 0 && this.isMouseInsideModelDropdown(mouseX, mouseY)) {
                int row = ((int) mouseY - this.modelDropdownY - 1) / MODEL_DROPDOWN_ROW_HEIGHT;
                int modelIndex = this.modelDropdownScrollIndex + row;
                this.selectDiscoveredModel(modelIndex);
                return true;
            }

            if (this.fetchModelsButton != null && this.fetchModelsButton.isMouseOver(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }

            this.setModelDropdownOpen(false);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isMouseInsideModelDropdown(mouseX, mouseY)) {
            this.scrollModelDropdown(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
