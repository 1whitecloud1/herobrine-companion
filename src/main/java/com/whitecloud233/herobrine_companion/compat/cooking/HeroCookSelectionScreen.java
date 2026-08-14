package com.whitecloud233.herobrine_companion.compat.cooking;

import com.whitecloud233.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
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

public class HeroCookSelectionScreen extends Screen {
    private static final int PANEL_WIDTH = 248;
    private static final int PANEL_HEIGHT = 212;
    private static final int COL_BG = 0xEE1F1A17;
    private static final int COL_BORDER = 0xFF8B6A3D;
    private static final int COL_TEXT = 0xFFF3E6C8;
    private static final int COL_SUBTEXT = 0xFFBCA47B;

    private final int heroId;
    private final BlockPos cookwarePos;
    private final List<HeroCookingCompat.CookOptionView> options;
    private final Screen previousScreen;
    private CookOptionList optionList;
    private int repeatCount = HeroCookingCompat.MIN_COOK_REPEAT_COUNT;

    public HeroCookSelectionScreen(int heroId, BlockPos cookwarePos,
                                   List<HeroCookingCompat.CookOptionView> options,
                                   Screen previousScreen) {
        super(Component.translatable("gui.herobrine_companion.cook_select_title"));
        this.heroId = heroId;
        this.cookwarePos = cookwarePos;
        this.options = List.copyOf(options);
        this.previousScreen = previousScreen;
    }

    /** 面板几何：宽高钳制到窗口内，居中。返回 {left, top, w, h}。 */
    private int[] panelDims() {
        int w = Math.min(PANEL_WIDTH, Math.max(220, this.width - 16));
        int h = Math.min(PANEL_HEIGHT, Math.max(180, this.height - 16));
        return new int[]{(this.width - w) / 2, Math.max(4, (this.height - h) / 2), w, h};
    }

    @Override
    protected void init() {
        super.init();
        int[] dims = panelDims();
        int left = dims[0];
        int top = dims[1];
        int panelW = dims[2];
        int panelH = dims[3];

        this.optionList = new CookOptionList(this.minecraft, panelW - 16, panelH - 90, top + 34, 28);
        this.optionList.setX(left + 8);
        this.addRenderableWidget(this.optionList);
        this.refreshOptionList();

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + panelW - 72, top + panelH - 48, 28, 16,
                Component.literal("-"),
                button -> this.adjustRepeatCount(-1),
                null
        ));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + panelW - 40, top + panelH - 48, 28, 16,
                Component.literal("+"),
                button -> this.adjustRepeatCount(1),
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
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int[] dims = panelDims();
        int left = dims[0];
        int top = dims[1];
        int panelW = dims[2];
        int panelH = dims[3];
        guiGraphics.fill(left, top, left + panelW, top + panelH, COL_BG);
        guiGraphics.fill(left, top, left + panelW, top + 1, COL_BORDER);
        guiGraphics.fill(left, top + panelH - 1, left + panelW, top + panelH, COL_BORDER);
        guiGraphics.fill(left, top, left + 1, top + panelH, COL_BORDER);
        guiGraphics.fill(left + panelW - 1, top, left + panelW, top + panelH, COL_BORDER);

        guiGraphics.drawString(this.font, this.title, left + 10, top + 8, COL_TEXT, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.cook_select_hint"),
                left + 10, top + 18, COL_SUBTEXT, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.cook_select_count", this.repeatCount),
                left + 10, top + panelH - 44, COL_TEXT, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.cook_select_count_hint"),
                left + 10, top + panelH - 32, COL_SUBTEXT, false);
        if (this.optionList != null && this.optionList.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.herobrine_companion.cook_select_none_for_count", this.repeatCount),
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

    private void adjustRepeatCount(int delta) {
        this.repeatCount = Mth.clamp(this.repeatCount + delta,
                HeroCookingCompat.MIN_COOK_REPEAT_COUNT,
                HeroCookingCompat.MAX_COOK_REPEAT_COUNT);
        this.refreshOptionList();
    }

    private void submit(ResourceLocation recipeId) {
        PacketHandler.sendToServer(new SelectCookOptionPacket(this.heroId, this.cookwarePos, recipeId, this.repeatCount));
        this.onClose();
    }

    private void refreshOptionList() {
        if (this.optionList == null) {
            return;
        }

        this.optionList.rebuild(this.options, this.repeatCount);
    }

    private final class CookOptionList extends ObjectSelectionList<CookOptionEntry> {
        private CookOptionList(Minecraft minecraft, int width, int height, int top, int itemHeight) {
            super(minecraft, width, height, top, itemHeight);
            this.setRenderHeader(false, 0);
        }

        @Override
        public int getRowWidth() {
            return this.width - 6;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getX() + this.width - 6;
        }

        private void addOption(CookOptionEntry entry) {
            this.addEntry(entry);
        }

        private void rebuild(List<HeroCookingCompat.CookOptionView> options, int repeatCount) {
            this.clearEntries();
            for (HeroCookingCompat.CookOptionView option : options) {
                if (option.maxRepeatCount() >= repeatCount) {
                    this.addOption(new CookOptionEntry(option));
                }
            }
            this.setScrollAmount(0.0D);
        }

        private boolean isEmpty() {
            return this.getItemCount() == 0;
        }
    }

    private final class CookOptionEntry extends ObjectSelectionList.Entry<CookOptionEntry> {
        private final HeroCookingCompat.CookOptionView option;

        private CookOptionEntry(HeroCookingCompat.CookOptionView option) {
            this.option = option;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            int bg = hovered ? 0xAA5B4630 : 0x6631261B;
            guiGraphics.fill(left, top, left + width, top + height - 2, bg);
            guiGraphics.fill(left, top, left + 1, top + height - 2, COL_BORDER);

            ItemStack result = this.option.result();
            guiGraphics.renderItem(result, left + 6, top + 5);
            guiGraphics.renderItemDecorations(HeroCookSelectionScreen.this.font, result, left + 6, top + 5);

            Component name = result.getHoverName();
            guiGraphics.drawString(HeroCookSelectionScreen.this.font, name, left + 28, top + 6, COL_TEXT, false);
            guiGraphics.drawString(HeroCookSelectionScreen.this.font,
                    Component.translatable("gui.herobrine_companion.cook_select_recipe_limit", this.option.maxRepeatCount()),
                    left + 28, top + 16, COL_SUBTEXT, false);

            if (mouseX >= left + 6 && mouseX <= left + 22 && mouseY >= top + 5 && mouseY <= top + 21) {
                guiGraphics.renderTooltip(HeroCookSelectionScreen.this.font, result, mouseX, mouseY);
            }
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
