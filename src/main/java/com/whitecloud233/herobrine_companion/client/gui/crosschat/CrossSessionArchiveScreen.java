package com.whitecloud233.herobrine_companion.client.gui.crosschat;

import com.whitecloud233.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.herobrine_companion.client.service.CrossChatHistoryStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class CrossSessionArchiveScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 332;
    private static final int COL_BG = 0xFF2B2B2B;
    private static final int COL_BORDER = 0xFF555555;
    private static final int COL_TEXT = 0xFFA9B7C6;
    private static final int COL_TITLE = 0xFFD16D9E;
    private static final int COL_INFO = 0xFF6A8759;
    private static final int LIST_BG = 0xFF232323;
    private static final int LIST_SELECTED_BG = 0xFF4E3A4F;
    private static final int LIST_HOVER_BG = 0xFF383838;
    private static final int DETAIL_BG = 0xFF202020;
    private static final int PLAYER_SPEAKER = 0xFF66D9EF;
    private static final int HB_SPEAKER = 0xFFD16D9E;
    private static final DateTimeFormatter DETAIL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CrossChatHistoryStore historyStore = CrossChatHistoryStore.getInstance();
    private final int entityId;
    private final Screen parentScreen;
    private ArchiveList archiveList;
    private EditBox searchBox;
    private Button playerViewButton;
    private Button hbViewButton;
    private Button exportPlayerButton;
    private Button exportHbButton;
    private Button deletePlayerButton;
    private Button deleteHbButton;
    private Button deleteAllButton;
    private String selectedPeerName = "";
    private boolean previewHbMode;
    private List<CrossChatHistoryStore.ArchiveSearchResult> currentResults = List.of();

    public CrossSessionArchiveScreen(int entityId, Screen parentScreen) {
        super(Component.translatable("gui.herobrine_companion.cross_chat.archive_title"));
        this.entityId = entityId;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        this.searchBox = new EditBox(this.font, left + 12, top + 34, PANEL_WIDTH - 24, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.archive_search"));
        this.searchBox.setMaxLength(80);
        this.searchBox.setSuggestion(Component.translatable("gui.herobrine_companion.cross_chat.archive_search_hint").getString());
        this.searchBox.setResponder(value -> this.refreshArchiveList());
        this.addRenderableWidget(this.searchBox);

        this.archiveList = new ArchiveList(Minecraft.getInstance(), 176, 186, top + 62, 24);
        this.archiveList.setX(left + 12);
        this.addRenderableWidget(this.archiveList);

        int rowOneY = top + PANEL_HEIGHT - 58;
        int rowTwoY = top + PANEL_HEIGHT - 32;
        this.playerViewButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 12, rowOneY, 92, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.archive_view_player"),
                button -> {
                    this.previewHbMode = false;
                    this.updateButtons();
                }, null
        ));

        this.hbViewButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 112, rowOneY, 92, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.archive_view_hb"),
                button -> {
                    this.previewHbMode = true;
                    this.updateButtons();
                }, null
        ));

        this.exportPlayerButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 212, rowOneY, 92, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.export_player_only"),
                button -> this.exportSelectedArchive(false), null
        ));

        this.exportHbButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 312, rowOneY, 92, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.export_hb_only"),
                button -> this.exportSelectedArchive(true), null
        ));

        this.deletePlayerButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 12, rowTwoY, 92, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.delete_player_only"),
                button -> this.deleteSelectedModeHistory(false), null
        ));

        this.deleteHbButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 112, rowTwoY, 92, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.delete_hb_only"),
                button -> this.deleteSelectedModeHistory(true), null
        ));

        this.deleteAllButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 212, rowTwoY, 92, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.delete_all_peer"),
                button -> this.deleteSelectedArchive(), null
        ));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 312, rowTwoY, 92, 20,
                Component.translatable("gui.herobrine_companion.back"),
                button -> Minecraft.getInstance().setScreen(this.parentScreen != null ? this.parentScreen : new CrossSessionHubScreen(this.entityId)), null
        ));

        this.refreshArchiveList();
        this.updateButtons();
        this.setInitialFocus(this.searchBox);
    }

    private void refreshArchiveList() {
        if (this.archiveList == null) {
            return;
        }
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim();
        this.currentResults = this.historyStore.searchPeerArchives(query);
        this.archiveList.rebuild(this.currentResults);

        if (!this.selectedPeerName.isBlank()) {
            for (CrossChatHistoryStore.ArchiveSearchResult result : this.currentResults) {
                if (result.summary().peerName().equalsIgnoreCase(this.selectedPeerName)) {
                    this.selectPeer(result.summary().peerName());
                    return;
                }
            }
        }

        if (!this.currentResults.isEmpty()) {
            this.selectPeer(this.currentResults.get(0).summary().peerName());
        } else {
            this.selectedPeerName = "";
            if (this.archiveList != null) {
                this.archiveList.setSelectedByPeerName("");
            }
            this.updateButtons();
        }
    }

    private void selectPeer(String peerName) {
        this.selectedPeerName = peerName == null ? "" : peerName;
        if (this.archiveList != null) {
            this.archiveList.setSelectedByPeerName(this.selectedPeerName);
        }
        this.syncPreviewMode();
        this.updateButtons();
    }

    private void syncPreviewMode() {
        CrossChatHistoryStore.ArchiveSummary summary = this.getSelectedSummary();
        if (summary == null) {
            this.previewHbMode = false;
            return;
        }
        if (this.previewHbMode && summary.hbMessageCount() <= 0 && summary.playerMessageCount() > 0) {
            this.previewHbMode = false;
        } else if (!this.previewHbMode && summary.playerMessageCount() <= 0 && summary.hbMessageCount() > 0) {
            this.previewHbMode = true;
        }
    }

    private CrossChatHistoryStore.ArchiveSummary getSelectedSummary() {
        return this.selectedPeerName.isBlank() ? null : this.historyStore.getArchiveSummary(this.selectedPeerName);
    }

    private void updateButtons() {
        CrossChatHistoryStore.ArchiveSummary summary = this.getSelectedSummary();
        boolean hasSelection = summary != null;
        if (this.playerViewButton != null) {
            this.playerViewButton.active = hasSelection && summary.playerMessageCount() > 0;
        }
        if (this.hbViewButton != null) {
            this.hbViewButton.active = hasSelection && summary.hbMessageCount() > 0;
        }
        if (this.exportPlayerButton != null) {
            this.exportPlayerButton.active = hasSelection && summary.playerMessageCount() > 0;
        }
        if (this.exportHbButton != null) {
            this.exportHbButton.active = hasSelection && summary.hbMessageCount() > 0;
        }
        if (this.deletePlayerButton != null) {
            this.deletePlayerButton.active = hasSelection && summary.playerMessageCount() > 0;
        }
        if (this.deleteHbButton != null) {
            this.deleteHbButton.active = hasSelection && summary.hbMessageCount() > 0;
        }
        if (this.deleteAllButton != null) {
            this.deleteAllButton.active = hasSelection;
        }
    }

    private void exportSelectedArchive(boolean hbMode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || this.selectedPeerName.isBlank()) {
            return;
        }
        Component modeLabel = Component.translatable(hbMode
                ? "gui.herobrine_companion.cross_chat.archive_mode_hb"
                : "gui.herobrine_companion.cross_chat.archive_mode_player");
        File exported = this.historyStore.exportPeerHistory(this.selectedPeerName, hbMode);
        if (exported == null) {
            mc.gui.getChat().addMessage(Component.translatable(
                    "message.herobrine_companion.cross_chat.export_mode_failed",
                    this.selectedPeerName,
                    modeLabel
            ));
            return;
        }
        mc.gui.getChat().addMessage(Component.translatable(
                "message.herobrine_companion.cross_chat.export_mode_success",
                this.selectedPeerName,
                modeLabel
        ));
        mc.gui.getChat().addMessage(Component.translatable("message.herobrine_companion.cross_chat.export_path", exported.getAbsolutePath()));
    }

    private void deleteSelectedModeHistory(boolean hbMode) {
        Minecraft mc = Minecraft.getInstance();
        CrossChatHistoryStore.ArchiveSummary summary = this.getSelectedSummary();
        if (mc.player == null || summary == null || this.selectedPeerName.isBlank()) {
            return;
        }

        String deletedPeer = this.selectedPeerName;
        this.historyStore.deletePeerArchive(deletedPeer, hbMode);
        mc.gui.getChat().addMessage(Component.translatable(
                "message.herobrine_companion.cross_chat.delete_mode_success",
                deletedPeer,
                Component.translatable(hbMode
                        ? "gui.herobrine_companion.cross_chat.archive_mode_hb"
                        : "gui.herobrine_companion.cross_chat.archive_mode_player")
        ));
        this.refreshArchiveList();
        if (!this.selectedPeerName.isBlank()) {
            this.syncPreviewMode();
        }
        this.updateButtons();
    }

    private void deleteSelectedArchive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || this.selectedPeerName.isBlank()) {
            return;
        }
        String deletedPeer = this.selectedPeerName;
        this.historyStore.deletePeerArchive(deletedPeer);
        mc.gui.getChat().addMessage(Component.translatable("message.herobrine_companion.cross_chat.delete_all_success", deletedPeer));
        this.selectedPeerName = "";
        this.refreshArchiveList();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, COL_BG);
        guiGraphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, COL_BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top + 10, COL_TITLE);

        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.cross_chat.archive_subtitle"),
                left + 12, top + 22, COL_INFO, false);

        int listLeft = left + 12;
        int listTop = top + 62;
        int listWidth = 176;
        int listHeight = 162;
        guiGraphics.fill(listLeft, listTop, listLeft + listWidth, listTop + listHeight, LIST_BG);
        guiGraphics.renderOutline(listLeft, listTop, listWidth, listHeight, COL_BORDER);

        int detailLeft = left + 196;
        int detailTop = top + 62;
        int detailWidth = PANEL_WIDTH - 208;
        int detailHeight = 162;
        guiGraphics.fill(detailLeft, detailTop, detailLeft + detailWidth, detailTop + detailHeight, DETAIL_BG);
        guiGraphics.renderOutline(detailLeft, detailTop, detailWidth, detailHeight, COL_BORDER);

        if (this.archiveList != null && this.archiveList.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.herobrine_companion.cross_chat.archive_empty"),
                    listLeft + (listWidth / 2), listTop + (listHeight / 2) - 4, COL_TEXT);
        }

        this.renderDetailPanel(guiGraphics, detailLeft, detailTop, detailWidth, detailHeight);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderDetailPanel(GuiGraphics guiGraphics, int left, int top, int width, int height) {
        CrossChatHistoryStore.ArchiveSummary summary = this.getSelectedSummary();
        if (summary == null) {
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.herobrine_companion.cross_chat.archive_no_selection"),
                    left + 8, top + 10, COL_TEXT, false);
            return;
        }

        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.cross_chat.archive_current_peer", summary.peerName()),
                left + 8, top + 10, COL_INFO, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.cross_chat.archive_counts", summary.playerMessageCount(), summary.hbMessageCount()),
                left + 8, top + 24, COL_TEXT, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.cross_chat.archive_updated", formatTimestamp(summary.updatedAt())),
                left + 8, top + 38, COL_TEXT, false);

        boolean showingHb = this.previewHbMode && summary.hbMessageCount() > 0;
        Component modeLabel = Component.translatable(showingHb
                ? "gui.herobrine_companion.cross_chat.archive_view_mode_hb"
                : "gui.herobrine_companion.cross_chat.archive_view_mode_player");
        guiGraphics.drawString(this.font, modeLabel, left + 8, top + 54, 0xFFFFD866, false);

        List<CrossChatHistoryStore.HistoryEntrySnapshot> entries = this.historyStore.getEntries(summary.peerName(), showingHb);
        if (entries.isEmpty()) {
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.herobrine_companion.cross_chat.archive_mode_empty"),
                    left + 8, top + 70, COL_TEXT, false);
            return;
        }

        List<FormattedCharSequence> wrappedLines = new ArrayList<>();
        for (CrossChatHistoryStore.HistoryEntrySnapshot entry : entries) {
            wrappedLines.addAll(this.font.split(this.buildPreviewLine(entry), width - 16));
        }

        int maxVisibleLines = Math.max(1, (height - 78) / 10);
        int startIndex = Math.max(0, wrappedLines.size() - maxVisibleLines);
        int y = top + 70;
        for (int i = startIndex; i < wrappedLines.size(); i++) {
            guiGraphics.drawString(this.font, wrappedLines.get(i), left + 8, y, COL_TEXT, false);
            y += 10;
        }
    }

    private MutableComponent buildPreviewLine(CrossChatHistoryStore.HistoryEntrySnapshot entry) {
        int speakerColor = CrossChatHistoryStore.KIND_HB.equalsIgnoreCase(entry.kind()) ? HB_SPEAKER : PLAYER_SPEAKER;
        String speakerName = entry.speaker() == null || entry.speaker().isBlank()
                ? Component.translatable("gui.herobrine_companion.cross_chat.archive_unknown_speaker").getString()
                : entry.speaker();
        MutableComponent speaker = CrossChatHistoryStore.KIND_HB.equalsIgnoreCase(entry.kind())
                ? Component.translatable("gui.herobrine_companion.cross_chat.archive_hb_speaker", speakerName)
                : Component.literal(speakerName);
        MutableComponent line = speaker
                .withStyle(style -> style.withColor(speakerColor))
                .append(Component.literal(": ").withStyle(style -> style.withColor(0xFF808080)));
        line.append(Component.literal(entry.content() == null ? "" : entry.content()).withStyle(style -> style.withColor(COL_TEXT)));
        return line;
    }

    private static String formatTimestamp(long timestamp) {
        long safeTimestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        return DETAIL_TIME.format(Instant.ofEpochMilli(safeTimestamp).atZone(ZoneId.systemDefault()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class ArchiveList extends ObjectSelectionList<ArchiveList.ArchiveEntry> {
        public ArchiveList(Minecraft minecraft, int width, int height, int top, int itemHeight) {
            super(minecraft, width, height, top, itemHeight);
            this.setRenderHeader(false, 0);
        }

        public void rebuild(List<CrossChatHistoryStore.ArchiveSearchResult> results) {
            this.clearEntries();
            for (CrossChatHistoryStore.ArchiveSearchResult result : results) {
                this.addEntry(new ArchiveEntry(result));
            }
        }

        public void setSelectedByPeerName(String peerName) {
            if (peerName == null || peerName.isBlank()) {
                this.setSelected(null);
                return;
            }
            for (ArchiveEntry entry : this.children()) {
                if (entry.summary.peerName().equalsIgnoreCase(peerName)) {
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
            for (ArchiveEntry entry : this.children()) {
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
                guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, COL_TITLE);
            }
        }

        public int getMaxScroll() {
            return Math.max(0, this.getMaxPosition() - (this.getHeight() - 4));
        }

        public int getMaxPosition() {
            return this.getItemCount() * this.itemHeight + this.headerHeight;
        }

        private class ArchiveEntry extends Entry<ArchiveEntry> {
            private final CrossChatHistoryStore.ArchiveSummary summary;
            private final int matchCount;

            private ArchiveEntry(CrossChatHistoryStore.ArchiveSearchResult result) {
                this.summary = result.summary();
                this.matchCount = result.matchCount();
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
                boolean hovered = mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= top + height;
                boolean selected = CrossSessionArchiveScreen.this.selectedPeerName.equalsIgnoreCase(this.summary.peerName());
                int background = selected ? LIST_SELECTED_BG : (hovered ? LIST_HOVER_BG : LIST_BG);
                guiGraphics.fill(left, top, left + width, top + height - 2, background);
                guiGraphics.renderOutline(left, top, width, height - 2, selected ? COL_TITLE : COL_BORDER);

                Component label = Component.translatable(
                        "gui.herobrine_companion.cross_chat.archive_entry_label",
                        this.summary.peerName(),
                        this.summary.playerMessageCount(),
                        this.summary.hbMessageCount(),
                        this.matchCount > 0 ? " {" + this.matchCount + "}" : ""
                );
                guiGraphics.drawString(CrossSessionArchiveScreen.this.font, label, left + 6, top + 7, selected ? 0xFFFFD866 : COL_TEXT, false);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    CrossSessionArchiveScreen.this.selectPeer(this.summary.peerName());
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return Component.translatable(
                        "gui.herobrine_companion.cross_chat.archive_entry_narration",
                        this.summary.peerName(),
                        this.summary.playerMessageCount(),
                        this.summary.hbMessageCount()
                );
            }
        }
    }
}

