package com.whitecloud233.herobrine_companion.config;

import com.whitecloud233.herobrine_companion.client.gui.HeroTheme;
import com.whitecloud233.herobrine_companion.client.gui.HeroScrollbar;
import com.whitecloud233.herobrine_companion.client.gui.HeroPanelScreen;
import com.whitecloud233.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.herobrine_companion.client.event.ClientHooks;
import com.whitecloud233.herobrine_companion.client.llm.LlmSettings;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.herobrine_companion.client.service.LLMContextWindow;
import com.whitecloud233.herobrine_companion.client.service.LLMModelDiscovery;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ApiKeyInputScreen extends HeroPanelScreen {
    private static final int MODEL_DROPDOWN_ROW_HEIGHT = 18;
    private static final int MODEL_DROPDOWN_MAX_ROWS = 6;
    private static final int FORMAT_DROPDOWN_ROW_HEIGHT = 18;
    private static final List<String> FORMAT_OPTIONS = List.of("", "OPENAI_CHAT", "OPENAI_RESPONSES", "ANTHROPIC", "GEMINI");
    /**
     * 面板最大内容宽度。
     *
     * <p>字段名改为左侧同排后排布后，输入框需要拿到足够宽度（原先 300 宽全给框，
     * 现在要切出约 80px 给标签），因此上限相应放大，避免输入框被压窄。</p>
     */
    private static final int MAX_CONTENT_WIDTH = 400;
    private static final int MIN_CONTENT_WIDTH = 240;
    /**
     * 面板内容区最大高度。
     *
     * <p>每个字段现在是「标签 + 输入框」两段（标签贴在框上方 11px），
     * 五个字段 + 服务商行 + 操作行的自然高度比旧版高约 40px；
     * 这里相应放大上限，让 1080p / 900p 窗口一屏放得下，不必靠滚动看保存按钮。</p>
     */
    private static final int MAX_CONTENT_HEIGHT = 396;
    private static final int SCREEN_MARGIN = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int CONTROL_GAP = 4;
    private static final int ACTION_GAP = 8;
    private static final int SCROLL_STEP = 18;
    private static final int SCROLLBAR_WIDTH = 4;

    private final Screen lastScreen;
    private LLMConfig.Provider selectedProvider;
    private Button deepseekButton;
    private Button openrouterButton;
    private Button qiniuButton;
    private Button geminiButton;
    private Button customButton;
    private Button saveButton;
    private Button routingButton;
    private Button cancelButton;
    private Button fetchModelsButton;
    private Button localPageButton;
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
    private Button formatButton;
    private boolean formatDropdownOpen = false;
    private int formatDropdownX;
    private int formatDropdownY;
    private int formatDropdownWidth;
    private int scrollOffset = 0;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffsetY;
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
        this.draggingScrollbar = false;
        Layout layout = this.createLayout();
        this.modelDropdownX = layout.fieldX();
        this.modelDropdownY = layout.modelDropdownY();
        this.modelDropdownWidth = layout.modelBoxWidth();

        if (this.selectedProvider == null) {
            this.selectedProvider = LLMConfig.Provider.QINIU_CLOUD;
        }
        ProviderDraft selectedDraft = this.getDraft(this.selectedProvider);

        this.deepseekButton = this.addRenderableWidget(this.createProviderButton(layout.contentX(), layout.providerButtonY(), layout.providerButtonWidth(), LLMConfig.Provider.DEEPSEEK_OFFICIAL));
        this.openrouterButton = this.addRenderableWidget(this.createProviderButton(layout.contentX() + layout.providerButtonWidth() + CONTROL_GAP, layout.providerButtonY(), layout.providerButtonWidth(), LLMConfig.Provider.OPENROUTER));
        this.qiniuButton = this.addRenderableWidget(this.createProviderButton(layout.contentX() + (layout.providerButtonWidth() + CONTROL_GAP) * 2, layout.providerButtonY(), layout.providerButtonWidth(), LLMConfig.Provider.QINIU_CLOUD));
        this.geminiButton = this.addRenderableWidget(this.createProviderButton(layout.contentX() + (layout.providerButtonWidth() + CONTROL_GAP) * 3, layout.providerButtonY(), layout.providerButtonWidth(), LLMConfig.Provider.GEMINI));
        this.customButton = this.addRenderableWidget(this.createProviderButton(layout.contentX() + (layout.providerButtonWidth() + CONTROL_GAP) * 4, layout.providerButtonY(), layout.providerButtonWidth(), LLMConfig.Provider.CUSTOM));

        this.localPageButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentX(), layout.localButtonY(), layout.contentWidth(), BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.api_setup.local_page_button"),
                (button) -> this.minecraft.setScreen(new LocalModelScreen(this)),
                null));

        // 创建 API Key 输入框，使用翻译键
        this.apiKeyBox = new ThemedEditBox(this.font, layout.fieldX(), layout.apiKeyBoxY(), layout.fieldWidth(), BUTTON_HEIGHT, Component.translatable("gui.herobrine_companion.api_setup.api_key"));
        this.apiKeyBox.setMaxLength(256);
        this.apiKeyBox.setValue(this.isPlaceholderKey(selectedDraft.apiKey) ? "" : selectedDraft.apiKey);
        this.apiKeyBox.setResponder(value -> {
            this.getSelectedDraft().apiKey = value.trim();
            this.clearDiscoveredModels();
            this.updateSaveButtonState();
        });
        this.addRenderableWidget(this.apiKeyBox);

        this.providerIdBox = new ThemedEditBox(this.font, layout.fieldX(), layout.providerIdBoxY(), layout.fieldWidth(), BUTTON_HEIGHT, Component.translatable("gui.herobrine_companion.api_setup.provider_id"));
        this.providerIdBox.setMaxLength(256);
        this.providerIdBox.setValue(selectedDraft.providerId);
        this.providerIdBox.setResponder(value -> {
            this.getSelectedDraft().providerId = value.trim();
            this.updateSaveButtonState();
        });
        this.addRenderableWidget(this.providerIdBox);

        this.endpointBox = new ThemedEditBox(this.font, layout.fieldX(), layout.endpointBoxY(), layout.fieldWidth(), BUTTON_HEIGHT, Component.translatable("gui.herobrine_companion.api_setup.endpoint_input"));
        this.endpointBox.setMaxLength(512);
        this.endpointBox.setValue(selectedDraft.endpoint);
        this.endpointBox.setResponder(value -> {
            this.getSelectedDraft().endpoint = value.trim();
            this.clearDiscoveredModels();
            this.updateSaveButtonState();
        });
        this.addRenderableWidget(this.endpointBox);

        this.modelBox = new ThemedEditBox(this.font, layout.fieldX(), layout.modelBoxY(), layout.modelBoxWidth(), BUTTON_HEIGHT, Component.translatable("gui.herobrine_companion.api_setup.model_id"));
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

        this.fetchModelsButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.fetchButtonX(), layout.modelBoxY(), layout.fetchButtonWidth(), BUTTON_HEIGHT,
                this.getFetchModelsButtonMessage(),
                (button) -> this.handleFetchModelsButton(),
                null));

        this.modelNameBox = new ThemedEditBox(this.font, layout.fieldX(), layout.modelNameBoxY(), layout.fieldWidth(), BUTTON_HEIGHT, Component.translatable("gui.herobrine_companion.api_setup.model_name"));
        this.modelNameBox.setMaxLength(256);
        this.modelNameBox.setValue(selectedDraft.modelName);
        this.modelNameBox.setResponder(value -> {
            this.getSelectedDraft().modelName = value.trim();
            this.updateSaveButtonState();
        });
        this.addRenderableWidget(this.modelNameBox);

        this.formatButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.fieldX(), layout.formatBoxY(), layout.fieldWidth(), BUTTON_HEIGHT,
                this.getFormatButtonMessage(this.getCustomApiFormatDraft()),
                button -> {
                    this.formatDropdownOpen = !this.formatDropdownOpen;
                    this.updateSaveButtonState();
                },
                null));

        this.refreshProviderButtons();
        this.refreshInputMode();

        // 保存按钮，使用翻译键
        this.saveButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.saveButtonX(), layout.actionButtonY(), layout.actionButtonWidth(), BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.api_setup.confirm_save"),
                (button) -> {
                    this.captureCurrentDraft();
                    ProviderDraft draft = this.getSelectedDraft();
                    if (this.selectedProvider == LLMConfig.Provider.CUSTOM) {
                        LLMConfig.customApiFormat = draft.apiFormat;
                    }
                    LLMConfig.saveProviderSettings(this.selectedProvider, draft.providerId, draft.apiKey, draft.endpoint, draft.modelId, draft.modelName);
                    if (LLMConfig.isLocalRouteActive()) {
                        LLMConfig.clearLocalModel();
                    } else {
                        LLMConfig.clearLocalChatRoutes();
                        LLMConfig.save();
                    }
                    ClientHooks.setApiEnabled(true);
                    this.minecraft.setScreen(this.lastScreen);
                },
                null));

        this.routingButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.routingButtonX(), layout.actionButtonY(), layout.actionButtonWidth(), BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.api_setup.routing_button"),
                (button) -> this.minecraft.setScreen(new LLMRoutingScreen(this)),
                null));

        this.cancelButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.cancelButtonX(), layout.actionButtonY(), layout.actionButtonWidth(), BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.api_setup.cancel"),
                (button) -> this.minecraft.setScreen(this.lastScreen),
                null));

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
        int contentWidth = HeroTheme.clampToAvailable(MIN_CONTENT_WIDTH, MAX_CONTENT_WIDTH, availableWidth);
        int contentHeight = Math.min(MAX_CONTENT_HEIGHT, availableHeight);
        int contentX = (this.width - contentWidth) / 2;
        int viewportTop = Math.max(screenMargin, (this.height - contentHeight) / 2);
        int contentTop = viewportTop - scrollOffset;
        int centerX = contentX + contentWidth / 2;

        int titleY = contentTop;
        int promptY = titleY + 15;
        int localButtonY = promptY + 16;
        int providerPromptY = localButtonY + BUTTON_HEIGHT + 4;
        int providerButtonY = providerPromptY + 12;

        // 字段名改到输入框左侧（同一行），不再在框上方再占一行——
        // 这是"字太挤"的根因：标签+框两段堆叠把行距压到 28px，中文标签紧贴上一行的框。
        // 现在每行只有 20px 的输入框，行距给到 30px，视觉上留出清晰的空隙。
        int labelWidth = this.computeLabelWidth(contentWidth);
        int fieldX = contentX + labelWidth;
        int fieldWidth = Math.max(60, contentWidth - labelWidth);
        int fieldGap = Math.max(30, Math.min(36, (contentHeight - 150) / 5));

        int apiKeyBoxY = providerButtonY + 44;
        int providerIdBoxY = apiKeyBoxY + fieldGap;
        int endpointBoxY = providerIdBoxY + fieldGap;
        int modelBoxY = endpointBoxY + fieldGap;
        int modelNameBoxY = modelBoxY + fieldGap;
        int formatBoxY = modelNameBoxY + fieldGap;
        int actionButtonY = formatBoxY + (fieldGap < 34 ? 28 : 36);
        int warningY = actionButtonY - 12;
        int infoY = actionButtonY + 28;

        int providerButtonWidth = Math.max(1, (contentWidth - CONTROL_GAP * 4) / 5);
        int fetchButtonWidth = Math.min(74, Math.max(60, contentWidth / 4));
        int modelBoxWidth = Math.max(1, fieldWidth - fetchButtonWidth - CONTROL_GAP);
        int fetchButtonX = fieldX + modelBoxWidth + CONTROL_GAP;
        int actionButtonWidth = Math.min(130, Math.max(1, (contentWidth - ACTION_GAP * 2) / 3));
        int actionGroupWidth = actionButtonWidth * 3 + ACTION_GAP * 2;
        int saveButtonX = contentX + (contentWidth - actionGroupWidth) / 2;
        int routingButtonX = saveButtonX + actionButtonWidth + ACTION_GAP;
        int cancelButtonX = routingButtonX + actionButtonWidth + ACTION_GAP;

        return new Layout(contentX, contentTop, contentWidth, contentHeight, centerX,
                titleY, promptY, localButtonY, providerPromptY, providerButtonY, providerButtonWidth,
                apiKeyBoxY, providerIdBoxY, endpointBoxY, modelBoxY, modelBoxWidth,
                fetchButtonX, fetchButtonWidth, modelNameBoxY, formatBoxY, actionButtonY, actionButtonWidth,
                saveButtonX, routingButtonX, cancelButtonX, warningY, infoY, modelBoxY + BUTTON_HEIGHT + 2,
                viewportTop, contentHeight, fieldX, fieldWidth, labelWidth);
    }

    /**
     * 左侧字段名列宽：取本页所有字段名里最宽的那个，再留一点空白。
     *
     * <p>上限限制在内容宽的 42%，否则窗口一窄，输入框会被标签挤得没法输入。</p>
     */
    private int computeLabelWidth(int contentWidth) {
        int widest = 0;
        for (String key : new String[]{
                "gui.herobrine_companion.api_setup.api_key",
                "gui.herobrine_companion.api_setup.provider_id",
                "gui.herobrine_companion.api_setup.endpoint_input",
                "gui.herobrine_companion.api_setup.model_id",
                "gui.herobrine_companion.api_setup.model_name",
                "gui.herobrine_companion.api_setup.format"}) {
            widest = Math.max(widest, this.font.width(Component.translatable(key).getString()));
        }
        return Math.max(48, Math.min(widest + 10, contentWidth * 42 / 100));
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

    /** 滚动条几何统一由 {@link HeroScrollbar} 计算（换算公式全项目只有一份）。 */
    private HeroScrollbar getScrollbarMetrics(Layout layout) {
        int trackX = Math.min(layout.contentX() + layout.contentWidth() + 3, this.width - SCROLLBAR_WIDTH - 2);
        return HeroScrollbar.of(trackX, SCROLLBAR_WIDTH, layout.viewportTop(), layout.viewportHeight(),
                this.getMaxScroll(), this.scrollOffset);
    }

    private void renderScrollbar(GuiGraphics guiGraphics, Layout layout, int mouseX, int mouseY) {
        HeroScrollbar scrollbar = this.getScrollbarMetrics(layout);
        scrollbar.render(guiGraphics, this.draggingScrollbar, scrollbar.isOverThumb(mouseX, mouseY));
    }

    private boolean isMouseOverScrollbar(double mouseX, double mouseY, HeroScrollbar scrollbar) {
        return scrollbar.isOverTrack(mouseX, mouseY);
    }

    private void scrollToScrollbarPosition(double thumbTop, HeroScrollbar scrollbar) {
        this.scrollOffset = scrollbar.scrollOffsetForThumbTop(thumbTop);
        this.applyLayoutToWidgets(this.createLayout());
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
        if (this.geminiButton != null) {
            this.geminiButton.setX(layout.contentX() + (layout.providerButtonWidth() + CONTROL_GAP) * 3);
            this.geminiButton.setY(layout.providerButtonY());
        }
        if (this.customButton != null) {
            this.customButton.setX(layout.contentX() + (layout.providerButtonWidth() + CONTROL_GAP) * 4);
            this.customButton.setY(layout.providerButtonY());
        }
        if (this.localPageButton != null) {
            this.localPageButton.setX(layout.contentX());
            this.localPageButton.setY(layout.localButtonY());
        }
        if (this.apiKeyBox != null) {
            this.apiKeyBox.setX(layout.fieldX());
            this.apiKeyBox.setY(layout.apiKeyBoxY());
        }
        if (this.providerIdBox != null) {
            this.providerIdBox.setX(layout.fieldX());
            this.providerIdBox.setY(layout.providerIdBoxY());
        }
        if (this.endpointBox != null) {
            this.endpointBox.setX(layout.fieldX());
            this.endpointBox.setY(layout.endpointBoxY());
        }
        if (this.modelBox != null) {
            this.modelBox.setX(layout.fieldX());
            this.modelBox.setY(layout.modelBoxY());
        }
        if (this.fetchModelsButton != null) {
            this.fetchModelsButton.setX(layout.fetchButtonX());
            this.fetchModelsButton.setY(layout.modelBoxY());
        }
        if (this.modelNameBox != null) {
            this.modelNameBox.setX(layout.fieldX());
            this.modelNameBox.setY(layout.modelNameBoxY());
        }
        if (this.formatButton != null) {
            this.formatButton.setX(layout.fieldX());
            this.formatButton.setY(layout.formatBoxY());
        }
        this.formatDropdownX = layout.fieldX();
        this.formatDropdownY = layout.formatBoxY() + BUTTON_HEIGHT + 2;
        this.formatDropdownWidth = layout.fieldWidth();
        if (this.saveButton != null) {
            this.saveButton.setX(layout.saveButtonX());
            this.saveButton.setY(layout.actionButtonY());
        }
        if (this.routingButton != null) {
            this.routingButton.setX(layout.routingButtonX());
            this.routingButton.setY(layout.actionButtonY());
        }
        if (this.cancelButton != null) {
            this.cancelButton.setX(layout.cancelButtonX());
            this.cancelButton.setY(layout.actionButtonY());
        }
        this.modelDropdownX = layout.fieldX();
        this.modelDropdownY = layout.modelDropdownY();
        this.modelDropdownWidth = layout.modelBoxWidth();
    }

    @Override
    public void tick() {
        // 1.21.1 的 EditBox 已去掉 tick()（光标闪烁改用 Util.getMillis），无需逐框驱动。
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
        return new HeroScreen.ThemedButton(x, y, width, BUTTON_HEIGHT,
                this.getProviderButtonMessage(provider),
                (button) -> this.selectProvider(provider),
                null);
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
        this.updateProviderButton(this.geminiButton, LLMConfig.Provider.GEMINI);
        this.updateProviderButton(this.customButton, LLMConfig.Provider.CUSTOM);
    }

    private void refreshInputMode() {
        boolean customSelected = this.isCustomProviderSelected();
        if (this.providerIdBox != null) {
            this.providerIdBox.setEditable(customSelected);
            this.providerIdBox.active = true;
        }
        if (this.endpointBox != null) {
            this.endpointBox.setEditable(true);
            this.endpointBox.active = true;
        }
        if (this.modelNameBox != null) {
            this.modelNameBox.setEditable(true);
            this.modelNameBox.active = true;
        }
        if (this.formatButton != null) {
            this.formatButton.visible = customSelected;
            this.formatButton.active = customSelected;
            this.formatButton.setMessage(this.getFormatButtonMessage(this.getCustomApiFormatDraft()));
        }
        if (!customSelected) {
            this.formatDropdownOpen = false;
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
            case GEMINI -> Component.translatable("gui.herobrine_companion.api_setup.provider.gemini");
            case CUSTOM -> Component.translatable("gui.herobrine_companion.api_setup.provider.custom");
        };
    }

    private Component getProviderHint() {
        return switch (this.selectedProvider) {
            case DEEPSEEK_OFFICIAL -> Component.translatable("gui.herobrine_companion.api_setup.provider_hint.deepseek", this.selectedProvider.getDefaultModel());
            case OPENROUTER -> Component.translatable("gui.herobrine_companion.api_setup.provider_hint.openrouter", this.selectedProvider.getDefaultModel());
            case QINIU_CLOUD -> Component.translatable("gui.herobrine_companion.api_setup.provider_hint.qiniu", this.selectedProvider.getDefaultModel());
            case QINIU_CLOUD_ANTHROPIC -> Component.translatable("gui.herobrine_companion.api_setup.provider_hint.qiniu_anthropic", this.selectedProvider.getDefaultModel());
            case GEMINI -> Component.translatable("gui.herobrine_companion.api_setup.provider_hint.gemini", this.selectedProvider.getDefaultModel());
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
            return new ProviderDraft(settings.providerId(), settings.apiKey(), settings.endpoint(), settings.modelId(), settings.modelName(),
                    normalizedProvider == LLMConfig.Provider.CUSTOM ? LLMConfig.customApiFormat : "");
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

    /**
     * 浮动层打开时，把会被它盖住的输入框直接隐藏。
     *
     * <p><b>为什么必须隐藏而不是靠绘制顺序</b>：{@code EditBox} 把文字排进
     * {@code Font} 的延迟批次，而 {@code GuiGraphics.fill()} 走的是另一条 {@code gui()} 批次。
     * 两条队列的提交顺序并不由调用顺序严格决定，实测即使先 {@code flush()} 再铺底，
     * 下层输入框的文字仍会浮到浮层之上（"下拉列表被模型名称穿透"）。</p>
     *
     * <p>隐藏控件是唯一完全确定的做法：没有排队的文字，也就不可能穿透。
     * 浮层关闭后由本方法恢复。本页原本就已用同一思路处理保存/取消键（见
     * {@code updateSaveButtonState} 里的 {@code visible = !anyDropdownOpen}）。</p>
     */
    private void syncOverlayOcclusion() {
        boolean overlayOpen = this.modelDropdownOpen || (this.formatDropdownOpen && this.isCustomProviderSelected());
        int overlayTop = this.modelDropdownOpen ? this.modelDropdownY : this.formatDropdownY;
        int overlayBottom = overlayTop + (this.modelDropdownOpen
                ? this.getModelDropdownHeight()
                : FORMAT_OPTIONS.size() * FORMAT_DROPDOWN_ROW_HEIGHT);

        // 模型浮层从"模型标识"框下方开始铺，所以它会盖住模型名称框与格式框；
        // 这里按"控件与浮层矩形是否相交"逐个判定，而不是写死是哪几个框——
        // 布局随窗口变化时判断依然成立。
        this.setOccluded(this.modelNameBox, overlayOpen, overlayTop, overlayBottom);
        this.setOccluded(this.formatButton, overlayOpen, overlayTop, overlayBottom);
    }

    /** 控件与浮层矩形相交就隐藏，否则恢复显示。 */
    private void setOccluded(AbstractWidget widget, boolean overlayOpen, int overlayTop, int overlayBottom) {
        if (widget == null) {
            return;
        }
        boolean intersects = overlayOpen
                && widget.getY() < overlayBottom
                && widget.getY() + widget.getHeight() > overlayTop;
        widget.visible = !intersects;
    }

    private void updateSaveButtonState() {
        boolean anyDropdownOpen = this.modelDropdownOpen || this.formatDropdownOpen;
        if (this.saveButton != null) {
            boolean hasRequiredCoreFields = !this.apiKeyBox.getValue().trim().isEmpty()
                    && !this.endpointBox.getValue().trim().isEmpty()
                    && !this.modelBox.getValue().trim().isEmpty()
                    && !this.providerIdBox.getValue().trim().isEmpty();
            this.saveButton.active = hasRequiredCoreFields;
            this.saveButton.visible = !anyDropdownOpen;
        }
        if (this.cancelButton != null) {
            this.cancelButton.active = !anyDropdownOpen;
            this.cancelButton.visible = !anyDropdownOpen;
        }
        if (this.routingButton != null) {
            this.routingButton.active = !anyDropdownOpen;
            this.routingButton.visible = !anyDropdownOpen;
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
        LLMConfig.EndpointFormat endpointFormat = provider == LLMConfig.Provider.CUSTOM && LLMConfig.getCustomApiFormat() != null
                ? LLMConfig.getCustomApiFormat()
                : LLMConfig.detectEndpointFormat(endpoint);
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

    /**
     * 拉取模型列表成功后，用当前输入的 endpoint/key/model 探测真实上下文窗口。
     * 结果按 endpoint+model 缓存并落盘；重复选择同一模型不会再发请求。
     */
    private void probeContextWindowAsync() {
        String endpoint = this.getCurrentEndpointInput();
        String apiKey = this.apiKeyBox.getValue().trim();
        String model = this.modelBox.getValue().trim();
        if (endpoint.isEmpty() || model.isEmpty() || apiKey.isEmpty()
                || apiKey.equals(LLMConfig.getDefaultApiKeyPlaceholder())) {
            return;
        }
        LLMConfig.Provider provider = this.selectedProvider == null ? LLMConfig.Provider.QINIU_CLOUD : this.selectedProvider;
        LLMConfig.EndpointFormat endpointFormat = provider == LLMConfig.Provider.CUSTOM && LLMConfig.getCustomApiFormat() != null
                ? LLMConfig.getCustomApiFormat()
                : LLMConfig.detectEndpointFormat(endpoint);
        LLMContextWindow.probeIfStale(LlmSettings.of(endpoint, apiKey, model, provider, endpointFormat));
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
            // 端点确实通了：顺手把这个模型的真实上下文窗口探测下来（缓存 + 落盘）。
            this.probeContextWindowAsync();
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
        // 1.20.1 的 GuiGraphics 会把文字排进 Font 批次延迟提交，单纯 fill() 只是"排队靠后"，
        // 并不会盖住下方已经排队的"模型名称"标签与输入框文字——那正是下拉列表出现
        // 文字叠影的原因。这里先 flush 把旧批次落到帧缓冲，再画不透明浮层底。
        HeroTheme.overlay(guiGraphics, this.modelDropdownX, this.modelDropdownY,
                this.modelDropdownWidth, dropdownHeight);

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
            // 选中/悬停底色画在行内 1px 边框之内，避免盖掉浮层外框。
            HeroTheme.overlayRow(guiGraphics, this.modelDropdownX + 1, rowY,
                    this.modelDropdownWidth - 2, MODEL_DROPDOWN_ROW_HEIGHT, selected, hovered);

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

    private String getCustomApiFormatDraft() {
        ProviderDraft draft = this.getSelectedDraft();
        return draft == null ? "" : draft.apiFormat;
    }

    private void setCustomApiFormatDraft(String format) {
        this.getSelectedDraft().apiFormat = format == null ? "" : format;
    }

    private Component getFormatButtonMessage(String format) {
        return Component.translatable("gui.herobrine_companion.api_setup.format." + formatDisplaySuffix(format));
    }

    private static String formatDisplaySuffix(String format) {
        if (format == null || format.isBlank()) {
            return "auto";
        }
        return switch (format) {
            case "OPENAI_CHAT" -> "openai_chat";
            case "OPENAI_RESPONSES" -> "openai_responses";
            case "ANTHROPIC" -> "anthropic";
            case "GEMINI" -> "gemini";
            default -> "auto";
        };
    }

    private boolean isMouseInsideFormatDropdown(double mouseX, double mouseY) {
        return this.formatDropdownOpen
                && mouseX >= this.formatDropdownX
                && mouseX < this.formatDropdownX + this.formatDropdownWidth
                && mouseY >= this.formatDropdownY
                && mouseY < this.formatDropdownY + FORMAT_OPTIONS.size() * FORMAT_DROPDOWN_ROW_HEIGHT;
    }

    private void renderFormatDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.formatDropdownOpen) {
            return;
        }
        int dropdownHeight = FORMAT_OPTIONS.size() * FORMAT_DROPDOWN_ROW_HEIGHT;
        // 同模型列表：先提交旧批次，浮层才能真的盖住下面的标签与输入框文字。
        HeroTheme.overlay(guiGraphics, this.formatDropdownX, this.formatDropdownY,
                this.formatDropdownWidth, dropdownHeight);

        for (int row = 0; row < FORMAT_OPTIONS.size(); row++) {
            String format = FORMAT_OPTIONS.get(row);
            int rowY = this.formatDropdownY + row * FORMAT_DROPDOWN_ROW_HEIGHT;
            boolean selected = format.equals(this.getCustomApiFormatDraft());
            boolean hovered = mouseX >= this.formatDropdownX
                    && mouseX < this.formatDropdownX + this.formatDropdownWidth
                    && mouseY >= rowY
                    && mouseY < rowY + FORMAT_DROPDOWN_ROW_HEIGHT;
            HeroTheme.overlayRow(guiGraphics, this.formatDropdownX + 1, rowY,
                    this.formatDropdownWidth - 2, FORMAT_DROPDOWN_ROW_HEIGHT, selected, hovered);
            guiGraphics.drawString(this.font, this.getFormatButtonMessage(format),
                    this.formatDropdownX + 5, rowY + 4, selected ? 0xB8FFB8 : 0xE6E6E6, false);
        }
    }

    private static final class ProviderDraft {
        private String providerId;
        private String apiKey;
        private String endpoint;
        private String modelId;
        private String modelName;
        private String apiFormat;

        private ProviderDraft(String providerId, String apiKey, String endpoint, String modelId, String modelName, String apiFormat) {
            this.providerId = providerId == null ? "" : providerId;
            this.apiKey = apiKey == null ? "" : apiKey;
            this.endpoint = endpoint == null ? "" : endpoint;
            this.modelId = modelId == null ? "" : modelId;
            this.modelName = modelName == null ? "" : modelName;
            this.apiFormat = apiFormat == null ? "" : apiFormat;
        }
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
        this.renderPanelBackground(guiGraphics, layout);

        // 渲染提示文字，使用翻译键
        guiGraphics.drawCenteredString(this.font, this.title, layout.centerX(), layout.titleY(), HeroTheme.LABEL);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.api_setup.prompt_input"), layout.centerX(), layout.promptY(), HeroTheme.TEXT);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.api_setup.prompt_provider"), layout.centerX(), layout.providerPromptY(), HeroTheme.INFO);

        // 字段标签：画在输入框左侧、与该行输入框垂直居中对齐。
        // 每行只占一个 20px 输入框的高度，行距因此能拉大，中文标签不会再贴着上一行的框。
        this.drawFieldLabel(guiGraphics, layout.contentX(), layout.apiKeyBoxY(), "gui.herobrine_companion.api_setup.api_key");
        this.drawFieldLabel(guiGraphics, layout.contentX(), layout.providerIdBoxY(), "gui.herobrine_companion.api_setup.provider_id");
        this.drawFieldLabel(guiGraphics, layout.contentX(), layout.endpointBoxY(), "gui.herobrine_companion.api_setup.endpoint_input");
        this.drawFieldLabel(guiGraphics, layout.contentX(), layout.modelBoxY(), "gui.herobrine_companion.api_setup.model_id");
        this.drawFieldLabel(guiGraphics, layout.contentX(), layout.modelNameBoxY(), "gui.herobrine_companion.api_setup.model_name");
        if (this.isCustomProviderSelected()) {
            this.drawFieldLabel(guiGraphics, layout.contentX(), layout.formatBoxY(), "gui.herobrine_companion.api_setup.format");
        }

        // 浮动层打开时先隐藏会被它盖住的输入框：没有排队的文字，就不可能穿透浮层。
        // 必须在 super.render() 之前设置，控件才会当帧就不参与绘制。
        this.syncOverlayOcclusion();

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 下拉浮层最后画：先隐藏被覆盖的控件，再用不透明底整块盖住，
        // 然后才画滚动条——滚动条若画在浮层之上会横穿列表，视觉上像"穿透"。
        if (this.formatDropdownOpen && this.isCustomProviderSelected()) {
            this.renderFormatDropdown(guiGraphics, mouseX, mouseY);
            return;
        }

        if (this.modelDropdownOpen) {
            this.renderModelDropdown(guiGraphics, mouseX, mouseY);
            return;
        }

        this.renderScrollbar(guiGraphics, layout, mouseX, mouseY);

        int textY = layout.infoY();
        int infoWidth = Math.max(1, Math.min(320, layout.contentWidth() + 40));
        if (!this.modelDiscoveryStatus.getString().isBlank()) {
            textY = this.drawWrappedCenteredText(guiGraphics, this.modelDiscoveryStatus, layout.centerX(), textY, infoWidth, this.modelDiscoveryStatusColor) + 2;
        } else if (!this.saveButton.active) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.api_setup.warning_required"), layout.centerX(), layout.warningY(), HeroTheme.WARN);
        }

        textY = this.drawWrappedCenteredText(guiGraphics,
                this.getEndpointDisplayMessage(),
                layout.centerX(), textY, infoWidth, 0x9FD2FF);
        textY = this.drawWrappedCenteredText(guiGraphics, this.getProviderHint(), layout.centerX(), textY + 2, infoWidth, HeroTheme.TEXT);
        this.drawWrappedCenteredText(guiGraphics,
                Component.translatable("gui.herobrine_companion.api_setup.prompt_safe"),
                layout.centerX(), textY + 4, infoWidth, HeroTheme.DISABLED);
    }

    /**
     * 字段名绘制：贴在输入框左侧、与该行垂直居中。
     *
     * <p>{@code boxY} 是输入框顶边；输入框高 {@link #BUTTON_HEIGHT}，字高 8，
     * 因此基线偏移取 {@code (BUTTON_HEIGHT - 8) / 2}，与原版 EditBox 的居中公式一致。</p>
     */
    private void drawFieldLabel(GuiGraphics guiGraphics, int x, int boxY, String translationKey) {
        HeroTheme.fieldLabel(guiGraphics, this.font, x,
                boxY + (BUTTON_HEIGHT - 8) / 2, Component.translatable(translationKey));
    }

    /**
     * 与 HeroScreen 一致的面板底：主内容区、顶栏、底栏与外框。
     *
     * <p>内容整体可滚动，所以面板本身按视口（viewportTop/Height）绘制，滚动时只移动内容，
     * 边框保持固定——滚动条的意义就是"框不动、内容动"。</p>
     */
    private void renderPanelBackground(GuiGraphics guiGraphics, Layout layout) {
        int left = layout.contentX() - 6;
        int right = layout.contentX() + layout.contentWidth() + 6;
        int top = layout.viewportTop();
        int bottom = layout.viewportTop() + layout.viewportHeight();
        guiGraphics.fill(left, top, right, bottom, HeroTheme.BG_MAIN);
        guiGraphics.renderOutline(left, top, right - left, bottom - top, HeroTheme.BORDER);
        // 顶栏强调条：与 HeroScreen 标题栏同款蓝色。
        guiGraphics.fill(left + 1, top + 1, Math.min(right - 1, left + 1 + 140), top + 3, HeroTheme.ACCENT);
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
        if (this.draggingScrollbar && button == 0) {
            HeroScrollbar scrollbar = this.getScrollbarMetrics(this.createLayout());
            this.scrollToScrollbarPosition(mouseY - this.scrollbarGrabOffsetY, scrollbar);
            return true;
        }
        EditBox focusedTextBox = this.getFocusedTextBox();
        if (focusedTextBox != null && focusedTextBox.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingScrollbar && button == 0) {
            this.draggingScrollbar = false;
            return true;
        }
        EditBox focusedTextBox = this.getFocusedTextBox();
        if (focusedTextBox != null && focusedTextBox.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            HeroScrollbar scrollbar = this.getScrollbarMetrics(this.createLayout());
            if (this.isMouseOverScrollbar(mouseX, mouseY, scrollbar)) {
                if (mouseY >= scrollbar.thumbY() && mouseY < scrollbar.thumbY() + scrollbar.thumbHeight()) {
                    this.draggingScrollbar = true;
                    this.scrollbarGrabOffsetY = mouseY - scrollbar.thumbY();
                } else {
                    this.scrollbarGrabOffsetY = scrollbar.thumbHeight() / 2.0D;
                    this.scrollToScrollbarPosition(mouseY - this.scrollbarGrabOffsetY, scrollbar);
                    this.draggingScrollbar = true;
                }
                return true;
            }
        }

        if (this.formatDropdownOpen) {
            if (button == 0 && this.isMouseInsideFormatDropdown(mouseX, mouseY)) {
                int row = ((int) mouseY - this.formatDropdownY) / FORMAT_DROPDOWN_ROW_HEIGHT;
                if (row >= 0 && row < FORMAT_OPTIONS.size()) {
                    String format = FORMAT_OPTIONS.get(row);
                    this.setCustomApiFormatDraft(format);
                    if (this.formatButton != null) {
                        this.formatButton.setMessage(this.getFormatButtonMessage(format));
                    }
                    this.formatDropdownOpen = false;
                    this.updateSaveButtonState();
                }
                return true;
            }
            this.formatDropdownOpen = false;
            this.updateSaveButtonState();
            return super.mouseClicked(mouseX, mouseY, button);
        }

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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.isMouseInsideModelDropdown(mouseX, mouseY)) {
            this.scrollModelDropdown(verticalAmount > 0 ? -1 : 1);
            return true;
        }
        int maxScroll = this.getMaxScroll();
        if (maxScroll > 0) {
            this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset - (int) (verticalAmount * SCROLL_STEP)));
            this.applyLayoutToWidgets(this.createLayout());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private record Layout(
            int contentX,
            int contentTop,
            int contentWidth,
            int contentHeight,
            int centerX,
            int titleY,
            int promptY,
            int localButtonY,
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
            int formatBoxY,
            int actionButtonY,
            int actionButtonWidth,
            int saveButtonX,
            int routingButtonX,
            int cancelButtonX,
            int warningY,
            int infoY,
            int modelDropdownY,
            int viewportTop,
            int viewportHeight,
            /** 输入框左边界（字段名右侧）。 */
            int fieldX,
            /** 输入框可用宽度。 */
            int fieldWidth,
            /** 左侧字段名列宽。 */
            int labelWidth
    ) {
    }

}