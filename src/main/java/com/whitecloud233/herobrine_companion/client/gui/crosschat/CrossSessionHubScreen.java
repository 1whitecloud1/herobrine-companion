package com.whitecloud233.herobrine_companion.client.gui.crosschat;

import com.whitecloud233.herobrine_companion.client.event.ClientHooks;
import com.whitecloud233.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.herobrine_companion.client.service.CrossChatHistoryStore;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.ai.CloseCrossChatSessionPacket;
import com.whitecloud233.herobrine_companion.network.ai.RequestCrossChatSessionPacket;
import com.whitecloud233.herobrine_companion.network.ai.SetCrossChatAutoChatPacket;
import com.whitecloud233.herobrine_companion.network.ai.SetCrossChatAutoTurnLimitPacket;
import com.whitecloud233.herobrine_companion.network.ai.SetCrossChatPermissionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.File;
import java.util.*;

public class CrossSessionHubScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 352;
    private static final int MAX_PANEL_HEIGHT = 344;
    private static final int MIN_PANEL_WIDTH = 300;
    private static final int MIN_PANEL_HEIGHT = 310;
    private static final int SCREEN_MARGIN = 8;
    private static final int CONTENT_MARGIN = 16;
    private static final int ROW_GAP = 6;
    private static final int BUTTON_GAP = 8;
    private static final int TOOL_BUTTON_GAP = 5;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BG = 0xFF2B2B2B;
    private static final int BORDER = 0xFF555555;
    private static final int TITLE = 0xFFD16D9E;
    private static final int TEXT = 0xFFA9B7C6;
    private static final int INFO = 0xFF6A8759;
    private static final int LIST_BG = 0xFF232323;
    private static final int LIST_SELECTED_BG = 0xFF4E3A4F;
    private static final int LIST_HOVER_BG = 0xFF383838;
    private static final int PLAYER_REFRESH_INTERVAL = 20;
    private static final int MIN_AUTO_HB_TURN_LIMIT = 1;
    private static final int MAX_AUTO_HB_TURN_LIMIT = 16;

    private final int entityId;
    private OnlinePlayerList playerList;
    private Button requestButton;
    private Button refreshButton;
    private Button permissionButton;
    private Button playerChatButton;
    private Button hbChatButton;
    private Button autoChatButton;
    private Button autoTurnDownButton;
    private Button autoTurnUpButton;
    private Button guideButton;
    private Button archiveButton;
    private Button exportButton;
    private Button closeButton;
    private String selectedPlayerName = "";
    private int refreshTicker;

    public CrossSessionHubScreen(int entityId) {
        super(Component.translatable("gui.herobrine_companion.cross_chat.title"));
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        super.init();
        Layout layout = this.createLayout();

        this.playerList = new OnlinePlayerList(Minecraft.getInstance(), layout.contentWidth, layout.listHeight, layout.listTop, 22);
        this.playerList.setX(layout.contentLeft);
        this.addRenderableWidget(this.playerList);

        int twoColumnWidth = (layout.contentWidth - BUTTON_GAP) / 2;
        this.requestButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft, layout.requestY, twoColumnWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.cross_chat.request"),
                button -> this.sendSelectedRequest(), null
        ));

        this.refreshButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft + twoColumnWidth + BUTTON_GAP, layout.requestY, twoColumnWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.cross_chat.refresh"),
                button -> this.refreshOnlinePlayers(), null
        ));

        this.permissionButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft, layout.permissionY, layout.contentWidth, BUTTON_HEIGHT,
                Component.empty(),
                button -> {
                    boolean next = !ClientHooks.isAllowIncomingCrossChat();
                    PacketHandler.sendToServer(new SetCrossChatPermissionPacket(next));
                    this.updateButtons();
                }, null
        ));

        int threeColumnWidth = (layout.contentWidth - BUTTON_GAP * 2) / 3;
        this.playerChatButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft, layout.chatY, threeColumnWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.cross_chat.player_chat"),
                button -> this.openPlayerCrossChat(), null
        ));

        this.hbChatButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft + threeColumnWidth + BUTTON_GAP, layout.chatY, threeColumnWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.cross_chat.hb_chat"),
                button -> this.openHbCrossChat(), null
        ));

        this.autoChatButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft + (threeColumnWidth + BUTTON_GAP) * 2, layout.chatY, threeColumnWidth, BUTTON_HEIGHT,
                Component.empty(),
                button -> this.toggleAutoChat(), null
        ));

        this.autoTurnDownButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft + layout.contentWidth - 60, layout.autoY, 28, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.cross_chat.auto_turn_limit_decrease"),
                button -> this.adjustAutoTurnLimit(-1), null
        ));

        this.autoTurnUpButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft + layout.contentWidth - 28, layout.autoY, 28, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.cross_chat.auto_turn_limit_increase"),
                button -> this.adjustAutoTurnLimit(1), null
        ));

        int toolButtonWidth = (layout.contentWidth - TOOL_BUTTON_GAP * 4) / 5;
        this.guideButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft, layout.toolsY, toolButtonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.cross_chat.guide"),
                button -> Minecraft.getInstance().setScreen(new CrossChatGuideScreen(this)), null
        ));

        this.archiveButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft + toolButtonWidth + TOOL_BUTTON_GAP, layout.toolsY, toolButtonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.cross_chat.archive"),
                button -> Minecraft.getInstance().setScreen(new CrossSessionArchiveScreen(this.entityId, this)), null
        ));

        this.exportButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft + (toolButtonWidth + TOOL_BUTTON_GAP) * 2, layout.toolsY, toolButtonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.cross_chat.export"),
                button -> this.exportCurrentPeerHistory(), null
        ));

        this.closeButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft + (toolButtonWidth + TOOL_BUTTON_GAP) * 3, layout.toolsY, toolButtonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.cross_chat.close"),
                button -> {
                    PacketHandler.sendToServer(new CloseCrossChatSessionPacket());
                    this.updateButtons();
                }, null
        ));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentLeft + (toolButtonWidth + TOOL_BUTTON_GAP) * 4, layout.toolsY, toolButtonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.back"),
                button -> Minecraft.getInstance().setScreen(new HeroScreen(this.entityId)), null
        ));

        this.refreshOnlinePlayers();
        this.refreshTicker = PLAYER_REFRESH_INTERVAL;
        this.updateButtons();
        this.setInitialFocus(this.playerList);
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
        int listTop = top + 42;
        int listHeight = Math.min(80, Math.max(44, panelHeight - 264));
        int requestY = listTop + listHeight + 6;
        int permissionY = requestY + BUTTON_HEIGHT + ROW_GAP;
        int chatY = permissionY + BUTTON_HEIGHT + ROW_GAP;
        int toolsY = chatY + BUTTON_HEIGHT + ROW_GAP;
        int autoY = toolsY + BUTTON_HEIGHT + ROW_GAP;
        int infoTop = autoY + 24;

        return new Layout(panelWidth, panelHeight, left, top, contentLeft, contentWidth,
                listTop, listHeight, requestY, permissionY, chatY, toolsY, autoY, infoTop);
    }

    private static int clampToAvailable(int min, int max, int available) {
        int clamped = Math.min(max, available);
        return Math.max(Math.min(min, available), clamped);
    }

    private void updateButtons() {
        if (this.requestButton != null) {
            this.requestButton.active = !this.selectedPlayerName.isBlank() && !ClientHooks.hasActiveCrossChatSession();
        }
        if (this.permissionButton != null) {
            this.permissionButton.setMessage(Component.translatable(
                    ClientHooks.isAllowIncomingCrossChat()
                            ? "gui.herobrine_companion.cross_chat.allow_on"
                            : "gui.herobrine_companion.cross_chat.allow_off"
            ));
        }
        if (this.refreshButton != null) {
            this.refreshButton.active = this.minecraft != null && this.minecraft.player != null;
        }
        if (this.playerChatButton != null) {
            this.playerChatButton.active = ClientHooks.hasActiveCrossChatSession();
        }
        if (this.hbChatButton != null) {
            this.hbChatButton.active = ClientHooks.hasActiveCrossChatSession();
        }
        if (this.autoChatButton != null) {
            this.autoChatButton.active = ClientHooks.hasActiveCrossChatSession();
            this.autoChatButton.setMessage(Component.translatable(
                    ClientHooks.isCrossChatAutoChatEnabled()
                            ? "gui.herobrine_companion.cross_chat.auto_on"
                            : "gui.herobrine_companion.cross_chat.auto_off"
            ));
        }
        if (this.autoTurnDownButton != null) {
            this.autoTurnDownButton.active = this.minecraft != null
                    && this.minecraft.player != null
                    && ClientHooks.getCrossChatAutoHbTurnLimit() > MIN_AUTO_HB_TURN_LIMIT;
        }
        if (this.autoTurnUpButton != null) {
            this.autoTurnUpButton.active = this.minecraft != null
                    && this.minecraft.player != null
                    && ClientHooks.getCrossChatAutoHbTurnLimit() < MAX_AUTO_HB_TURN_LIMIT;
        }
        if (this.guideButton != null) {
            this.guideButton.active = true;
        }
        if (this.archiveButton != null) {
            this.archiveButton.active = true;
        }
        if (this.exportButton != null) {
            String peerName = ClientHooks.getCrossChatPeerName();
            this.exportButton.active = ClientHooks.hasActiveCrossChatSession() && peerName != null && !peerName.isBlank();
        }
        if (this.closeButton != null) {
            this.closeButton.active = ClientHooks.hasActiveCrossChatSession();
        }
    }

    private void sendSelectedRequest() {
        if (this.selectedPlayerName.isBlank()) {
            return;
        }
        PacketHandler.sendToServer(new RequestCrossChatSessionPacket(this.selectedPlayerName));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.gui.getChat().addMessage(Component.translatable("message.herobrine_companion.cross_chat.request_sent_hint"));
        }
    }

    private void refreshOnlinePlayers() {
        if (this.playerList == null) {
            return;
        }

        List<String> onlinePlayers = this.collectOnlinePlayerNames();
        String preferredSelection = this.selectedPlayerName;
        this.playerList.rebuild(onlinePlayers);

        if (preferredSelection != null && !preferredSelection.isBlank() && onlinePlayers.contains(preferredSelection)) {
            this.selectPlayer(preferredSelection);
        } else if (!onlinePlayers.isEmpty()) {
            this.selectPlayer(onlinePlayers.get(0));
        } else {
            this.selectPlayer("");
        }

        this.updateButtons();
    }

    private List<String> collectOnlinePlayerNames() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return List.of();
        }

        UUID selfId = mc.player.getUUID();
        Set<String> uniqueNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<String> playerNames = new ArrayList<>();

        for (PlayerInfo info : mc.player.connection.getOnlinePlayers()) {
            if (info == null || info.getProfile().getName() == null) {
                continue;
            }

            UUID playerId = info.getProfile().getId();
            if (playerId != null && playerId.equals(selfId)) {
                continue;
            }

            String playerName = info.getProfile().getName();
            if (playerName.isBlank() || !uniqueNames.add(playerName)) {
                continue;
            }
            playerNames.add(playerName);
        }

        playerNames.sort(String.CASE_INSENSITIVE_ORDER);
        return playerNames;
    }

    private void selectPlayer(String playerName) {
        this.selectedPlayerName = playerName == null ? "" : playerName;
        if (this.playerList != null) {
            this.playerList.setSelectedByName(this.selectedPlayerName);
        }
        this.updateButtons();
    }

    private void openPlayerCrossChat() {
        if (!ClientHooks.hasActiveCrossChatSession()) {
            return;
        }
        ClientHooks.openHeroChatFromCommand(false);
    }

    private void openHbCrossChat() {
        if (!ClientHooks.hasActiveCrossChatSession()) {
            return;
        }
        ClientHooks.openHeroChatFromCommand(true);
    }

    private void toggleAutoChat() {
        if (!ClientHooks.hasActiveCrossChatSession()) {
            return;
        }

        boolean next = !ClientHooks.isCrossChatAutoChatEnabled();
        ClientHooks.setCrossChatAutoChatEnabled(next);
        PacketHandler.sendToServer(new SetCrossChatAutoChatPacket(next));
        this.updateButtons();
    }

    private void adjustAutoTurnLimit(int delta) {
        int current = ClientHooks.getCrossChatAutoHbTurnLimit();
        int next = Mth.clamp(current + delta, MIN_AUTO_HB_TURN_LIMIT, MAX_AUTO_HB_TURN_LIMIT);
        if (next == current) {
            return;
        }
        ClientHooks.setCrossChatAutoHbTurnLimit(next);
        PacketHandler.sendToServer(new SetCrossChatAutoTurnLimitPacket(next));
        this.updateButtons();
    }

    private void exportCurrentPeerHistory() {
        Minecraft mc = Minecraft.getInstance();
        String peerName = ClientHooks.getCrossChatPeerName();
        if (mc.player == null || peerName == null || peerName.isBlank()) {
            return;
        }

        List<File> exported = CrossChatHistoryStore.getInstance().exportPeerHistories(peerName);
        if (exported.isEmpty()) {
            mc.gui.getChat().addMessage(Component.translatable("message.herobrine_companion.cross_chat.export_failed", peerName));
            return;
        }

        mc.gui.getChat().addMessage(Component.translatable("message.herobrine_companion.cross_chat.export_success", peerName, exported.size()));
        for (File file : exported) {
            mc.gui.getChat().addMessage(Component.translatable("message.herobrine_companion.cross_chat.export_path", file.getAbsolutePath()));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (--this.refreshTicker <= 0) {
            this.refreshOnlinePlayers();
            this.refreshTicker = PLAYER_REFRESH_INTERVAL;
        }
        this.updateButtons();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = this.createLayout();
        guiGraphics.fill(layout.left, layout.top, layout.left + layout.panelWidth, layout.top + layout.panelHeight, BG);
        guiGraphics.renderOutline(layout.left, layout.top, layout.panelWidth, layout.panelHeight, BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, layout.top + 12, TITLE);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.cross_chat.online_players"),
                layout.contentLeft, layout.top + 30, TEXT, false);

        guiGraphics.fill(layout.contentLeft, layout.listTop,
                layout.contentLeft + layout.contentWidth, layout.listTop + layout.listHeight, LIST_BG);
        guiGraphics.renderOutline(layout.contentLeft, layout.listTop, layout.contentWidth, layout.listHeight, BORDER);

        if (this.playerList != null && this.playerList.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.herobrine_companion.cross_chat.no_players"),
                    this.width / 2,
                    layout.listTop + (layout.listHeight / 2) - 4,
                    TEXT);
        }

        Component selectedPlayer = Component.translatable(
                "gui.herobrine_companion.cross_chat.selected_player",
                this.selectedPlayerName.isBlank()
                        ? Component.translatable("gui.herobrine_companion.cross_chat.none")
                        : Component.literal(this.selectedPlayerName)
        );

        String peerName = ClientHooks.getCrossChatPeerName();
        Component currentPeer = Component.translatable(
                "gui.herobrine_companion.cross_chat.current_peer",
                peerName == null || peerName.isBlank() ? "-" : peerName
        );
        Component status = Component.translatable(
                ClientHooks.hasActiveCrossChatSession()
                        ? "gui.herobrine_companion.cross_chat.session_active"
                        : "gui.herobrine_companion.cross_chat.session_inactive"
        );

        Component autoTurnLimit = Component.translatable(
                "gui.herobrine_companion.cross_chat.auto_turn_limit",
                ClientHooks.getCrossChatAutoHbTurnLimit()
        );

        guiGraphics.drawString(this.font, autoTurnLimit, layout.contentLeft, layout.autoY + 6, INFO, false);
        guiGraphics.drawString(this.font, selectedPlayer, layout.contentLeft, layout.infoTop, INFO, false);
        guiGraphics.drawString(this.font, currentPeer, layout.contentLeft, layout.infoTop + 14, INFO, false);
        guiGraphics.drawString(this.font, status, layout.contentLeft, layout.infoTop + 28, TEXT, false);
        guiGraphics.enableScissor(layout.contentLeft, layout.infoTop + 42,
                layout.contentLeft + layout.contentWidth, layout.top + layout.panelHeight - 8);
        guiGraphics.drawWordWrap(this.font,
                Component.translatable("gui.herobrine_companion.cross_chat.hint"),
                layout.contentLeft, layout.infoTop + 42, layout.contentWidth, TEXT);
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
        private final int listTop;
        private final int listHeight;
        private final int requestY;
        private final int permissionY;
        private final int chatY;
        private final int toolsY;
        private final int autoY;
        private final int infoTop;

        private Layout(int panelWidth, int panelHeight, int left, int top, int contentLeft, int contentWidth,
                       int listTop, int listHeight, int requestY, int permissionY, int chatY,
                       int toolsY, int autoY, int infoTop) {
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;
            this.left = left;
            this.top = top;
            this.contentLeft = contentLeft;
            this.contentWidth = contentWidth;
            this.listTop = listTop;
            this.listHeight = listHeight;
            this.requestY = requestY;
            this.permissionY = permissionY;
            this.chatY = chatY;
            this.toolsY = toolsY;
            this.autoY = autoY;
            this.infoTop = infoTop;
        }
    }

    private class OnlinePlayerList extends ObjectSelectionList<OnlinePlayerList.PlayerEntry> {
        public OnlinePlayerList(Minecraft minecraft, int width, int height, int top, int itemHeight) {
            super(minecraft, width, height, top, itemHeight);
            this.setRenderHeader(false, 0);
        }

        public void rebuild(List<String> playerNames) {
            this.clearEntries();
            for (String playerName : playerNames) {
                this.addEntry(new PlayerEntry(playerName));
            }
        }

        public void setSelectedByName(String playerName) {
            if (playerName == null || playerName.isBlank()) {
                this.setSelected(null);
                return;
            }

            for (PlayerEntry entry : this.children()) {
                if (entry.playerName.equalsIgnoreCase(playerName)) {
                    this.setSelected(entry);
                    return;
                }
            }

            this.setSelected(null);
        }

        public boolean isEmpty() {
            return this.children().isEmpty();
        }

        @Override
        public int getRowWidth() {
            return this.width - 8;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getX() + this.width - 6;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();

            guiGraphics.enableScissor(x, y, x + width, y + height);

            int itemY = y - (int) this.getScrollAmount();
            for (PlayerEntry entry : this.children()) {
                if (itemY + this.itemHeight >= y && itemY <= y + height) {
                    entry.render(guiGraphics, 0, itemY, x + 2, this.getRowWidth(), this.itemHeight, mouseX, mouseY, false, partialTick);
                }
                itemY += this.itemHeight;
            }

            guiGraphics.disableScissor();

            int maxScroll = this.getMaxScroll();
            if (maxScroll > 0) {
                int scrollbarX = this.getScrollbarPosition();
                int scrollbarHeight = Mth.clamp((int) ((float) (height * height) / (float) this.getMaxPosition()), 20, height - 8);
                int scrollbarY = (int) this.getScrollAmount() * (height - scrollbarHeight) / maxScroll + y;
                if (scrollbarY < y) {
                    scrollbarY = y;
                }

                guiGraphics.fill(scrollbarX, y, scrollbarX + 4, y + height, 0x40000000);
                guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, TITLE);
            }
        }

        public int getMaxScroll() {
            return Math.max(0, this.getMaxPosition() - (this.getHeight() - 4));
        }

        public int getMaxPosition() {
            return this.getItemCount() * this.itemHeight + this.headerHeight;
        }

        private class PlayerEntry extends ObjectSelectionList.Entry<PlayerEntry> {
            private final String playerName;

            private PlayerEntry(String playerName) {
                this.playerName = playerName;
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
                boolean hovered = mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= top + height;
                boolean selected = CrossSessionHubScreen.this.selectedPlayerName.equalsIgnoreCase(this.playerName);
                int background = selected ? LIST_SELECTED_BG : (hovered ? LIST_HOVER_BG : LIST_BG);
                guiGraphics.fill(left, top, left + width, top + height - 2, background);
                guiGraphics.renderOutline(left, top, width, height - 2, selected ? TITLE : BORDER);
                guiGraphics.drawString(CrossSessionHubScreen.this.font, this.playerName, left + 6, top + 7, selected ? 0xFFFFD866 : TEXT, false);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    CrossSessionHubScreen.this.selectPlayer(this.playerName);
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return Component.literal(this.playerName);
            }
        }
    }
}


