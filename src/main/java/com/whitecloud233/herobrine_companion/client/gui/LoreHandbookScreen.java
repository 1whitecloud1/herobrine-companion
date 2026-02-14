package com.whitecloud233.herobrine_companion.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

public class LoreHandbookScreen extends Screen {
    // 使用原版书本材质
    private static final ResourceLocation BOOK_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/book.png");
    private final ItemStack handbook;
    private final List<String> unlockedFragments = new ArrayList<>();
    
    // 状态控制
    private String currentFragmentId = null; // 如果不为null，则处于阅读模式
    private int currentPage = 0;
    private List<FormattedCharSequence> cachedPageLines = new ArrayList<>();
    private int totalPages = 0;

    // 布局常量
    private static final int WIDTH = 192;
    private static final int HEIGHT = 192;
    private static final int TEXT_WIDTH = 114;
    
    // 颜色常量 (ARGB)
    private static final int TEXT_COLOR = 0xFF000000; // 不透明黑色
    private static final int HOVER_COLOR = 0xFFA0522D; // 悬停颜色 (赭石色/深褐色)
    private static final int LOCKED_COLOR = 0xFF888888; // 未解锁颜色 (灰色)

    public LoreHandbookScreen(ItemStack handbook) {
        super(GameNarrator.NO_TITLE);
        this.handbook = handbook;
        loadUnlockedFragments();
    }

    private void loadUnlockedFragments() {
        CustomData customData = handbook.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("collected_fragments", 9)) {
                ListTag list = tag.getList("collected_fragments", 8);
                for (int i = 0; i < list.size(); i++) {
                    unlockedFragments.add(list.getString(i));
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        int leftPos = (this.width - WIDTH) / 2;
        int topPos = (this.height - HEIGHT) / 2;

        if (currentFragmentId == null) {
            initIndex(leftPos, topPos);
        } else {
            initReading(leftPos, topPos);
        }
    }

    private void initIndex(int leftPos, int topPos) {
        // 列表
        int startY = topPos + 40;
        int buttonHeight = 14; // 稍微增加高度
        
        int itemsPerPage = 6;
        int indexStart = currentPage * itemsPerPage;
        int indexEnd = Math.min(indexStart + itemsPerPage, 11);

        for (int i = indexStart; i < indexEnd; i++) {
            int fragmentNum = i + 1;
            String id = "fragment_" + fragmentNum;
            boolean unlocked = unlockedFragments.contains(id);
            
            String titleKey = "lore.herobrine_companion." + id + ".title";
            Component btnText = unlocked ? Component.translatable(titleKey) : Component.literal("??? (未解锁)");
            
            // 截断过长的标题
            if (unlocked && btnText.getString().length() > 15) {
                btnText = Component.literal(btnText.getString().substring(0, 14) + "...");
            }

            // 使用自定义的 BookTextButton
            BookTextButton btn = new BookTextButton(leftPos + 36, startY + (i - indexStart) * (buttonHeight + 4), 116, buttonHeight, btnText, (button) -> {
                if (unlocked) {
                    openFragment(id);
                }
            }, unlocked);
            
            this.addRenderableWidget(btn);
        }

        // 目录翻页按钮
        if (currentPage > 0) {
            this.addRenderableWidget(new BookPageButton(leftPos + 38, topPos + 154, false, (button) -> {
                this.currentPage--;
                this.init();
            }, true));
        }
        
        if (indexEnd < 11) {
            this.addRenderableWidget(new BookPageButton(leftPos + 120, topPos + 154, true, (button) -> {
                this.currentPage++;
                this.init();
            }, true));
        }
    }

    private void initReading(int leftPos, int topPos) {
        // 返回按钮 (使用文字按钮) - 稍微左移
        this.addRenderableWidget(new BookTextButton(leftPos + 30, topPos + 160, 50, 14, Component.translatable("gui.herobrine_companion.back"), (button) -> {
            this.currentFragmentId = null;
            this.currentPage = 0;
            this.cachedPageLines.clear();
            this.init();
        }, true));

        // 翻页按钮 (阅读模式) - 稍微右移
        if (this.totalPages > 1) {
            if (this.currentPage > 0) {
                // 修复：将上一页按钮右移，避免与返回按钮重叠
                this.addRenderableWidget(new BookPageButton(leftPos + 120, topPos + 160, false, (button) -> {
                    this.currentPage--;
                    this.init(); // 重新初始化以刷新页面内容和按钮状态
                }, true));
            }

            if (this.currentPage < this.totalPages - 1) {
                // 修复：将下一页按钮右移
                this.addRenderableWidget(new BookPageButton(leftPos + 145, topPos + 160, true, (button) -> {
                    this.currentPage++;
                    this.init(); // 重新初始化以刷新页面内容和按钮状态
                }, true));
            }
        }
    }

    private void openFragment(String id) {
        this.currentFragmentId = id;
        this.currentPage = 0;
        
        String bodyKey = "lore.herobrine_companion." + id + ".body";
        String titleKey = "lore.herobrine_companion." + id + ".title";
        
        // 标题
        Component title = Component.translatable(titleKey).withStyle(style -> style.withBold(true).withColor(TEXT_COLOR));
        // 正文
        Component body = Component.translatable(bodyKey).withStyle(style -> style.withColor(TEXT_COLOR));

        this.cachedPageLines = new ArrayList<>();
        
        // 添加标题行
        this.cachedPageLines.addAll(this.font.split(title, TEXT_WIDTH));
        this.cachedPageLines.add(FormattedCharSequence.EMPTY); // 空行
        
        // 添加正文行
        this.cachedPageLines.addAll(this.font.split(body, TEXT_WIDTH));
        
        // 计算总页数
        int linesPerPage = 14;
        this.totalPages = Mth.ceil((float) this.cachedPageLines.size() / linesPerPage);
        
        // 重新初始化以显示翻页按钮
        this.init();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 留空
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableDepthTest();
        
        int leftPos = (this.width - WIDTH) / 2;
        int topPos = (this.height - HEIGHT) / 2;
        
        // 绘制书本背景
        guiGraphics.blit(BOOK_LOCATION, leftPos, topPos, 0, 0, WIDTH, HEIGHT);

        if (currentFragmentId != null) {
            renderReading(guiGraphics, leftPos, topPos);
        } else {
            // 目录页标题
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.lore_handbook_title"), leftPos + WIDTH / 2, topPos + 16 + 5, TEXT_COLOR);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderReading(GuiGraphics guiGraphics, int leftPos, int topPos) {
        if (currentFragmentId == null || cachedPageLines.isEmpty()) return;

        // 页码 (移到顶部)
        String pageStr = (this.currentPage + 1) + " / " + this.totalPages;
        // 调整页码位置：顶部居中，稍微偏上一点
        guiGraphics.drawCenteredString(this.font, pageStr, leftPos + WIDTH / 2, topPos + 16, TEXT_COLOR);

        int linesPerPage = 14; 
        
        // 确保当前页不越界
        this.currentPage = Mth.clamp(this.currentPage, 0, Math.max(0, this.totalPages - 1));

        int startLine = this.currentPage * linesPerPage;
        int endLine = Math.min(startLine + linesPerPage, cachedPageLines.size());

        // 调整正文起始位置，给顶部页码留出空间
        int yOffset = topPos + 30; 
        for (int i = startLine; i < endLine; i++) {
            guiGraphics.drawString(this.font, cachedPageLines.get(i), leftPos + 36, yOffset, TEXT_COLOR, false);
            yOffset += 9; // 行高
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- 自定义组件 ---

    /**
     * 纯文字按钮，没有背景，悬停变色
     */
    class BookTextButton extends Button {
        private final boolean isUnlocked;

        public BookTextButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean isUnlocked) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.isUnlocked = isUnlocked;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int color = !isUnlocked ? LOCKED_COLOR : (this.isHovered ? HOVER_COLOR : TEXT_COLOR);
            // 左对齐绘制
            guiGraphics.drawString(Minecraft.getInstance().font, this.getMessage(), this.getX(), this.getY() + (this.height - 8) / 2, color, false);
        }
    }

    /**
     * 书本翻页按钮 (使用文字符号，更清晰)
     */
    class BookPageButton extends Button {
        private final boolean isForward;

        public BookPageButton(int x, int y, boolean isForward, OnPress onPress, boolean playTurnSound) {
            super(x, y, 20, 14, Component.literal(isForward ? ">" : "<"), onPress, DEFAULT_NARRATION);
            this.isForward = isForward;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // 使用加粗的文字符号
            Component arrow = Component.literal(isForward ? ">" : "<").withStyle(style -> style.withBold(true));
            int color = this.isHovered ? HOVER_COLOR : TEXT_COLOR;
            
            // 稍微放大一点 (可选，这里直接用加粗)
            guiGraphics.pose().pushPose();
            // guiGraphics.pose().scale(1.2f, 1.2f, 1.0f); // 如果需要放大，可以取消注释并调整坐标
            
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, arrow, this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, color);
            
            guiGraphics.pose().popPose();
        }
    }
}
