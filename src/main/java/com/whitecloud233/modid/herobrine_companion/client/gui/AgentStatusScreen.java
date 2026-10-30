package com.whitecloud233.modid.herobrine_companion.client.gui;

import com.whitecloud233.modid.herobrine_companion.client.agent.ClientAgentStatusStore;
import com.whitecloud233.modid.herobrine_companion.client.network.ClientMemoryDigest;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.AgentStatusSnapshot;
import com.whitecloud233.modid.herobrine_companion.network.ClearHeroMemoryPacket;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.RequestAgentStatusPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Agent 状态面板（M5，对应设计文档 4.9"能解释自己"）。
 *
 * <p><b>单一职责</b>：只做渲染与输入。所有数据一律取自 {@link ClientAgentStatusStore}，
 * 本类不采集、不解析、不缓存业务状态——"刷新"只是发一个请求包，数据何时回来由仓储决定。</p>
 *
 * <p>五个页签对应设计文档要求的可观测信息：概览（当前意图 / 上次决策原因）、
 * 任务队列、决策日志、工具审计、记忆。</p>
 *
 * <p>视觉与 {@link HeroScreen} 统一：同一套面板底、标题栏、蓝色强调条、橙色分区标题与
 * 主题按钮，配色全部来自 {@link HeroTheme}，不再在本类里另立一套灰阶。</p>
 */
public class AgentStatusScreen extends HeroPanelScreen {

    /** 自动刷新间隔（tick）：1 秒一次，与 {@code HeroAgent.CYCLE_TICKS} 同频。 */
    private static final int REFRESH_INTERVAL_TICKS = 20;

    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 232;
    private static final int MIN_WIDTH = 280;
    private static final int MIN_HEIGHT = 190;
    private static final int SCREEN_MARGIN = 8;
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 8;
    private static final int TAB_HEIGHT = 16;

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

    /** 面板几何：init/render 时按窗口钳制后写入，供各 helper 使用。 */
    private HeroTheme.Frame frame = new HeroTheme.Frame(0, 0, PANEL_WIDTH, PANEL_HEIGHT);

    public AgentStatusScreen() {
        super(Component.translatable("gui.herobrine_companion.agent_status.title"));
    }

    /** 客户端入口：打开面板并立即请求一次快照。 */
    public static void open() {
        PacketHandler.sendToServer(new RequestAgentStatusPacket());
        Minecraft.getInstance().setScreen(new AgentStatusScreen());
    }

    /** 面板几何：宽高钳制到窗口内，居中。 */
    private HeroTheme.Frame frame() {
        this.frame = HeroTheme.frame(this.width, this.height, PANEL_WIDTH, PANEL_HEIGHT,
                MIN_WIDTH, MIN_HEIGHT, SCREEN_MARGIN);
        return this.frame;
    }

    @Override
    protected void init() {
        super.init();
        HeroTheme.Frame frame = this.frame();

        // 页签按钮：等分内容区宽度，放在底部操作栏里（与 HeroScreen 的底栏同位置）。
        Tab[] tabs = Tab.values();
        int tabsWidth = frame.contentWidth();
        int tabWidth = tabsWidth / tabs.length;
        int tabY = frame.footerButtonY();
        int tabLeft = frame.contentLeft();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            // 最后一个页签吃掉整除余数，避免右侧留下一条缝。
            int thisWidth = (i == tabs.length - 1) ? tabsWidth - tabWidth * (tabs.length - 1) : tabWidth;
            this.addRenderableWidget(new HeroScreen.ThemedButton(
                    tabLeft + i * tabWidth, tabY, thisWidth - 2, TAB_HEIGHT,
                    Component.translatable(tab.titleKey),
                    button -> this.activeTab = tab,
                    null));
        }

        // 记忆页签专属：清理按钮（默认隐藏，切到记忆页才显示）。
        int buttonW = 72;
        int buttonH = 16;
        int buttonY = frame.contentTop() + 4;
        this.clearNarrativeButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                frame.left() + frame.width() - buttonW * 2 - 10, buttonY, buttonW, buttonH,
                Component.translatable("gui.herobrine_companion.agent_status.clear_narrative"),
                button -> clearMemory(true), null));
        this.clearAllButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                frame.left() + frame.width() - buttonW - 6, buttonY, buttonW, buttonH,
                Component.translatable("gui.herobrine_companion.agent_status.clear_all"),
                button -> clearMemory(false), null));
        this.clearNarrativeButton.visible = false;
        this.clearAllButton.visible = false;
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

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        HeroTheme.Frame frame = this.frame();
        AgentStatusSnapshot snapshot = ClientAgentStatusStore.snapshot();

        // 面板底 + 标题栏 + 强调条：与 HeroScreen 同一套画法。
        HeroTheme.panel(guiGraphics, this.font, frame, this.title);
        renderHeader(guiGraphics, frame, snapshot);

        int bodyX = frame.contentLeft() + PADDING;
        int bodyY = frame.contentTop() + 16;
        int bodyBottom = frame.contentBottom() - 2;

        // 记忆页才显示清理按钮；其余页隐藏,避免误触。
        boolean memoryTab = activeTab == Tab.MEMORY;
        this.clearNarrativeButton.visible = memoryTab;
        this.clearAllButton.visible = memoryTab;
        switch (activeTab) {
            case OVERVIEW -> renderOverview(guiGraphics, frame, bodyX, bodyY, snapshot);
            case QUEUE -> renderQueue(guiGraphics, bodyX, bodyY, bodyBottom, snapshot);
            case DECISIONS -> renderLines(guiGraphics, bodyX, bodyY, bodyBottom,
                    snapshot.decisions(), Component.translatable("gui.herobrine_companion.agent_status.empty_decisions"));
            case AUDIT -> renderAudit(guiGraphics, bodyX, bodyY, bodyBottom, snapshot);
            case MEMORY -> renderMemory(guiGraphics, bodyX, bodyY, bodyBottom, snapshot);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics guiGraphics, HeroTheme.Frame frame, AgentStatusSnapshot snapshot) {
        String freshness = ClientAgentStatusStore.hasData()
                ? Component.translatable("gui.herobrine_companion.agent_status.freshness",
                        snapshot.gameTime(), ClientAgentStatusStore.ageSeconds()).getString()
                : Component.translatable("gui.herobrine_companion.agent_status.waiting_data").getString();
        int textWidth = this.font.width(freshness);
        // 新鲜度贴右显示；面板太窄时让位给标题，避免两段文字叠在一起。
        int freshnessX = frame.left() + frame.width() - textWidth - PADDING;
        if (freshnessX > frame.left() + 110) {
            guiGraphics.drawString(this.font, freshness, freshnessX,
                    frame.top() + 9, ClientAgentStatusStore.hasData() ? HeroTheme.INFO : HeroTheme.DISABLED, false);
        }

        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.agent_status.header"),
                frame.contentLeft(), frame.contentTop(), HeroTheme.TEXT, false);
        HeroTheme.divider(guiGraphics, frame.contentLeft(), frame.contentLeft() + frame.contentWidth(),
                frame.contentTop() + 12);
    }

    private void renderOverview(GuiGraphics guiGraphics, HeroTheme.Frame frame, int x, int y, AgentStatusSnapshot snapshot) {
        int cursor = y;
        cursor = renderField(guiGraphics, frame, x, cursor,
                Component.translatable("gui.herobrine_companion.agent_status.field.intention"),
                snapshot.intention());
        cursor = renderField(guiGraphics, frame, x, cursor,
                Component.translatable("gui.herobrine_companion.agent_status.field.mind"),
                snapshot.mindState());
        cursor = renderField(guiGraphics, frame, x, cursor,
                Component.translatable("gui.herobrine_companion.agent_status.field.queue"),
                snapshot.isQueueIdle()
                        ? Component.translatable("gui.herobrine_companion.agent_status.idle").getString()
                        : Component.translatable("gui.herobrine_companion.agent_status.pending_tasks",
                                snapshot.queueSize()).getString());
        cursor = renderField(guiGraphics, frame, x, cursor,
                Component.translatable("gui.herobrine_companion.agent_status.field.memory"),
                Component.translatable("gui.herobrine_companion.agent_status.memory_count",
                        snapshot.memoryEpisodes(), snapshot.memoryFacts()).getString());
        cursor = renderField(guiGraphics, frame, x, cursor,
                Component.translatable("gui.herobrine_companion.agent_status.field.tools"),
                Component.translatable("gui.herobrine_companion.agent_status.tool_count",
                        snapshot.toolCatalog().size()).getString());

        cursor += 4;
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.agent_status.last_decision"),
                x, cursor, HeroTheme.LABEL, false);
        cursor += LINE_HEIGHT + 1;
        // 决策行通常很长，按面板宽度折行，保证因果链完整可读。
        List<FormattedCharSequence> wrapped =
                this.font.split(Component.literal(snapshot.lastDecision()), frame.contentWidth() - PADDING * 3);
        for (FormattedCharSequence line : wrapped) {
            if (cursor > frame.contentBottom()) {
                break;
            }
            guiGraphics.drawString(this.font, line, x + 4, cursor, HeroTheme.TEXT, false);
            cursor += LINE_HEIGHT;
        }
    }

    private void renderQueue(GuiGraphics guiGraphics, int x, int y, int bottom, AgentStatusSnapshot snapshot) {
        int cursor = renderField(guiGraphics, this.frame, x, y,
                Component.translatable("gui.herobrine_companion.agent_status.field.queue_len"),
                snapshot.isQueueIdle()
                        ? Component.translatable("gui.herobrine_companion.agent_status.idle").getString()
                        : String.valueOf(snapshot.queueSize()));
        cursor = renderField(guiGraphics, this.frame, x, cursor,
                Component.translatable("gui.herobrine_companion.agent_status.field.progress"),
                snapshot.queueProgress().isEmpty() ? "—" : snapshot.queueProgress());

        cursor += 4;
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.agent_status.queue_log"),
                x, cursor, HeroTheme.LABEL, false);
        renderLines(guiGraphics, x + 4, cursor + LINE_HEIGHT + 1, bottom,
                snapshot.queueLog(), Component.translatable("gui.herobrine_companion.agent_status.empty_queue_log"));
    }

    /** 审计页：先列"允许做什么"（工具目录），再列"实际做了什么"（审计流水）。 */
    private void renderAudit(GuiGraphics guiGraphics, int x, int y, int bottom, AgentStatusSnapshot snapshot) {
        int cursor = y;
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.agent_status.audit_tools"),
                x, cursor, HeroTheme.LABEL, false);
        cursor += LINE_HEIGHT + 1;
        for (String tool : snapshot.toolCatalog()) {
            if (cursor > bottom - LINE_HEIGHT * 3) {
                break;
            }
            guiGraphics.drawString(this.font, "· " + clip(tool), x + 4, cursor, HeroTheme.VALUE, false);
            cursor += LINE_HEIGHT;
        }

        cursor += 4;
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.agent_status.audit_calls"),
                x, cursor, HeroTheme.LABEL, false);
        renderLines(guiGraphics, x + 4, cursor + LINE_HEIGHT + 1, bottom,
                snapshot.audits(), Component.translatable("gui.herobrine_companion.agent_status.empty_audit"));
    }

    /** 记忆页:展示玩家偏好 + 顶层情景(可经上方"清空叙事/清空全部"按钮清理)。 */
    private void renderMemory(GuiGraphics guiGraphics, int x, int y, int bottom, AgentStatusSnapshot snapshot) {
        int cursor = y + 20; // 让开顶部清理按钮
        Component emptyHint = Component.translatable("gui.herobrine_companion.agent_status.empty_memory");
        List<String> lines = snapshot.memoryLines();
        if (lines.isEmpty()) {
            guiGraphics.drawString(this.font, emptyHint, x, cursor, HeroTheme.DISABLED, false);
            return;
        }
        renderLines(guiGraphics, x, cursor, bottom, lines, emptyHint);
    }

    private int renderField(GuiGraphics guiGraphics, HeroTheme.Frame frame, int x, int y, Component label, String value) {
        guiGraphics.drawString(this.font, label, x, y, HeroTheme.LABEL, false);
        int valueX = x + labelWidth(frame);
        guiGraphics.drawString(this.font, clip(value), valueX, y, HeroTheme.TEXT, false);
        return y + LINE_HEIGHT + 2;
    }

    /** 值列起点：取几个固定标签里最宽的那个，保证同一页内冒号对齐。 */
    private int labelWidth(HeroTheme.Frame frame) {
        int widest = 0;
        for (String key : new String[]{
                "gui.herobrine_companion.agent_status.field.intention",
                "gui.herobrine_companion.agent_status.field.mind",
                "gui.herobrine_companion.agent_status.field.queue",
                "gui.herobrine_companion.agent_status.field.memory",
                "gui.herobrine_companion.agent_status.field.tools",
                "gui.herobrine_companion.agent_status.field.queue_len",
                "gui.herobrine_companion.agent_status.field.progress"}) {
            widest = Math.max(widest, this.font.width(Component.translatable(key).getString()));
        }
        // 不超过内容区 40%，否则窄窗口下值列会被挤没。
        return Math.min(widest + 6, Math.max(40, frame.contentWidth() * 2 / 5));
    }

    private void renderLines(GuiGraphics guiGraphics, int x, int y, int bottom,
                             List<String> lines, Component emptyHint) {
        if (lines.isEmpty()) {
            guiGraphics.drawString(this.font, emptyHint, x, y, HeroTheme.DISABLED, false);
            return;
        }
        int cursor = y;
        for (String line : lines) {
            if (cursor > bottom - LINE_HEIGHT) {
                guiGraphics.drawString(this.font, "…", x, cursor, HeroTheme.DISABLED, false);
                return;
            }
            // 日志页单行截断而非折行：优先保证"一屏能看到更多条"。
            guiGraphics.drawString(this.font, clip(line), x, cursor, HeroTheme.TEXT, false);
            cursor += LINE_HEIGHT;
        }
    }

    /** 按面板可用宽度截断单行，超出部分以省略号收尾。 */
    private String clip(String text) {
        int maxWidth = Math.max(1, this.frame.contentWidth() - PADDING * 4);
        return HeroTheme.clip(this.font, text, maxWidth);
    }

}
