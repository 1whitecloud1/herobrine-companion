package com.whitecloud233.herobrine_companion.config;

import com.whitecloud233.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.herobrine_companion.client.gui.HeroTheme;
import com.whitecloud233.herobrine_companion.client.llm.LlmTask;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.whitecloud233.herobrine_companion.client.gui.HeroPanelScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 任务路由页（单一职责：为每个 LLM 请求类型分配主 provider 与备用 provider）。
 * 空值 = 跟随全局激活 provider / 无备用。从 API Key 页进入，保存写回 LLMConfig。
 *
 * <p>7 个任务在矮窗口下放不进一屏，因此行区域做成可滚动（滚轮 / 拖拽滑块 / 点击轨道），
 * 底部保存与返回键固定在面板底栏，任何窗口高度都够得着。视觉沿用
 * {@link HeroTheme}，与 HeroScreen 一致。</p>
 */
public class LLMRoutingScreen extends HeroPanelScreen {
    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 420;
    private static final int MIN_WIDTH = 300;
    private static final int MIN_HEIGHT = 200;
    private static final int SCREEN_MARGIN = 8;
    private static final int BUTTON_HEIGHT = 18;
    private static final int ROW_HEIGHT = 26;
    private static final int LABEL_WIDTH = 140;
    private static final int CONTROL_GAP = 8;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int SCROLLBAR_MIN_THUMB = 16;
    private static final int HEADER_HEIGHT = 16;

    /** 每行两个按钮：主 / 备用。渲染与命中都用这份几何，避免两处各算一遍算错。 */
    private record Row(int y, LlmTask task, Button primary, Button fallback) {
    }

    private final Screen lastScreen;
    private final Map<LlmTask, LLMConfig.TaskRoute> routeDrafts = new EnumMap<>(LlmTask.class);
    private final List<Row> rows = new ArrayList<>();

    private HeroTheme.Frame frame = new HeroTheme.Frame(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
    private int rowsTop;
    private int rowsViewportHeight;
    private int scrollOffset;
    private int maxScroll;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;

    public LLMRoutingScreen(Screen lastScreen) {
        super(Component.translatable("gui.herobrine_companion.routing.title"));
        this.lastScreen = lastScreen;
    }

    private HeroTheme.Frame frame() {
        this.frame = HeroTheme.frame(this.width, this.height, PANEL_WIDTH, PANEL_HEIGHT,
                MIN_WIDTH, MIN_HEIGHT, SCREEN_MARGIN);
        return this.frame;
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
        this.rebuildRows();
    }

    /**
     * 按当前窗口重建行控件。
     *
     * <p>行是可滚动的，所以控件位置依赖 scrollOffset；滚动时也要重建/重排，
     * 否则按钮会和文字错位。这里统一从布局推一遍，保证两者永远同源。</p>
     */
    private void rebuildRows() {
        this.clearWidgets();
        this.rows.clear();
        HeroTheme.Frame frame = this.frame();

        int contentLeft = frame.contentLeft();
        int contentWidth = frame.contentWidth();
        int labelWidth = Math.min(LABEL_WIDTH, Math.max(70, contentWidth * 35 / 100));
        int controlsWidth = Math.max(1, contentWidth - labelWidth - CONTROL_GAP - SCROLLBAR_WIDTH - 2);
        int buttonWidth = Math.max(1, (controlsWidth - CONTROL_GAP) / 2);
        int primaryX = contentLeft + labelWidth + CONTROL_GAP;
        int fallbackX = primaryX + buttonWidth + CONTROL_GAP;

        this.rowsTop = frame.contentTop() + HEADER_HEIGHT + 4;
        // 底部操作栏之上留出滚动条/间距，行区域吃掉剩下的高度。
        int rowsBottom = frame.contentBottom() - BUTTON_HEIGHT - 12;
        int availableHeight = Math.max(ROW_HEIGHT, rowsBottom - this.rowsTop);
        // 视口高度对齐到"整行"的整数倍：这样滚到任意位置都不会出现半截行，
        // 也就不需要 scissor 去裁行内按钮（裁剪曾经把底栏按钮一起裁没，见 render）。
        int visibleRows = Math.max(1, availableHeight / ROW_HEIGHT);
        this.rowsViewportHeight = visibleRows * ROW_HEIGHT;

        int totalHeight = LlmTask.values().length * ROW_HEIGHT;
        this.maxScroll = Math.max(0, totalHeight - this.rowsViewportHeight);
        this.scrollOffset = Math.max(0, Math.min(this.maxScroll, this.scrollOffset));

        int rowIndex = 0;
        for (LlmTask task : LlmTask.values()) {
            int rowY = this.rowsTop + rowIndex * ROW_HEIGHT - this.scrollOffset;
            final LlmTask capturedTask = task;
            Button primary = this.addRenderableWidget(new HeroScreen.ThemedButton(
                    primaryX, rowY + 4, buttonWidth, BUTTON_HEIGHT,
                    this.getPrimaryButtonMessage(capturedTask),
                    button -> {
                        this.cyclePrimary(capturedTask);
                        button.setMessage(this.getPrimaryButtonMessage(capturedTask));
                    },
                    null));
            Button fallback = this.addRenderableWidget(new HeroScreen.ThemedButton(
                    fallbackX, rowY + 4, buttonWidth, BUTTON_HEIGHT,
                    this.getFallbackButtonMessage(capturedTask),
                    button -> {
                        this.cycleFallback(capturedTask);
                        button.setMessage(this.getFallbackButtonMessage(capturedTask));
                    },
                    null));
            this.rows.add(new Row(rowY, task, primary, fallback));
            rowIndex++;
        }

        // 底部固定操作栏：保存 / 返回。
        int actionWidth = Math.min(120, Math.max(1, (contentWidth - CONTROL_GAP) / 2));
        int groupWidth = actionWidth * 2 + CONTROL_GAP;
        int saveX = frame.left() + (frame.width() - groupWidth) / 2;
        int actionY = frame.contentBottom() - BUTTON_HEIGHT;
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                saveX, actionY, actionWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.api_setup.confirm_save"),
                button -> this.saveAndClose(), null));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                saveX + actionWidth + CONTROL_GAP, actionY, actionWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.back"),
                button -> Minecraft.getInstance().setScreen(this.lastScreen), null));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        HeroTheme.Frame frame = this.frame();
        HeroTheme.panel(guiGraphics, this.font, frame, this.title);

        int contentLeft = frame.contentLeft();
        int contentWidth = frame.contentWidth();
        int labelWidth = Math.min(LABEL_WIDTH, Math.max(70, contentWidth * 35 / 100));
        int controlsWidth = Math.max(1, contentWidth - labelWidth - CONTROL_GAP - SCROLLBAR_WIDTH - 2);
        int buttonWidth = Math.max(1, (controlsWidth - CONTROL_GAP) / 2);
        int primaryX = contentLeft + labelWidth + CONTROL_GAP;
        int fallbackX = primaryX + buttonWidth + CONTROL_GAP;

        // 表头（列名），固定在行区域上方不随滚动移动。
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.routing.task_header"),
                contentLeft, frame.contentTop(), HeroTheme.INFO, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.routing.primary"),
                primaryX, frame.contentTop(), HeroTheme.LABEL, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.routing.fallback"),
                fallbackX, frame.contentTop(), HeroTheme.LABEL, false);
        HeroTheme.divider(guiGraphics, contentLeft, contentLeft + contentWidth, frame.contentTop() + 11);

        // 行视口按"整行"对齐（见 rebuildRows），因此不存在半截行：
        // 行内按钮要么完整可见，要么完全滚出视口后隐藏，不需要 scissor 裁剪。
        //
        // 这里刻意不用 scissor：裁剪只影响绘制、不影响鼠标命中，一旦底栏按钮
        // 落进裁剪框就会出现"保存/返回看不见却还能点"（本页真实踩过的坑）。
        // 现行做法靠"整行显隐 + 视口整行对齐"就能保证不重叠，也就不用承担这个风险。
        for (Row row : this.rows) {
            int buttonTop = row.y() + 4;
            boolean intersects = buttonTop < this.rowsTop + this.rowsViewportHeight
                    && buttonTop + BUTTON_HEIGHT > this.rowsTop;
            row.primary().visible = intersects;
            row.fallback().visible = intersects;
            if (!intersects) {
                continue;
            }
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.herobrine_companion.routing.task."
                            + row.task().name().toLowerCase(Locale.ROOT)),
                    contentLeft, row.y() + 9, HeroTheme.TEXT, false);
        }

        // 一行渲染：行内按钮与底栏按钮都由控件系统按自身可见性绘制，
        // 没有任何裁剪状态会影响底栏。
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        this.renderScrollbar(guiGraphics, contentLeft + contentWidth - SCROLLBAR_WIDTH);
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int trackX) {
        if (this.maxScroll <= 0) {
            return;
        }
        int thumbHeight = Math.max(SCROLLBAR_MIN_THUMB,
                (int) ((long) this.rowsViewportHeight * this.rowsViewportHeight
                        / (this.rowsViewportHeight + this.maxScroll)));
        int travel = this.rowsViewportHeight - thumbHeight;
        int thumbY = this.rowsTop + (travel <= 0 ? 0 : (int) ((long) travel * this.scrollOffset / this.maxScroll));
        HeroTheme.scrollbar(guiGraphics, trackX, this.rowsTop, this.rowsViewportHeight,
                thumbY, thumbHeight, this.draggingScrollbar);
    }

    /** 滚动条几何：{trackX, trackY, trackHeight, thumbY, thumbHeight}。 */
    private int[] scrollbarMetrics() {
        HeroTheme.Frame frame = this.frame();
        int trackX = frame.contentLeft() + frame.contentWidth() - SCROLLBAR_WIDTH;
        if (this.maxScroll <= 0) {
            return new int[]{trackX, this.rowsTop, this.rowsViewportHeight, -1, 0};
        }
        int thumbHeight = Math.max(SCROLLBAR_MIN_THUMB,
                (int) ((long) this.rowsViewportHeight * this.rowsViewportHeight
                        / (this.rowsViewportHeight + this.maxScroll)));
        int travel = this.rowsViewportHeight - thumbHeight;
        int thumbY = this.rowsTop + (travel <= 0 ? 0 : (int) ((long) travel * this.scrollOffset / this.maxScroll));
        return new int[]{trackX, this.rowsTop, this.rowsViewportHeight, thumbY, thumbHeight};
    }

    private void scrollTo(int offset) {
        int clamped = Math.max(0, Math.min(this.maxScroll, offset));
        if (clamped == this.scrollOffset) {
            return;
        }
        this.scrollOffset = clamped;
        this.rebuildRows();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.maxScroll > 0) {
            this.scrollTo(this.scrollOffset - (int) Math.signum(verticalAmount) * ROW_HEIGHT);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.maxScroll > 0) {
            int[] metrics = this.scrollbarMetrics();
            int trackX = metrics[0];
            if (mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
                    && mouseY >= metrics[1] && mouseY < metrics[1] + metrics[2]) {
                int thumbY = metrics[3];
                int thumbHeight = metrics[4];
                if (mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
                    this.draggingScrollbar = true;
                    this.scrollbarGrabOffset = mouseY - thumbY;
                } else {
                    this.scrollbarGrabOffset = thumbHeight / 2.0D;
                    this.jumpToThumb(mouseY - this.scrollbarGrabOffset);
                    this.draggingScrollbar = true;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScrollbar && button == 0) {
            this.jumpToThumb(mouseY - this.scrollbarGrabOffset);
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
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void jumpToThumb(double thumbTop) {
        int[] metrics = this.scrollbarMetrics();
        int travel = metrics[2] - metrics[4];
        if (travel <= 0) {
            this.scrollTo(0);
            return;
        }
        int offset = (int) Math.round((thumbTop - metrics[1]) * this.maxScroll / (double) travel);
        this.scrollTo(offset);
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
        LLMConfig.TaskRoute route = this.routeDrafts.get(task);
        if (route != null && LLMConfig.LOCAL_ROUTE_ID.equalsIgnoreCase(route.providerId())) {
            return Component.translatable("gui.herobrine_companion.llm_settings.local_model_button");
        }
        LLMConfig.Provider provider = this.primaryProvider(task);
        return provider == null
                ? Component.translatable("gui.herobrine_companion.routing.default")
                : this.providerName(provider);
    }

    private Component getFallbackButtonMessage(LlmTask task) {
        LLMConfig.TaskRoute route = this.routeDrafts.get(task);
        if (route != null && LLMConfig.LOCAL_ROUTE_ID.equalsIgnoreCase(route.fallbackProviderId())) {
            return Component.translatable("gui.herobrine_companion.llm_settings.local_model_button");
        }
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
        LLMConfig.TaskRoute route = this.routeDrafts.get(task);
        String current = route == null ? "" : route.providerId();
        this.routeDrafts.put(task, new LLMConfig.TaskRoute(
                cycleProviderId(current), route == null ? "" : route.fallbackProviderId()));
    }

    private void cycleFallback(LlmTask task) {
        LLMConfig.TaskRoute route = this.routeDrafts.get(task);
        String current = route == null ? "" : route.fallbackProviderId();
        this.routeDrafts.put(task, new LLMConfig.TaskRoute(
                route == null ? "" : route.providerId(), cycleProviderId(current)));
    }

    private static String cycleProviderId(String current) {
        if (LLMConfig.LOCAL_ROUTE_ID.equalsIgnoreCase(current)) return "";
        LLMConfig.Provider next = cycleProvider(LLMConfig.Provider.fromSavedValue(current));
        if (next != null) return next.getId();
        return LLMConfig.isLocalModelConnected() ? LLMConfig.LOCAL_ROUTE_ID : "";
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
