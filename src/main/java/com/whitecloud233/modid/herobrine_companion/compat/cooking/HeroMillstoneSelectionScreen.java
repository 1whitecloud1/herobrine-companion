package com.whitecloud233.modid.herobrine_companion.compat.cooking;

import com.whitecloud233.modid.herobrine_companion.client.gui.HeroTheme;
import com.whitecloud233.modid.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingRules;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.MillstoneOption;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 石磨界面：先设定要磨几份（石磨一次最多吃 8 个），再点材料把「投入 / 产出」一并指派给 Herobrine。
 *
 * <p>与做菜界面同一套视觉；差别是石磨吃的是背包里的原料，所以每个条目同时显示原料与产物，
 * 可磨份数由服务端按背包实际数量算好后发过来。
 */
public class HeroMillstoneSelectionScreen extends Screen {
    private static final int PANEL_WIDTH = 264;
    private static final int PANEL_HEIGHT = 212;
    private static final int COL_BG = 0xEE1F1A17;
    private static final int COL_BORDER = 0xFF8B6A3D;
    private static final int COL_TEXT = 0xFFF3E6C8;
    private static final int COL_SUBTEXT = 0xFFBCA47B;
    private static final int COL_LIST_BG = 0x44000000;
    private static final int COL_LIST_BORDER = 0xFF5C4730;

    private final int heroId;
    private final BlockPos millstonePos;
    private final List<MillstoneOption> options;
    private final Screen previousScreen;
    private MillstoneOptionList optionList;
    private int count = CookingRules.MIN_COOK_REPEAT_COUNT;

    public HeroMillstoneSelectionScreen(int heroId, BlockPos millstonePos,
                                        List<MillstoneOption> options,
                                        Screen previousScreen) {
        super(Component.translatable("gui.herobrine_companion.millstone_select_title"));
        this.heroId = heroId;
        this.millstonePos = millstonePos;
        this.options = List.copyOf(options);
        this.previousScreen = previousScreen;
    }

    /** 面板几何：宽高钳制到窗口内，居中。返回 {left, top, w, h}。 */
    private int[] panelDims() {
        HeroTheme.Frame frame = HeroTheme.frame(this.width, this.height,
                PANEL_WIDTH, PANEL_HEIGHT, 220, 180, 8);
        return new int[]{frame.left(), frame.top(), frame.width(), frame.height()};
    }

    @Override
    protected void init() {
        super.init();
        int[] dims = panelDims();
        int left = dims[0];
        int top = dims[1];
        int panelW = dims[2];
        int panelH = dims[3];

        this.optionList = new MillstoneOptionList(this.minecraft, panelW - 16, panelH - 90, top + 34, 30);
        this.optionList.setLeftPos(left + 8);
        this.addRenderableWidget(this.optionList);
        this.refreshOptionList();

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + panelW - 72, top + panelH - 48, 28, 16,
                Component.literal("-"),
                button -> this.adjustCount(-1),
                null
        ));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + panelW - 40, top + panelH - 48, 28, 16,
                Component.literal("+"),
                button -> this.adjustCount(1),
                null
        ));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + panelW - 72, top + panelH - 24, 64, 16,
                Component.translatable("gui.herobrine_companion.back"),
                button -> this.onClose(),
                null
        ));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int[] dims = panelDims();
        int left = dims[0];
        int top = dims[1];
        int panelW = dims[2];
        int panelH = dims[3];
        guiGraphics.fill(left, top, left + panelW, top + panelH, COL_BG);
        guiGraphics.renderOutline(left, top, panelW, panelH, COL_BORDER);

        if (this.optionList != null) {
            int listLeft = this.optionList.getLeft();
            int listTop = this.optionList.getTop();
            int listBottom = this.optionList.getBottom();
            guiGraphics.fill(listLeft, listTop, listLeft + this.optionList.getWidth(), listBottom, COL_LIST_BG);
            guiGraphics.renderOutline(listLeft, listTop, this.optionList.getWidth(), listBottom - listTop, COL_LIST_BORDER);
        }

        guiGraphics.drawString(this.font, this.title, left + 10, top + 8, COL_TEXT, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.millstone_select_hint"),
                left + 10, top + 18, COL_SUBTEXT, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.millstone_select_count", this.count),
                left + 10, top + panelH - 44, COL_TEXT, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.millstone_select_count_hint"),
                left + 10, top + panelH - 32, COL_SUBTEXT, false);
        if (this.optionList != null && this.optionList.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.herobrine_companion.millstone_select_none"),
                    this.width / 2,
                    top + 92,
                    COL_TEXT);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.previousScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void adjustCount(int delta) {
        this.count = Mth.clamp(this.count + delta,
                CookingRules.MIN_COOK_REPEAT_COUNT,
                CookingRules.MAX_COOK_REPEAT_COUNT);
        this.refreshOptionList();
    }

    private void submit(ResourceLocation recipeId) {
        PacketHandler.sendToServer(new SelectMillstoneOptionPacket(this.heroId, this.millstonePos, recipeId, this.count));
        this.onClose();
    }

    private void refreshOptionList() {
        if (this.optionList == null) {
            return;
        }
        this.optionList.rebuild(this.options, this.count);
    }

    private final class MillstoneOptionList extends ObjectSelectionList<MillstoneOptionEntry> {
        private MillstoneOptionList(Minecraft minecraft, int width, int height, int top, int itemHeight) {
            super(minecraft, width, height, top, top + height, itemHeight);
            this.setRenderHeader(false, 0);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
        }

        @Override
        public int getRowWidth() {
            return this.width - 6;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getLeft() + this.width - 6;
        }

        private void rebuild(List<MillstoneOption> options, int count) {
            this.clearEntries();
            for (MillstoneOption option : options) {
                // 可磨份数不足以覆盖玩家选的份数时不给选，避免派活后中途没料。
                if (option.maxCount() >= count) {
                    this.addEntry(new MillstoneOptionEntry(option));
                }
            }
            this.setScrollAmount(0.0D);
        }

        private boolean isEmpty() {
            return this.getItemCount() == 0;
        }
    }

    private final class MillstoneOptionEntry extends ObjectSelectionList.Entry<MillstoneOptionEntry> {
        private final MillstoneOption option;

        private MillstoneOptionEntry(MillstoneOption option) {
            this.option = option;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            int bg = hovered ? 0xAA5B4630 : 0x6631261B;
            guiGraphics.fill(left, top, left + width, top + height - 2, bg);
            guiGraphics.fill(left, top, left + 1, top + height - 2, COL_BORDER);

            ItemStack input = this.option.inputExample();
            ItemStack result = this.option.result();
            guiGraphics.renderItem(input, left + 5, top + 6);
            guiGraphics.renderItemDecorations(HeroMillstoneSelectionScreen.this.font, input, left + 5, top + 6);

            guiGraphics.drawString(HeroMillstoneSelectionScreen.this.font,
                    Component.literal("→"), left + 27, top + 10, COL_SUBTEXT, false);

            guiGraphics.renderItem(result, left + 40, top + 6);
            guiGraphics.renderItemDecorations(HeroMillstoneSelectionScreen.this.font, result, left + 40, top + 6);

            guiGraphics.drawString(HeroMillstoneSelectionScreen.this.font, result.getHoverName(),
                    left + 62, top + 5, COL_TEXT, false);
            guiGraphics.drawString(HeroMillstoneSelectionScreen.this.font,
                    Component.translatable("gui.herobrine_companion.millstone_select_available",
                            input.getHoverName(), this.option.maxCount()),
                    left + 62, top + 16, COL_SUBTEXT, false);

            if (isOverIcon(mouseX, mouseY, left + 5, top + 6)) {
                guiGraphics.renderTooltip(HeroMillstoneSelectionScreen.this.font, input, mouseX, mouseY);
            } else if (isOverIcon(mouseX, mouseY, left + 40, top + 6)) {
                guiGraphics.renderTooltip(HeroMillstoneSelectionScreen.this.font, result, mouseX, mouseY);
            }
        }

        private boolean isOverIcon(int mouseX, int mouseY, int iconX, int iconY) {
            return mouseX >= iconX && mouseX <= iconX + 16 && mouseY >= iconY && mouseY <= iconY + 16;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                submit(this.option.recipeId());
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return this.option.result().getHoverName();
        }
    }
}
