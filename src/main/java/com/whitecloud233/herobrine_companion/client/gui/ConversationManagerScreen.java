package com.whitecloud233.herobrine_companion.client.gui;

import com.whitecloud233.herobrine_companion.client.event.ClientHooks;
import com.whitecloud233.herobrine_companion.client.service.ConversationStore;
import com.whitecloud233.herobrine_companion.config.ApiKeyInputScreen;
import com.whitecloud233.herobrine_companion.config.LLMSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import com.whitecloud233.herobrine_companion.client.service.AIService;

import java.io.File;
import java.util.List;
import java.util.UUID;

public class ConversationManagerScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 340;
    private static final int MAX_PANEL_HEIGHT = 245;
    private static final int MIN_PANEL_WIDTH = 260;
    private static final int MIN_PANEL_HEIGHT = 220;
    private static final int SCREEN_MARGIN = 8;
    private static final int CONTENT_MARGIN = 10;
    private static final int CONTROL_GAP = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int COL_BG = 0xFF2B2B2B;
    private static final int COL_BORDER = 0xFF555555;
    private static final int COL_TEXT = 0xFFA9B7C6;
    private static final int COL_TITLE = 0xFFCC7832;
    private static final int COL_INFO = 0xFF6A8759;

    private final ConversationStore conversationStore = ConversationStore.getInstance();
    private final int entityId;
    private HeroActionList conversationList;
    private EditBox searchBox;
    private Button continueButton;
    private Button deleteButton;
    private Button exportButton;

    public ConversationManagerScreen(int entityId) {
        super(Component.translatable("gui.herobrine_companion.conversation_manager.title"));
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null) {
            return;
        }

        UUID playerUUID = this.getPlayerUUID();
        if (playerUUID != null) {
            this.conversationStore.loadForCurrentSession();
            this.conversationStore.ensureActiveConversation(playerUUID);
        }

        Layout layout = this.createLayout();

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.apiButtonX, layout.controlsY, layout.topButtonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.conversation_manager.api_setup"),
                button -> Minecraft.getInstance().setScreen(new ApiKeyInputScreen(this)),
                null
        ));


        this.searchBox = new EditBox(this.font, layout.searchX, layout.controlsY, layout.searchWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.conversation_manager.search"));
        this.searchBox.setMaxLength(80);
        this.searchBox.setSuggestion(Component.translatable("gui.herobrine_companion.conversation_manager.search_hint").getString());
        this.searchBox.setResponder(value -> this.refreshConversationList());
        this.addRenderableWidget(this.searchBox);

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.settingsButtonX, layout.controlsY, layout.topButtonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.conversation_manager.llm_settings"),
                button -> Minecraft.getInstance().setScreen(new LLMSettingsScreen(this)),
                null
        ));
        this.conversationList = new HeroActionList(this.minecraft, layout.contentWidth, layout.listHeight, layout.listTop, 24);
        this.conversationList.setX(layout.contentLeft);
        this.refreshConversationList();
        this.addRenderableWidget(this.conversationList);

        int actionWidth = (layout.contentWidth - CONTROL_GAP * 3) / 4;
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft, layout.actionY, actionWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.conversation_manager.new"),
                button -> {
                    UUID uuid = this.getPlayerUUID();
                    if (uuid != null) {
                        AIService.createFreshConversation(uuid);
                        this.refreshConversationList();
                    }
                },
                null
        ));

        this.exportButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft + (actionWidth + CONTROL_GAP), layout.actionY, actionWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.conversation_manager.export"),
                button -> this.exportActiveConversation(),
                null
        ));

        this.continueButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft + (actionWidth + CONTROL_GAP) * 2, layout.actionY, actionWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.conversation_manager.continue"),
                button -> this.openChat(),
                null
        ));

        this.deleteButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft + (actionWidth + CONTROL_GAP) * 3, layout.actionY, actionWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.conversation_manager.delete"),
                button -> {
                    UUID uuid = this.getPlayerUUID();
                    if (uuid != null) {
                        List<ConversationStore.ConversationSummary> conversations = this.conversationStore.listConversations(uuid);
                        for (ConversationStore.ConversationSummary summary : conversations) {
                            if (summary.active()) {
                                this.conversationStore.deleteConversation(uuid, summary.id());
                                this.refreshConversationList();
                                break;
                            }
                        }
                    }
                },
                null
        ));

        this.setInitialFocus(this.searchBox);

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.left + (layout.panelWidth - 100) / 2, layout.backY, 100, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.back"),
                button -> Minecraft.getInstance().setScreen(new HeroScreen(this.entityId)),
                null
        ));

        this.updateButtonStates();
    }

    private Layout createLayout() {
        int availableWidth = Math.max(1, this.width - SCREEN_MARGIN * 2);
        int availableHeight = Math.max(1, this.height - SCREEN_MARGIN * 2);
        int panelWidth = clampToAvailable(MIN_PANEL_WIDTH, MAX_PANEL_WIDTH, availableWidth);
        int panelHeight = clampToAvailable(MIN_PANEL_HEIGHT, MAX_PANEL_HEIGHT, availableHeight);
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int contentLeft = left + CONTENT_MARGIN;
        int contentWidth = Math.max(1, panelWidth - CONTENT_MARGIN * 2);
        int controlsY = top + 38;
        int topButtonWidth = contentWidth >= 210
                ? Math.min(76, Math.max(54, (contentWidth - 70 - CONTROL_GAP * 2) / 2))
                : Math.max(32, (contentWidth - CONTROL_GAP * 2) / 3);
        int searchX = contentLeft + topButtonWidth + CONTROL_GAP;
        int settingsButtonX = left + panelWidth - CONTENT_MARGIN - topButtonWidth;
        int searchWidth = Math.max(1, settingsButtonX - CONTROL_GAP - searchX);
        int backY = top + panelHeight - 28;
        int actionY = backY - 26;
        int hintTop = actionY - 24;
        int listTop = controlsY + 28;
        int listHeight = Math.max(40, hintTop - listTop - 4);

        return new Layout(panelWidth, panelHeight, left, top, contentLeft, contentWidth, controlsY,
                contentLeft, searchX, searchWidth, settingsButtonX, topButtonWidth,
                listTop, listHeight, hintTop, actionY, backY);
    }

    private static int clampToAvailable(int min, int max, int available) {
        int clamped = Math.min(max, available);
        return Math.max(Math.min(min, available), clamped);
    }

    private UUID getPlayerUUID() {
        return this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
    }

    private void refreshConversationList() {
        if (this.conversationList == null) {
            return;
        }

        this.conversationList.clearActions();
        UUID playerUUID = this.getPlayerUUID();
        if (playerUUID == null) {
            return;
        }

        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim();
        List<ConversationStore.ConversationSearchResult> conversations = this.conversationStore.searchConversations(playerUUID, query);
        if (conversations.isEmpty()) {
            this.conversationList.addAction(Component.translatable("gui.herobrine_companion.conversation_manager.search_empty"), button -> {}, null);
        }

        for (ConversationStore.ConversationSearchResult result : conversations) {
            ConversationStore.ConversationSummary summary = result.summary();
            this.conversationList.addDynamicAction(() -> {
                MutableComponent label = Component.literal(summary.active() ? "✓ " : "• ");
                label.append(Component.literal(summary.title()));
                label.append(Component.literal(" [" + summary.messageCount() + "]"));
                if (!query.isEmpty()) {
                    label.append(Component.literal(" {" + result.matchCount() + "}"));
                }
                return label;
            }, button -> {
                this.conversationStore.setActiveConversation(playerUUID, summary.id());
                this.refreshConversationList();
            }, null);
        }
        this.updateButtonStates();
    }

    private void updateButtonStates() {
        UUID playerUUID = this.getPlayerUUID();
        boolean hasPlayer = playerUUID != null;
        if (this.continueButton != null) {
            this.continueButton.active = hasPlayer;
        }
        if (this.deleteButton != null) {
            this.deleteButton.active = hasPlayer;
        }
        if (this.exportButton != null) {
            this.exportButton.active = hasPlayer;
        }
    }

    private void exportActiveConversation() {
        Minecraft mc = Minecraft.getInstance();
        UUID playerUUID = this.getPlayerUUID();
        if (mc.player == null || playerUUID == null) {
            return;
        }

        File exported = this.conversationStore.exportActiveConversation(playerUUID);
        if (exported != null) {
            mc.gui.getChat().addMessage(Component.translatable("message.herobrine_companion.conversation_export_success", exported.getName()));
            mc.gui.getChat().addMessage(Component.translatable("message.herobrine_companion.conversation_export_path", exported.getAbsolutePath()));
        } else {
            mc.gui.getChat().addMessage(Component.translatable("message.herobrine_companion.conversation_export_failed"));
        }
    }

    private void openChat() {
        Minecraft mc = Minecraft.getInstance();
        UUID playerUUID = this.getPlayerUUID();
        if (mc.player == null || playerUUID == null) {
            return;
        }

        this.conversationStore.ensureActiveConversation(playerUUID);
        String activeTitle = this.conversationStore.getActiveConversationTitle(playerUUID);
        ClientHooks.enableChat();
        mc.setScreen(new HeroChatScreen(""));

        String modeKey = ClientHooks.isApiEnabled()
                ? "message.herobrine_companion.system_cloud_connected"
                : "message.herobrine_companion.system_local_mode";
        mc.gui.getChat().addMessage(Component.translatable(modeKey));
        if (ClientHooks.isApiEnabled()) {
            mc.gui.getChat().addMessage(Component.translatable("message.herobrine_companion.chat_active_conversation", activeTitle));
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No default background overlay.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = this.createLayout();

        guiGraphics.fill(layout.left, layout.top, layout.left + layout.panelWidth, layout.top + layout.panelHeight, COL_BG);
        guiGraphics.renderOutline(layout.left, layout.top, layout.panelWidth, layout.panelHeight, COL_BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, layout.left + layout.panelWidth / 2, layout.top + 10, COL_TITLE);

        UUID playerUUID = this.getPlayerUUID();
        String activeTitle = playerUUID == null
                ? "-"
                : this.conversationStore.getActiveConversationTitle(playerUUID);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.conversation_manager.active", Component.literal(activeTitle)),
                layout.contentLeft, layout.top + 22, COL_INFO, false);
        guiGraphics.enableScissor(layout.contentLeft, layout.hintTop,
                layout.contentLeft + layout.contentWidth, layout.actionY - 2);
        guiGraphics.drawWordWrap(this.font,
                Component.translatable("gui.herobrine_companion.conversation_manager.hint"),
                layout.contentLeft, layout.hintTop, layout.contentWidth, COL_TEXT);
        guiGraphics.disableScissor();

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class Layout {
        private final int panelWidth;
        private final int panelHeight;
        private final int left;
        private final int top;
        private final int contentLeft;
        private final int contentWidth;
        private final int controlsY;
        private final int apiButtonX;
        private final int searchX;
        private final int searchWidth;
        private final int settingsButtonX;
        private final int topButtonWidth;
        private final int listTop;
        private final int listHeight;
        private final int hintTop;
        private final int actionY;
        private final int backY;

        private Layout(int panelWidth, int panelHeight, int left, int top, int contentLeft, int contentWidth,
                       int controlsY, int apiButtonX, int searchX, int searchWidth, int settingsButtonX,
                       int topButtonWidth, int listTop, int listHeight, int hintTop, int actionY, int backY) {
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;
            this.left = left;
            this.top = top;
            this.contentLeft = contentLeft;
            this.contentWidth = contentWidth;
            this.controlsY = controlsY;
            this.apiButtonX = apiButtonX;
            this.searchX = searchX;
            this.searchWidth = searchWidth;
            this.settingsButtonX = settingsButtonX;
            this.topButtonWidth = topButtonWidth;
            this.listTop = listTop;
            this.listHeight = listHeight;
            this.hintTop = hintTop;
            this.actionY = actionY;
            this.backY = backY;
        }
    }
}


