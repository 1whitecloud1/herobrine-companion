package com.whitecloud233.herobrine_companion.client.gui;

import com.whitecloud233.herobrine_companion.client.event.ClientHooks;
import com.whitecloud233.herobrine_companion.client.service.ConversationStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.File;
import java.util.List;
import java.util.UUID;

public class ConversationManagerScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 245;
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

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startX = centerX - PANEL_WIDTH / 2;
        int startY = centerY - PANEL_HEIGHT / 2;

        this.searchBox = new EditBox(this.font, startX + 10, startY + 38, PANEL_WIDTH - 20, 20,
                Component.translatable("gui.herobrine_companion.conversation_manager.search"));
        this.searchBox.setMaxLength(80);
        this.searchBox.setSuggestion(Component.translatable("gui.herobrine_companion.conversation_manager.search_hint").getString());
        this.searchBox.setResponder(value -> this.refreshConversationList());
        this.addRenderableWidget(this.searchBox);

        this.conversationList = new HeroActionList(this.minecraft, PANEL_WIDTH - 20, 96, startY + 66, 24);
        this.conversationList.setX(startX + 10);
        this.refreshConversationList();
        this.addRenderableWidget(this.conversationList);

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                startX + 10, startY + PANEL_HEIGHT - 52, 74, 20,
                Component.translatable("gui.herobrine_companion.conversation_manager.new"),
                button -> {
                    UUID uuid = this.getPlayerUUID();
                    if (uuid != null) {
                        this.conversationStore.createConversation(uuid);
                        this.refreshConversationList();
                    }
                },
                null
        ));

        this.exportButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                startX + 92, startY + PANEL_HEIGHT - 52, 74, 20,
                Component.translatable("gui.herobrine_companion.conversation_manager.export"),
                button -> this.exportActiveConversation(),
                null
        ));

        this.continueButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                startX + 174, startY + PANEL_HEIGHT - 52, 74, 20,
                Component.translatable("gui.herobrine_companion.conversation_manager.continue"),
                button -> this.openChat(),
                null
        ));

        this.deleteButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                startX + 256, startY + PANEL_HEIGHT - 52, 74, 20,
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
                centerX - 50, startY + PANEL_HEIGHT - 25, 100, 20,
                Component.translatable("gui.herobrine_companion.back"),
                button -> Minecraft.getInstance().setScreen(new HeroScreen(this.entityId)),
                null
        ));

        this.updateButtonStates();
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
        mc.setScreen(new ChatScreen(""));

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
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startX = centerX - PANEL_WIDTH / 2;
        int startY = centerY - PANEL_HEIGHT / 2;

        guiGraphics.fill(startX, startY, startX + PANEL_WIDTH, startY + PANEL_HEIGHT, COL_BG);
        guiGraphics.renderOutline(startX, startY, PANEL_WIDTH, PANEL_HEIGHT, COL_BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, centerX, startY + 10, COL_TITLE);

        UUID playerUUID = this.getPlayerUUID();
        String activeTitle = playerUUID == null
                ? "-"
                : this.conversationStore.getActiveConversationTitle(playerUUID);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.conversation_manager.active", Component.literal(activeTitle)),
                startX + 10, startY + 22, COL_INFO, false);
        guiGraphics.drawWordWrap(this.font,
                Component.translatable("gui.herobrine_companion.conversation_manager.hint"),
                startX + 10, startY + 168, PANEL_WIDTH - 20, COL_TEXT);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}


