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
    private static final int PANEL_WIDTH = 352;
    private static final int PANEL_HEIGHT = 344;
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
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        this.playerList = new OnlinePlayerList(Minecraft.getInstance(), PANEL_WIDTH - 32, 80, top + 42, 22);
        this.playerList.setX(left + 16);
        this.addRenderableWidget(this.playerList);

        this.requestButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 16, top + 128, 156, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.request"),
                button -> this.sendSelectedRequest(), null
        ));

        this.refreshButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 180, top + 128, 156, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.refresh"),
                button -> this.refreshOnlinePlayers(), null
        ));

        this.permissionButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 16, top + 154, PANEL_WIDTH - 32, 20,
                Component.empty(),
                button -> {
                    boolean next = !ClientHooks.isAllowIncomingCrossChat();
                    PacketHandler.sendToServer(new SetCrossChatPermissionPacket(next));
                    this.updateButtons();
                }, null
        ));

        this.playerChatButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 16, top + 180, 100, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.player_chat"),
                button -> this.openPlayerCrossChat(), null
        ));

        this.hbChatButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 126, top + 180, 100, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.hb_chat"),
                button -> this.openHbCrossChat(), null
        ));

        this.autoChatButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 236, top + 180, 100, 20,
                Component.empty(),
                button -> this.toggleAutoChat(), null
        ));

        this.autoTurnDownButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 276, top + 232, 28, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.auto_turn_limit_decrease"),
                button -> this.adjustAutoTurnLimit(-1), null
        ));

        this.autoTurnUpButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 308, top + 232, 28, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.auto_turn_limit_increase"),
                button -> this.adjustAutoTurnLimit(1), null
        ));

        this.guideButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 16, top + 206, 60, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.guide"),
                button -> Minecraft.getInstance().setScreen(new CrossChatGuideScreen(this)), null
        ));

        this.archiveButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 81, top + 206, 60, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.archive"),
                button -> Minecraft.getInstance().setScreen(new CrossSessionArchiveScreen(this.entityId, this)), null
        ));

        this.exportButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 146, top + 206, 60, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.export"),
                button -> this.exportCurrentPeerHistory(), null
        ));

        this.closeButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 211, top + 206, 60, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.close"),
                button -> {
                    PacketHandler.sendToServer(new CloseCrossChatSessionPacket());
                    this.updateButtons();
                }, null
        ));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 276, top + 206, 60, 20,
                Component.translatable("gui.herobrine_companion.back"),
                button -> Minecraft.getInstance().setScreen(new HeroScreen(this.entityId)), null
        ));

        this.refreshOnlinePlayers();
        this.refreshTicker = PLAYER_REFRESH_INTERVAL;
        this.updateButtons();
        this.setInitialFocus(this.playerList);
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
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BG);
        guiGraphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top + 12, TITLE);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.cross_chat.online_players"),
                left + 16, top + 30, TEXT, false);

        int listLeft = left + 16;
        int listTop = top + 42;
        int listWidth = PANEL_WIDTH - 32;
        int listHeight = 80;
        guiGraphics.fill(listLeft, listTop, listLeft + listWidth, listTop + listHeight, LIST_BG);
        guiGraphics.renderOutline(listLeft, listTop, listWidth, listHeight, BORDER);

        if (this.playerList != null && this.playerList.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.herobrine_companion.cross_chat.no_players"),
                    this.width / 2,
                    listTop + (listHeight / 2) - 4,
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

        guiGraphics.drawString(this.font, autoTurnLimit, left + 16, top + 238, INFO, false);
        guiGraphics.drawString(this.font, selectedPlayer, left + 16, top + 256, INFO, false);
        guiGraphics.drawString(this.font, currentPeer, left + 16, top + 270, INFO, false);
        guiGraphics.drawString(this.font, status, left + 16, top + 284, TEXT, false);
        guiGraphics.drawWordWrap(this.font,
                Component.translatable("gui.herobrine_companion.cross_chat.hint"),
                left + 16, top + 298, PANEL_WIDTH - 32, TEXT);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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


