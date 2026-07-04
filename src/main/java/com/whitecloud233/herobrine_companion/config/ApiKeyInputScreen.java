package com.whitecloud233.herobrine_companion.config;

import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.herobrine_companion.client.service.LLMModelDiscovery;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ApiKeyInputScreen extends Screen {
    private static final int MODEL_DROPDOWN_ROW_HEIGHT = 18;
    private static final int MODEL_DROPDOWN_MAX_ROWS = 6;
    private static final int MAX_CONTENT_WIDTH = 300;
    private static final int MIN_CONTENT_WIDTH = 220;
    private static final int MAX_CONTENT_HEIGHT = 330;
    private static final int SCREEN_MARGIN = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int CONTROL_GAP = 4;
    private static final int ACTION_GAP = 8;
    private static final int SCROLL_STEP = 18;

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
    private EditBox modelNameBox;
    private final Map<LLMConfig.Provider, ProviderDraft> providerDrafts = new EnumMap<>(LLMConfig.Provider.class);
    private List<String> discoveredModels = List.of();
    private int discoveredModelIndex = -1;
    private boolean modelDiscoveryInFlight = false;
    private Component modelDiscoveryStatus = Component.literal("");
    private int modelDiscoveryStatusColor = 0xAAAAAA;
    private String lastModelDiscoveryKey = "";
    private boolean modelDropdownOpen = false;
    private int modelDropdownScrollIndex = 0;
    private int scrollOffset = 0;
    private int modelDropdownX;
    private int modelDropdownY;
    private int modelDropdownWidth;

    public ApiKeyInputScreen(Screen lastScreen) {
        // 使用翻译键替换纯文本
        super(Component.translatable("gui.herobrine_companion.api_setup.title"));
        this.lastScreen = lastScreen;
        LLMConfig.ensureLoaded();
        this.selectedProvider = LLMConfig.getProvider();
    }

    @Override
    protected void init() {
        Layout layout = this.createLayout();
        this.modelDropdownX = layout.contentX();
        this.modelDropdownY = layout.modelDropdownY();
        this.modelDropdownWidth = layout.contentWidth();

        if (this.selectedProvider == null) {
            this.selectedProvider = LLMConfig.Provider.QINIU_CLOUD;
        }
        ProviderDraft selectedDraft = this.getDraft(this.selectedProvider);

        this.deepseekButton = this.addRenderableWidget(this.createProviderButton(layout.contentX(), layout.providerButtonY(), layout.providerButtonWidth(), LLMConfig.Provider.DEEPSEEK_OFFICIAL));
        this.openrouterButton = this.addRenderableWidget(this.createProviderButton(layout.contentX() + layout.providerButtonWidth() + CONTROL_GAP, layout.providerButtonY(), layout.providerButtonWidth(), LLMConfig.Provider.OPENROUTER));
        this.qiniuButton = this.addRenderableWidget(this.createProviderButton(layout.contentX() + (layout.providerButtonWidth() + CONTROL_GAP) * 2, layout.providerButtonY(), layout.providerButtonWidth(), LLMConfig.Provider.QINIU_CLOUD));
        this.customButton = this.addRenderableWidget(this.createProviderButton(layout.contentX() + (layout.providerButtonWidth() + CONTROL_GAP) * 3, layout.providerButtonY(), layout.providerButtonWidth(), LLMConfig.Provider.CUSTOM));

        // 创建 API Key 输入框，使用翻译键
        this.apiKeyBox = new MouseSelectableEditBox(this.font, layout.contentX(), layout.apiKeyBoxY(), layout.contentWidth(), BUTTON_HEIGHT, Component.translatable("gui.herobrine_companion.api_setup.api_key"));
        this.apiKeyBox.setMaxLength(256);
        this.apiKeyBox.setValue(this.isPlaceholderKey(selectedDraft.apiKey) ? "" : selectedDraft.apiKey);
        this.apiKeyBox.setResponder(value -> {
            this.getSelectedDraft().apiKey = value.trim();
            this.clearDiscoveredModels();
            this.updateSaveButtonState();
        });
        this.addRenderableWidget(this.apiKeyBox);

        this.providerIdBox = new MouseSelectableEditBox(this.font, layout.contentX(), layout.providerIdBoxY(), layout.contentWidth(), BUTTON_HEIGHT, Component.translatable("gui.herobrine_companion.api_setup.provider_id"));
        this.providerIdBox.setMaxLength(256);
        this.providerIdBox.setValue(selectedDraft.providerId);
        this.providerIdBox.setResponder(value -> {
            this.getSelectedDraft().providerId = value.trim();
            this.updateSaveButtonState();
        });
        this.addRenderableWidget(this.providerIdBox);

        this.endpointBox = new MouseSelectableEditBox(this.font, layout.contentX(), layout.endpointBoxY(), layout.contentWidth(), BUTTON_HEIGHT, Component.translatable("gui.herobrine_companion.api_setup.endpoint_input"));
        this.endpointBox.setMaxLength(512);
        this.endpointBox.setValue(selectedDraft.endpoint);
        this.endpointBox.setResponder(value -> {
            this.getSelectedDraft().endpoint = value.trim();
            this.clearDiscoveredModels();
            this.updateSaveButtonState();
        });
        this.addRenderableWidget(this.endpointBox);

        this.modelBox = new MouseSelectableEditBox(this.font, layout.contentX(), layout.modelBoxY(), layout.modelBoxWidth(), BUTTON_HEIGHT, Component.translatable("gui.herobrine_companion.api_setup.model_id"));
        this.modelBox.setMaxLength(256);
        this.modelBox.setValue(selectedDraft.modelId);
        this.modelBox.setResponder(value -> {
            ProviderDraft draft = this.getSelectedDraft();
            String previousModelId = draft.modelId == null ? "" : draft.modelId;
            draft.modelId = value.trim();
            this.syncDiscoveredModelIndex(draft.modelId);
            if (this.modelNameBox != null && (draft.modelName == null || draft.modelName.isBlank() || draft.modelName.equals(previousModelId))) {
                draft.modelName = draft.modelId;
                this.modelNameBox.setValue(draft.modelName);
            }
            this.updateSaveButtonState();
        });
        this.addRenderableWidget(this.modelBox);

        this.fetchModelsButton = this.addRenderableWidget(Button.builder(this.getFetchModelsButtonMessage(), (button) -> this.handleFetchModelsButton())
                .pos(layout.fetchButtonX(), layout.modelBoxY())
                .size(layout.fetchButtonWidth(), BUTTON_HEIGHT)
                .build());

        this.modelNameBox = new MouseSelectableEditBox(this.font, layout.contentX(), layout.modelNameBoxY(), layout.contentWidth(), BUTTON_HEIGHT, Component.translatable("gui.herobrine_companion.api_setup.model_name"));
        this.modelNameBox.setMaxLength(256);
        this.modelNameBox.setValue(selectedDraft.modelName);
        this.modelNameBox.setResponder(value -> {
            this.getSelectedDraft().modelName = value.trim();
            this.updateSaveButtonState();
        });
        this.addRenderableWidget(this.modelNameBox);

        this.refreshProviderButtons();
        this.refreshInputMode();

        // 保存按钮，使用翻译键
        this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.api_setup.confirm_save"), (button) -> {
            this.captureCurrentDraft();
            ProviderDraft draft = this.getSelectedDraft();
            LLMConfig.saveProviderSettings(this.selectedProvider, draft.providerId, draft.apiKey, draft.endpoint, draft.modelId, draft.modelName);
            this.minecraft.setScreen(this.lastScreen);
        }).pos(layout.saveButtonX(), layout.actionButtonY()).size(layout.actionButtonWidth(), BUTTON_HEIGHT).build());

        this.cancelButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.api_setup.cancel"), (button) ->
                        this.minecraft.setScreen(this.lastScreen))
                .pos(layout.cancelButtonX(), layout.actionButtonY())
                .size(layout.actionButtonWidth(), BUTTON_HEIGHT)
                .build());

        this.setInitialFocus(this.apiKeyBox);
        this.updateSaveButtonState();
        this.clampScrollOffset();
        this.applyLayoutToWidgets(this.createLayout());
    }

    private Layout createLayout() {
        return this.createLayout(this.scrollOffset);
    }

    private Layout createLayout(int scrollOffset) {
        int screenMargin = (this.width < 300 || this.height < 300) ? 6 : SCREEN_MARGIN;
        int availableWidth = Math.max(1, this.width - screenMargin * 2);
        int availableHeight = Math.max(1, this.height - screenMargin * 2);
        int contentWidth = clampToAvailable(MIN_CONTENT_WIDTH, MAX_CONTENT_WIDTH, availableWidth);
        int contentHeight = Math.min(MAX_CONTENT_HEIGHT, availableHeight);
        int contentX = (this.width - contentWidth) / 2;
        int viewportTop = Math.max(screenMargin, (this.height - contentHeight) / 2);
        int contentTop = viewportTop - scrollOffset;
        int centerX = contentX + contentWidth / 2;

        int titleY = contentTop;
        int promptY = titleY + 15;
        int providerPromptY = promptY + 13;
        int providerButtonY = providerPromptY + 12;
        int fieldGap = Math.max(24, Math.min(30, (contentHeight - 132) / 4));
        int apiKeyBoxY = providerButtonY + 40;
        int providerIdBoxY = apiKeyBoxY + fieldGap;
        int endpointBoxY = providerIdBoxY + fieldGap;
        int modelBoxY = endpointBoxY + fieldGap;
        int modelNameBoxY = modelBoxY + fieldGap - 2;
        int actionButtonY = modelNameBoxY + (fieldGap < 30 ? 24 : 32);
        int warningY = actionButtonY - 12;
        int infoY = actionButtonY + 28;

        int providerButtonWidth = Math.max(1, (contentWidth - CONTROL_GAP * 3) / 4);
        int fetchButtonWidth = Math.min(74, Math.max(60, contentWidth / 4));
        int modelBoxWidth = Math.max(1, contentWidth - fetchButtonWidth - CONTROL_GAP);
        int fetchButtonX = contentX + modelBoxWidth + CONTROL_GAP;
        int actionButtonWidth = Math.min(130, Math.max(1, (contentWidth - ACTION_GAP) / 2));
        int actionGroupWidth = actionButtonWidth * 2 + ACTION_GAP;
        int saveButtonX = contentX + (contentWidth - actionGroupWidth) / 2;
        int cancelButtonX = saveButtonX + actionButtonWidth + ACTION_GAP;

        return new Layout(contentX, contentTop, contentWidth, contentHeight, centerX,
                titleY, promptY, providerPromptY, providerButtonY, providerButtonWidth,
                apiKeyBoxY, providerIdBoxY, endpointBoxY, modelBoxY, modelBoxWidth,
                fetchButtonX, fetchButtonWidth, modelNameBoxY, actionButtonY, actionButtonWidth,
                saveButtonX, cancelButtonX, warningY, infoY, modelBoxY + BUTTON_HEIGHT + 2,
                viewportTop, contentHeight);
    }

    private static int clampToAvailable(int min, int max, int available) {
        int clamped = Math.min(max, available);
        return Math.max(Math.min(min, available), clamped);
    }

    private void clampScrollOffset() {
        int maxScroll = this.getMaxScroll();
        this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset));
    }

    private int getMaxScroll() {
        Layout layout = this.createLayout(0);
        int contentBottom = this.measureBottomInfoEndY(layout);
        int viewportBottom = layout.viewportTop() + layout.viewportHeight();
        return Math.max(0, contentBottom - viewportBottom + 6);
    }

    private int measureBottomInfoEndY(Layout layout) {
        int infoWidth = Math.max(1, Math.min(320, layout.contentWidth() + 40));
        int y = layout.infoY();
        if (!this.modelDiscoveryStatus.getString().isBlank()) {
            y = this.measureWrappedTextEndY(this.modelDiscoveryStatus, y, infoWidth) + 2;
        }
        y = this.measureWrappedTextEndY(this.getEndpointDisplayMessage(), y, infoWidth);
        y = this.measureWrappedTextEndY(this.getProviderHint(), y + 2, infoWidth);
        return this.measureWrappedTextEndY(Component.translatable("gui.herobrine_companion.api_setup.prompt_safe"), y + 4, infoWidth);
    }

    private int measureWrappedTextEndY(Component text, int startY, int maxWidth) {
        return startY + this.font.split(text, maxWidth).size() * 10;
    }

    private Component getEndpointDisplayMessage() {
        LLMConfig.Provider provider = this.selectedProvider == null ? LLMConfig.Provider.QINIU_CLOUD : this.selectedProvider;
        String endpoint = this.endpointBox == null || this.endpointBox.getValue().trim().isEmpty()
                ? provider.getEndpoint()
                : this.endpointBox.getValue().trim();
        return Component.translatable("gui.herobrine_companion.api_setup.endpoint", endpoint);
    }

    private void applyLayoutToWidgets(Layout layout) {
        if (this.deepseekButton != null) {
            this.deepseekButton.setX(layout.contentX());
            this.deepseekButton.setY(layout.providerButtonY());
        }
        if (this.openrouterButton != null) {
            this.openrouterButton.setX(layout.contentX() + layout.providerButtonWidth() + CONTROL_GAP);
            this.openrouterButton.setY(layout.providerButtonY());
        }
        if (this.qiniuButton != null) {
            this.qiniuButton.setX(layout.contentX() + (layout.providerButtonWidth() + CONTROL_GAP) * 2);
            this.qiniuButton.setY(layout.providerButtonY());
        }
        if (this.customButton != null) {
            this.customButton.setX(layout.contentX() + (layout.providerButtonWidth() + CONTROL_GAP) * 3);
            this.customButton.setY(layout.providerButtonY());
        }
        if (this.apiKeyBox != null) {
            this.apiKeyBox.setX(layout.contentX());
            this.apiKeyBox.setY(layout.apiKeyBoxY());
        }
        if (this.providerIdBox != null) {
            this.providerIdBox.setX(layout.contentX());
            this.providerIdBox.setY(layout.providerIdBoxY());
        }
        if (this.endpointBox != null) {
            this.endpointBox.setX(layout.contentX());
            this.endpointBox.setY(layout.endpointBoxY());
        }
        if (this.modelBox != null) {
            this.modelBox.setX(layout.contentX());
            this.modelBox.setY(layout.modelBoxY());
        }
        if (this.fetchModelsButton != null) {
            this.fetchModelsButton.setX(layout.fetchButtonX());
            this.fetchModelsButton.setY(layout.modelBoxY());
        }
        if (this.modelNameBox != null) {
            this.modelNameBox.setX(layout.contentX());
            this.modelNameBox.setY(layout.modelNameBoxY());
        }
        if (this.saveButton != null) {
            this.saveButton.setX(layout.saveButtonX());
            this.saveButton.setY(layout.actionButtonY());
        }
        if (this.cancelButton != null) {
            this.cancelButton.setX(layout.cancelButtonX());
            this.cancelButton.setY(layout.actionButtonY());
        }
        this.modelDropdownX = layout.contentX();
        this.modelDropdownY = layout.modelDropdownY();
        this.modelDropdownWidth = layout.contentWidth();
    }

    @Override
    public void tick() {
        super.tick();
    }

    private EditBox getFocusedTextBox() {
        if (this.apiKeyBox != null && this.apiKeyBox.isFocused()) {
            return this.apiKeyBox;
        }
        if (this.providerIdBox != null && this.providerIdBox.isFocused()) {
            return this.providerIdBox;
        }
        if (this.endpointBox != null && this.endpointBox.isFocused()) {
            return this.endpointBox;
        }
        if (this.modelBox != null && this.modelBox.isFocused()) {
            return this.modelBox;
        }
        if (this.modelNameBox != null && this.modelNameBox.isFocused()) {
            return this.modelNameBox;
        }
        return null;
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

        this.captureCurrentDraft();
        this.selectedProvider = provider;
        this.applyDraftToInputs(this.getSelectedDraft());

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
            this.providerIdBox.active = true;
        }
        // 所有提供商都允许编辑端点（用户可能需要使用代理或自定义端点）
        if (this.endpointBox != null) {
            this.endpointBox.setEditable(true);
            this.endpointBox.active = true;
        }
        if (this.modelNameBox != null) {
            this.modelNameBox.setEditable(true);
            this.modelNameBox.active = true;
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
            case QINIU_CLOUD_ANTHROPIC -> Component.translatable("gui.herobrine_companion.api_setup.provider.qiniu_anthropic");
            case CUSTOM -> Component.translatable("gui.herobrine_companion.api_setup.provider.custom");
        };
    }

    private Component getProviderHint() {
        return switch (this.selectedProvider) {
            case DEEPSEEK_OFFICIAL -> Component.translatable("gui.herobrine_companion.api_setup.provider_hint.deepseek", this.selectedProvider.getDefaultModel());
            case OPENROUTER -> Component.translatable("gui.herobrine_companion.api_setup.provider_hint.openrouter", this.selectedProvider.getDefaultModel());
            case QINIU_CLOUD -> Component.translatable("gui.herobrine_companion.api_setup.provider_hint.qiniu", this.selectedProvider.getDefaultModel());
            case QINIU_CLOUD_ANTHROPIC -> Component.translatable("gui.herobrine_companion.api_setup.provider_hint.qiniu_anthropic", this.selectedProvider.getDefaultModel());
            case CUSTOM -> Component.translatable("gui.herobrine_companion.api_setup.provider_hint.custom");
        };
    }

    private ProviderDraft getSelectedDraft() {
        return this.getDraft(this.selectedProvider == null ? LLMConfig.Provider.QINIU_CLOUD : this.selectedProvider);
    }

    private ProviderDraft getDraft(LLMConfig.Provider provider) {
        LLMConfig.Provider normalizedProvider = provider == null ? LLMConfig.Provider.QINIU_CLOUD : provider;
        return this.providerDrafts.computeIfAbsent(normalizedProvider, ignored -> {
            LLMConfig.ProviderSettings settings = LLMConfig.getProviderSettings(normalizedProvider);
            return new ProviderDraft(settings.providerId(), settings.apiKey(), settings.endpoint(), settings.modelId(), settings.modelName());
        });
    }

    private void captureCurrentDraft() {
        if (this.selectedProvider == null) {
            return;
        }
        ProviderDraft draft = this.getSelectedDraft();
        if (this.apiKeyBox != null) {
            draft.apiKey = this.apiKeyBox.getValue().trim();
        }
        if (this.providerIdBox != null) {
            draft.providerId = this.providerIdBox.getValue().trim();
        }
        if (this.endpointBox != null) {
            draft.endpoint = this.endpointBox.getValue().trim();
        }
        if (this.modelBox != null) {
            draft.modelId = this.modelBox.getValue().trim();
        }
        if (this.modelNameBox != null) {
            draft.modelName = this.modelNameBox.getValue().trim();
        }
    }

    private void applyDraftToInputs(ProviderDraft draft) {
        if (draft == null) {
            return;
        }
        if (this.apiKeyBox != null) {
            this.apiKeyBox.setValue(this.isPlaceholderKey(draft.apiKey) ? "" : draft.apiKey);
        }
        if (this.providerIdBox != null) {
            this.providerIdBox.setValue(draft.providerId);
        }
        if (this.endpointBox != null) {
            this.endpointBox.setValue(draft.endpoint);
        }
        if (this.modelBox != null) {
            this.modelBox.setValue(draft.modelId);
        }
        if (this.modelNameBox != null) {
            this.modelNameBox.setValue(draft.modelName == null || draft.modelName.isBlank() ? draft.modelId : draft.modelName);
        }
    }

    private boolean isPlaceholderKey(String apiKey) {
        return apiKey == null || apiKey.isBlank() || apiKey.equals(LLMConfig.getDefaultApiKeyPlaceholder());
    }

    private void updateSaveButtonState() {
        if (this.saveButton != null) {
            boolean hasRequiredCoreFields = !this.apiKeyBox.getValue().trim().isEmpty()
                    && !this.endpointBox.getValue().trim().isEmpty()
                    && !this.modelBox.getValue().trim().isEmpty()
                    && !this.providerIdBox.getValue().trim().isEmpty();
            this.saveButton.active = hasRequiredCoreFields;
            this.saveButton.visible = !this.modelDropdownOpen;
        }
        if (this.cancelButton != null) {
            this.cancelButton.active = !this.modelDropdownOpen;
            this.cancelButton.visible = !this.modelDropdownOpen;
        }
        this.updateFetchModelsButtonState();
    }

    private void syncModelNameWithIdIfNeeded() {
        if (this.modelBox == null || this.modelNameBox == null) {
            return;
        }
        String modelId = this.modelBox.getValue().trim();
        String modelName = this.modelNameBox.getValue().trim();
        if (modelName.isEmpty()) {
            this.modelNameBox.setValue(modelId);
            this.getSelectedDraft().modelName = modelId;
        }
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
        this.getSelectedDraft().modelId = model;
        this.syncModelNameWithIdIfNeeded();
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

    private static final class ProviderDraft {
        private String providerId;
        private String apiKey;
        private String endpoint;
        private String modelId;
        private String modelName;

        private ProviderDraft(String providerId, String apiKey, String endpoint, String modelId, String modelName) {
            this.providerId = providerId == null ? "" : providerId;
            this.apiKey = apiKey == null ? "" : apiKey;
            this.endpoint = endpoint == null ? "" : endpoint;
            this.modelId = modelId == null ? "" : modelId;
            this.modelName = modelName == null ? "" : modelName;
        }
    }

    private static final class MouseSelectableEditBox extends EditBox {
        private Consumer<String> responder = ignored -> {};
        private boolean suppressResponder;
        private boolean draggingSelection;
        private int selectionAnchor;

        private MouseSelectableEditBox(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
            super.setResponder(this::handleValueChanged);
        }

        @Override
        public void setResponder(Consumer<String> responder) {
            this.responder = responder == null ? ignored -> {} : responder;
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
        this.clampScrollOffset();
        Layout layout = this.createLayout();
        this.applyLayoutToWidgets(layout);
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 渲染提示文字，使用翻译键
        guiGraphics.drawCenteredString(this.font, this.title, layout.centerX(), layout.titleY(), 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.api_setup.prompt_input"), layout.centerX(), layout.promptY(), 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.api_setup.prompt_provider"), layout.centerX(), layout.providerPromptY(), 0xAAAAAA);

        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.api_setup.api_key"), layout.contentX(), layout.apiKeyBoxY() - 12, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.api_setup.provider_id"), layout.contentX(), layout.providerIdBoxY() - 12, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.api_setup.endpoint_input"), layout.contentX(), layout.endpointBoxY() - 12, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.api_setup.model_id"), layout.contentX(), layout.modelBoxY() - 12, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.api_setup.model_name"), layout.contentX(), layout.modelNameBoxY() - 12, 0xFFFFFF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (this.modelDropdownOpen) {
            this.renderModelDropdown(guiGraphics, mouseX, mouseY);
            return;
        }

        int textY = layout.infoY();
        int infoWidth = Math.max(1, Math.min(320, layout.contentWidth() + 40));
        if (!this.modelDiscoveryStatus.getString().isBlank()) {
            textY = this.drawWrappedCenteredText(guiGraphics, this.modelDiscoveryStatus, layout.centerX(), textY, infoWidth, this.modelDiscoveryStatusColor) + 2;
        } else if (!this.saveButton.active) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.api_setup.warning_required"), layout.centerX(), layout.warningY(), 0xFF8080);
        }

        textY = this.drawWrappedCenteredText(guiGraphics,
                this.getEndpointDisplayMessage(),
                layout.centerX(), textY, infoWidth, 0x9FD2FF);
        textY = this.drawWrappedCenteredText(guiGraphics, this.getProviderHint(), layout.centerX(), textY + 2, infoWidth, 0xCFCFCF);
        this.drawWrappedCenteredText(guiGraphics,
                Component.translatable("gui.herobrine_companion.api_setup.prompt_safe"),
                layout.centerX(), textY + 4, infoWidth, 0xAAAAAA);

        this.renderModelDropdown(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        EditBox focusedTextBox = this.getFocusedTextBox();
        if (focusedTextBox != null && focusedTextBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        EditBox focusedTextBox = this.getFocusedTextBox();
        if (focusedTextBox != null && focusedTextBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        EditBox focusedTextBox = this.getFocusedTextBox();
        if (focusedTextBox != null && focusedTextBox.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        EditBox focusedTextBox = this.getFocusedTextBox();
        if (focusedTextBox != null && focusedTextBox.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
            return super.mouseClicked(mouseX, mouseY, button);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isMouseInsideModelDropdown(mouseX, mouseY)) {
            this.scrollModelDropdown(scrollY > 0 ? -1 : 1);
            return true;
        }
        int maxScroll = this.getMaxScroll();
        if (maxScroll > 0) {
            this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset - (int) (scrollY * SCROLL_STEP)));
            this.applyLayoutToWidgets(this.createLayout());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private record Layout(
            int contentX,
            int contentTop,
            int contentWidth,
            int contentHeight,
            int centerX,
            int titleY,
            int promptY,
            int providerPromptY,
            int providerButtonY,
            int providerButtonWidth,
            int apiKeyBoxY,
            int providerIdBoxY,
            int endpointBoxY,
            int modelBoxY,
            int modelBoxWidth,
            int fetchButtonX,
            int fetchButtonWidth,
            int modelNameBoxY,
            int actionButtonY,
            int actionButtonWidth,
            int saveButtonX,
            int cancelButtonX,
            int warningY,
            int infoY,
            int modelDropdownY,
            int viewportTop,
            int viewportHeight
    ) {
    }
}
