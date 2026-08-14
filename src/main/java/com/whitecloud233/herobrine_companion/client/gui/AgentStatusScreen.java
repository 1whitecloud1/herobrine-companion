package com.whitecloud233.herobrine_companion.client.gui;

import com.whitecloud233.herobrine_companion.client.agent.ClientAgentStatusStore;
import com.whitecloud233.herobrine_companion.client.network.ClientMemoryDigest;
import com.whitecloud233.herobrine_companion.entity.ai.agent.AgentStatusSnapshot;
import com.whitecloud233.herobrine_companion.network.ClearHeroMemoryPacket;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.RequestAgentStatusPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Agent 状态面板（M5，对应设计文档 4.9"能解释自己"）。
 *
 * <p><b>单一职责</b>：只做渲染与输入。所有数据一律取自 {@link ClientAgentStatusStore}，
 * 本类不采集、不解析、不缓存业务状态——"刷新"只是发一个请求包，数据何时回来由仓储决定。</p>
 *
 * <p>四个页签对应设计文档要求的四类可观测信息：概览（当前意图 / 上次决策原因）、
 * 任务队列、决策日志、工具审计。</p>
 */
public class AgentStatusScreen extends Screen {

    /** 自动刷新间隔（tick）：1 秒一次，与 {@code HeroAgent.CYCLE_TICKS} 同频。 */
    private static final int REFRESH_INTERVAL_TICKS = 20;

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 200;
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 8;
    private static final int TAB_HEIGHT = 18;

    private static final int COLOR_PANEL = 0xC0101010;
    private static final int COLOR_BORDER = 0xFF3A3A3A;
    private static final int COLOR_TITLE = 0xFFE0E0E0;
    private static final int COLOR_LABEL = 0xFF9A9A9A;
    private static final int COLOR_VALUE = 0xFFFFFFFF;
    private static final int COLOR_BODY = 0xFFC8C8C8;
    private static final int COLOR_EMPTY = 0xFF707070;

    private enum Tab {
        OVERVIEW("gui.herobrine_companion.agent_status.tab.overview"),
        QUEUE("gui.herobrine_companion.agent_status.tab.queue"),
        DECISIONS("gui.herobrine_companion.agent_status.tab.decisions"),
        AUDIT("gui.herobrine_companion.agent_status.tab.audit"),
        MEMORY("gui.herobrine_companion.agent_status.tab.memory");

        private final String titleKey;

        Tab(String titleKey) {
            this.titleKey = titleKey;
        }
    }

    private Tab activeTab = Tab.OVERVIEW;
    private int ticksSinceRefresh;

    /** 记忆页签的清理按钮(默认隐藏,切到记忆页才显示)。 */
    private Button clearNarrativeButton;
    private Button clearAllButton;

    /** 面板实际尺寸：init/render 时按窗口钳制后写入，供各 helper 使用。 */
    private int panelW = PANEL_WIDTH;
    private int panelH = PANEL_HEIGHT;

    public AgentStatusScreen() {
        super(Component.translatable("gui.herobrine_companion.agent_status.title"));
    }

    /** 客户端入口：打开面板并立即请求一次快照。 */
    public static void open() {
        PacketHandler.sendToServer(new RequestAgentStatusPacket());
        Minecraft.getInstance().setScreen(new AgentStatusScreen());
    }

    /** 面板几何：宽高钳制到窗口内，居中。返回 {left, top}，并更新 panelW/panelH。 */
    private int[] panelDims() {
        this.panelW = Math.min(PANEL_WIDTH, Math.max(260, this.width - 16));
        this.panelH = Math.min(PANEL_HEIGHT, Math.max(160, this.height - 16));
        return new int[]{(this.width - panelW) / 2, Math.max(4, (this.height - panelH) / 2)};
    }

    @Override
    protected void init() {
        super.init();
        int[] dims = panelDims();
        int left = dims[0];
        int top = dims[1];

        // 页签按钮：等分面板宽度。
        Tab[] tabs = Tab.values();
        int tabWidth = panelW / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            this.addRenderableWidget(Button.builder(
                            Component.translatable(tab.titleKey),
                            button -> this.activeTab = tab)
                    .bounds(left + i * tabWidth, top + panelH - TAB_HEIGHT - 4, tabWidth - 2, TAB_HEIGHT)
                    .build());
        }

        // 记忆页签专属：清理按钮（默认隐藏，切到记忆页才显示）。
        int buttonW = 72;
        int buttonH = 14;
        int buttonY = top + 30;
        this.clearNarrativeButton = Button.builder(
                        Component.translatable("gui.herobrine_companion.agent_status.clear_narrative"),
                        button -> clearMemory(true))
                .bounds(left + panelW - buttonW * 2 - 10, buttonY, buttonW, buttonH)
                .build();
        this.clearAllButton = Button.builder(
                        Component.translatable("gui.herobrine_companion.agent_status.clear_all"),
                        button -> clearMemory(false))
                .bounds(left + panelW - buttonW - 6, buttonY, buttonW, buttonH)
                .build();
        this.clearNarrativeButton.visible = false;
        this.clearAllButton.visible = false;
        this.addRenderableWidget(this.clearNarrativeButton);
        this.addRenderableWidget(this.clearAllButton);
    }

    /** 清理记忆：清空叙事层（保留偏好）或全部，并让客户端缓存与面板同步。 */
    private void clearMemory(boolean keepFacts) {
        PacketHandler.sendToServer(new ClearHeroMemoryPacket(
                keepFacts ? ClearHeroMemoryPacket.MODE_KEEP_FACTS : ClearHeroMemoryPacket.MODE_CLEAR_ALL));
        ClientMemoryDigest.reset();
        PacketHandler.sendToServer(new RequestAgentStatusPacket());
    }

    @Override
    public void tick() {
        super.tick();
        // 面板打开期间定期刷新，让玩家看到 agent 的实时推进。
        if (++ticksSinceRefresh >= REFRESH_INTERVAL_TICKS) {
            ticksSinceRefresh = 0;
            PacketHandler.sendToServer(new RequestAgentStatusPacket());
        }
    }

    /** 与 {@link HeroScreen} 一致：留空禁用原版世界模糊背景，避免面板后的画面发糊。 */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        int[] dims = panelDims();
        int left = dims[0];
        int top = dims[1];
        guiGraphics.fill(left, top, left + panelW, top + panelH, COLOR_PANEL);
        guiGraphics.renderOutline(left, top, panelW, panelH, COLOR_BORDER);

        AgentStatusSnapshot snapshot = ClientAgentStatusStore.snapshot();
        renderHeader(guiGraphics, left, top, snapshot);

        int bodyX = left + PADDING;
        int bodyY = top + 30;
        int bodyBottom = top + panelH - TAB_HEIGHT - 10;
        // 记忆页才显示清理按钮；其余页隐藏,避免误触。
        boolean memoryTab = activeTab == Tab.MEMORY;
        this.clearNarrativeButton.visible = memoryTab;
        this.clearAllButton.visible = memoryTab;
        switch (activeTab) {
            case OVERVIEW -> renderOverview(guiGraphics, bodyX, bodyY, snapshot);
            case QUEUE -> renderQueue(guiGraphics, bodyX, bodyY, bodyBottom, snapshot);
            case DECISIONS -> renderLines(guiGraphics, bodyX, bodyY, bodyBottom,
                    snapshot.decisions(), Component.translatable("gui.herobrine_companion.agent_status.empty_decisions"));
            case AUDIT -> renderAudit(guiGraphics, bodyX, bodyY, bodyBottom, snapshot);
            case MEMORY -> renderMemory(guiGraphics, bodyX, bodyY, bodyBottom, snapshot);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics guiGraphics, int left, int top, AgentStatusSnapshot snapshot) {
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.agent_status.header"),
                left + PADDING, top + PADDING, COLOR_TITLE, false);

        String freshness = ClientAgentStatusStore.hasData()
                ? Component.translatable("gui.herobrine_companion.agent_status.freshness",
                        snapshot.gameTime(), ClientAgentStatusStore.ageSeconds()).getString()
                : Component.translatable("gui.herobrine_companion.agent_status.waiting_data").getString();
        int textWidth = this.font.width(freshness);
        guiGraphics.drawString(this.font, freshness,
                left + panelW - textWidth - PADDING, top + PADDING, COLOR_LABEL, false);

        guiGraphics.fill(left + 6, top + 22, left + panelW - 6, top + 23, COLOR_BORDER);
    }

    private void renderOverview(GuiGraphics guiGraphics, int x, int y, AgentStatusSnapshot snapshot) {
        int cursor = y;
        cursor = renderField(guiGraphics, x, cursor,
                Component.translatable("gui.herobrine_companion.agent_status.field.intention"),
                snapshot.intention());
        cursor = renderField(guiGraphics, x, cursor,
                Component.translatable("gui.herobrine_companion.agent_status.field.mind"),
                snapshot.mindState());
        cursor = renderField(guiGraphics, x, cursor,
                Component.translatable("gui.herobrine_companion.agent_status.field.queue"),
                snapshot.isQueueIdle()
                        ? Component.translatable("gui.herobrine_companion.agent_status.idle").getString()
                        : Component.translatable("gui.herobrine_companion.agent_status.pending_tasks",
                                snapshot.queueSize()).getString());
        cursor = renderField(guiGraphics, x, cursor,
                Component.translatable("gui.herobrine_companion.agent_status.field.memory"),
                Component.translatable("gui.herobrine_companion.agent_status.memory_count",
                        snapshot.memoryEpisodes(), snapshot.memoryFacts()).getString());
        cursor = renderField(guiGraphics, x, cursor,
                Component.translatable("gui.herobrine_companion.agent_status.field.tools"),
                Component.translatable("gui.herobrine_companion.agent_status.tool_count",
                        snapshot.toolCatalog().size()).getString());

        cursor += 4;
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.agent_status.last_decision"),
                x, cursor, COLOR_LABEL, false);
        cursor += LINE_HEIGHT;
        // 决策行通常很长，按面板宽度折行，保证因果链完整可读。
        List<FormattedCharSequence> wrapped =
                this.font.split(Component.literal(snapshot.lastDecision()), panelW - PADDING * 3);
        for (FormattedCharSequence line : wrapped) {
            guiGraphics.drawString(this.font, line, x + 4, cursor, COLOR_BODY, false);
            cursor += LINE_HEIGHT;
        }
    }

    private void renderQueue(GuiGraphics guiGraphics, int x, int y, int bottom, AgentStatusSnapshot snapshot) {
        int cursor = renderField(guiGraphics, x, y,
                Component.translatable("gui.herobrine_companion.agent_status.field.queue_len"),
                snapshot.isQueueIdle()
                        ? Component.translatable("gui.herobrine_companion.agent_status.idle").getString()
                        : String.valueOf(snapshot.queueSize()));
        cursor = renderField(guiGraphics, x, cursor,
                Component.translatable("gui.herobrine_companion.agent_status.field.progress"),
                snapshot.queueProgress().isEmpty() ? "—" : snapshot.queueProgress());

        cursor += 4;
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.agent_status.queue_log"),
                x, cursor, COLOR_LABEL, false);
        renderLines(guiGraphics, x + 4, cursor + LINE_HEIGHT, bottom,
                snapshot.queueLog(), Component.translatable("gui.herobrine_companion.agent_status.empty_queue_log"));
    }

    /** 审计页：先列"允许做什么"（工具目录），再列"实际做了什么"（审计流水）。 */
    private void renderAudit(GuiGraphics guiGraphics, int x, int y, int bottom, AgentStatusSnapshot snapshot) {
        int cursor = y;
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.agent_status.audit_tools"),
                x, cursor, COLOR_LABEL, false);
        cursor += LINE_HEIGHT;
        for (String tool : snapshot.toolCatalog()) {
            if (cursor > bottom - LINE_HEIGHT * 3) {
                break;
            }
            guiGraphics.drawString(this.font, "· " + clip(tool), x + 4, cursor, COLOR_VALUE, false);
            cursor += LINE_HEIGHT;
        }

        cursor += 4;
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.agent_status.audit_calls"),
                x, cursor, COLOR_LABEL, false);
        renderLines(guiGraphics, x + 4, cursor + LINE_HEIGHT, bottom,
                snapshot.audits(), Component.translatable("gui.herobrine_companion.agent_status.empty_audit"));
    }

    /** 记忆页:展示玩家偏好 + 顶层情景(可经上方"清空叙事/清空全部"按钮清理)。 */
    private void renderMemory(GuiGraphics guiGraphics, int x, int y, int bottom, AgentStatusSnapshot snapshot) {
        int cursor = y + 24; // 让开顶部清理按钮
        Component emptyHint = Component.translatable("gui.herobrine_companion.agent_status.empty_memory");
        List<String> lines = snapshot.memoryLines();
        if (lines.isEmpty()) {
            guiGraphics.drawString(this.font, emptyHint, x, cursor, COLOR_EMPTY, false);
            return;
        }
        renderLines(guiGraphics, x, cursor, bottom, lines, emptyHint);
    }

    private int renderField(GuiGraphics guiGraphics, int x, int y, Component label, String value) {
        guiGraphics.drawString(this.font, label, x, y, COLOR_LABEL, false);
        guiGraphics.drawString(this.font, clip(value), x + 60, y, COLOR_VALUE, false);
        return y + LINE_HEIGHT + 2;
    }

    private void renderLines(GuiGraphics guiGraphics, int x, int y, int bottom,
                             List<String> lines, Component emptyHint) {
        if (lines.isEmpty()) {
            guiGraphics.drawString(this.font, emptyHint, x, y, COLOR_EMPTY, false);
            return;
        }
        int cursor = y;
        for (String line : lines) {
            if (cursor > bottom - LINE_HEIGHT) {
                guiGraphics.drawString(this.font, "…", x, cursor, COLOR_EMPTY, false);
                return;
            }
            // 日志页单行截断而非折行：优先保证"一屏能看到更多条"。
            guiGraphics.drawString(this.font, clip(line), x, cursor, COLOR_BODY, false);
            cursor += LINE_HEIGHT;
        }
    }

    /** 按面板可用宽度截断单行，超出部分以省略号收尾。 */
    private String clip(String text) {
        int maxWidth = panelW - PADDING * 4;
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, maxWidth - this.font.width("…")) + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
