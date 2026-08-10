package com.whitecloud233.modid.herobrine_companion.config;

import com.whitecloud233.modid.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmTask;
import com.whitecloud233.modid.herobrine_companion.client.service.LLMConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 任务路由页（单一职责：为每个 LLM 请求类型分配主 provider 与备用 provider）。
 * 空值 = 跟随全局激活 provider / 无备用。从 API Key 页进入，保存写回 LLMConfig。
 */
public class LLMRoutingScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 560;
    private static final int MAX_PANEL_HEIGHT = 400;
    private static final int BUTTON_HEIGHT = 18;
    private static final int ROW_HEIGHT = 28;
    private static final int LABEL_WIDTH = 150;
    private static final int CONTROL_GAP = 8;
    private static final int COL_BG = 0xFF2B2B2B;
    private static final int COL_BORDER = 0xFF555555;
    private static final int COL_TITLE = 0xFFCC7832;

    private final Screen lastScreen;
    private final Map<LlmTask, LLMConfig.TaskRoute> routeDrafts = new EnumMap<>(LlmTask.class);

    public LLMRoutingScreen(Screen lastScreen) {
        super(Component.translatable("gui.herobrine_companion.routing.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        super.init();
        LLMConfig.ensureLoaded();
        if (this.routeDrafts.isEmpty()) {
            for (LlmTask task : LlmTask.values()) {
                LLMConfig.TaskRoute route = LLMConfig.getTaskRoute(task);
                this.routeDrafts.put(task, route == null ? new LLMConfig.TaskRoute("", "") : route);
            }
        }

        int screenMargin = 8;
        int panelWidth = Math.max(1, Math.min(MAX_PANEL_WIDTH, this.width - screenMargin * 2));
        int panelLeft = Math.max(screenMargin, (this.width - panelWidth) / 2);
        int panelHeight = Math.max(1, Math.min(MAX_PANEL_HEIGHT, this.height - screenMargin * 2));
        int panelTop = Math.max(screenMargin, (this.height - panelHeight) / 2);
        int contentLeft = panelLeft + 16;
        int contentWidth = panelWidth - 32;
        int buttonAreaWidth = contentWidth - LABEL_WIDTH - CONTROL_GAP;
        int buttonWidth = Math.max(1, (buttonAreaWidth - CONTROL_GAP) / 2);
        int primaryX = contentLeft + LABEL_WIDTH + CONTROL_GAP;
        int fallbackX = primaryX + buttonWidth + CONTROL_GAP;

        int rowHeight = computeRowHeight(panelHeight);
        int rowsStartY = panelTop + 74;
        int rowIndex = 0;
        for (LlmTask task : LlmTask.values()) {
            int rowY = rowsStartY + rowIndex * rowHeight;
            final LlmTask capturedTask = task;
            this.addRenderableWidget(new HeroScreen.ThemedButton(
                    primaryX, rowY, buttonWidth, BUTTON_HEIGHT,
                    this.getPrimaryButtonMessage(capturedTask),
                    button -> {
                        this.cyclePrimary(capturedTask);
                        button.setMessage(this.getPrimaryButtonMessage(capturedTask));
                    },
                    null));
            this.addRenderableWidget(new HeroScreen.ThemedButton(
                    fallbackX, rowY, buttonWidth, BUTTON_HEIGHT,
                    this.getFallbackButtonMessage(capturedTask),
                    button -> {
                        this.cycleFallback(capturedTask);
                        button.setMessage(this.getFallbackButtonMessage(capturedTask));
                    },
                    null));
            rowIndex++;
        }

        int buttonY = panelTop + panelHeight - 36;
        int actionWidth = Math.min(120, Math.max(1, (contentWidth - CONTROL_GAP) / 2));
        int actionGroupWidth = actionWidth * 2 + CONTROL_GAP;
        int saveX = panelLeft + (panelWidth - actionGroupWidth) / 2;
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                saveX, buttonY, actionWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.api_setup.confirm_save"),
                button -> this.saveAndClose(),
                null));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                saveX + actionWidth + CONTROL_GAP, buttonY, actionWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.back"),
                button -> Minecraft.getInstance().setScreen(this.lastScreen),
                null));
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
        // No default dim overlay.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int screenMargin = 8;
        int panelWidth = Math.max(1, Math.min(MAX_PANEL_WIDTH, this.width - screenMargin * 2));
        int panelLeft = Math.max(screenMargin, (this.width - panelWidth) / 2);
        int panelHeight = Math.max(1, Math.min(MAX_PANEL_HEIGHT, this.height - screenMargin * 2));
        int panelTop = Math.max(screenMargin, (this.height - panelHeight) / 2);
        int contentLeft = panelLeft + 16;
        int contentWidth = panelWidth - 32;
        int buttonAreaWidth = contentWidth - LABEL_WIDTH - CONTROL_GAP;
        int buttonWidth = Math.max(1, (buttonAreaWidth - CONTROL_GAP) / 2);
        int primaryX = contentLeft + LABEL_WIDTH + CONTROL_GAP;
        int fallbackX = primaryX + buttonWidth + CONTROL_GAP;

        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, COL_BG);
        guiGraphics.renderOutline(panelLeft, panelTop, panelWidth, panelHeight, COL_BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, panelTop + 12, COL_TITLE);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.routing.task_header"),
                contentLeft, panelTop + 58, 0xFFAAAAAA, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.routing.primary"),
                primaryX, panelTop + 58, 0xFFFFFF, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.routing.fallback"),
                fallbackX, panelTop + 58, 0xFFFFFF, false);

        int rowHeight = computeRowHeight(panelHeight);
        int rowsStartY = panelTop + 74;
        int rowIndex = 0;
        for (LlmTask task : LlmTask.values()) {
            int rowY = rowsStartY + rowIndex * rowHeight;
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.herobrine_companion.routing.task." + task.name().toLowerCase(java.util.Locale.ROOT)),
                    contentLeft, rowY + 4, 0xE0E0E0, false);
            rowIndex++;
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 行高随面板高度收缩，保证小窗口下面板不超出屏幕且 7 行不挤到底部按钮。 */
    private static int computeRowHeight(int panelHeight) {
        int available = (panelHeight - 40) - 74;
        return Math.min(ROW_HEIGHT, Math.max(22, available / LlmTask.values().length));
    }

    private LLMConfig.Provider primaryProvider(LlmTask task) {
        LLMConfig.TaskRoute route = this.routeDrafts.get(task);
        if (route == null || route.providerId().isBlank()) {
            return null;
        }
        return LLMConfig.Provider.fromSavedValue(route.providerId());
    }

    private LLMConfig.Provider fallbackProvider(LlmTask task) {
        LLMConfig.TaskRoute route = this.routeDrafts.get(task);
        if (route == null || route.fallbackProviderId().isBlank()) {
            return null;
        }
        return LLMConfig.Provider.fromSavedValue(route.fallbackProviderId());
    }

    private Component getPrimaryButtonMessage(LlmTask task) {
        LLMConfig.Provider provider = this.primaryProvider(task);
        return provider == null
                ? Component.translatable("gui.herobrine_companion.routing.default")
                : this.providerName(provider);
    }

    private Component getFallbackButtonMessage(LlmTask task) {
        LLMConfig.Provider provider = this.fallbackProvider(task);
        return provider == null
                ? Component.translatable("gui.herobrine_companion.routing.fallback_none")
                : this.providerName(provider);
    }

    private Component providerName(LLMConfig.Provider provider) {
        return switch (provider) {
            case DEEPSEEK_OFFICIAL -> Component.translatable("gui.herobrine_companion.api_setup.provider.deepseek");
            case OPENROUTER -> Component.translatable("gui.herobrine_companion.api_setup.provider.openrouter");
            case QINIU_CLOUD -> Component.translatable("gui.herobrine_companion.api_setup.provider.qiniu");
            case QINIU_CLOUD_ANTHROPIC -> Component.translatable("gui.herobrine_companion.api_setup.provider.qiniu_anthropic");
            case GEMINI -> Component.translatable("gui.herobrine_companion.api_setup.provider.gemini");
            case CUSTOM -> Component.translatable("gui.herobrine_companion.api_setup.provider.custom");
        };
    }

    private void cyclePrimary(LlmTask task) {
        LLMConfig.Provider current = this.primaryProvider(task);
        LLMConfig.Provider next = cycleProvider(current);
        LLMConfig.TaskRoute route = this.routeDrafts.get(task);
        this.routeDrafts.put(task, new LLMConfig.TaskRoute(
                next == null ? "" : next.getId(),
                route == null ? "" : route.fallbackProviderId()));
    }

    private void cycleFallback(LlmTask task) {
        LLMConfig.Provider current = this.fallbackProvider(task);
        LLMConfig.Provider next = cycleProvider(current);
        LLMConfig.TaskRoute route = this.routeDrafts.get(task);
        this.routeDrafts.put(task, new LLMConfig.TaskRoute(
                route == null ? "" : route.providerId(),
                next == null ? "" : next.getId()));
    }

    /** 循环顺序：null(默认) → 各 Provider。 */
    private static LLMConfig.Provider cycleProvider(LLMConfig.Provider current) {
        LLMConfig.Provider[] values = LLMConfig.Provider.values();
        if (current == null) {
            return values[0];
        }
        int nextIndex = current.ordinal() + 1;
        return nextIndex < values.length ? values[nextIndex] : null;
    }

    private void saveAndClose() {
        for (LlmTask task : LlmTask.values()) {
            LLMConfig.setTaskRoute(task, this.routeDrafts.get(task));
        }
        LLMConfig.save();
        Minecraft.getInstance().setScreen(this.lastScreen);
    }
}
