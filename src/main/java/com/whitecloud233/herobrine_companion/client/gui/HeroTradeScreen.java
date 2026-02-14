package com.whitecloud233.herobrine_companion.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

// 【核心修改】改为继承 Screen，彻底避开 AbstractContainerScreen 的逻辑，从而让 JEI 忽略此界面
// 同时实现 MenuAccess 接口，以满足 RegisterMenuScreensEvent 的泛型要求
public class HeroTradeScreen extends Screen implements MenuAccess<MerchantMenu> {
    // 配色方案
    private static final int COL_BG_MAIN    = 0xFF2B2B2B;
    private static final int COL_BG_SIDE    = 0xFF3C3F41;
    private static final int COL_BORDER     = 0xFF555555;
    private static final int COL_TEXT_MAIN  = 0xFFA9B7C6;
    private static final int COL_INFO       = 0xFF6A8759;

    // 按钮风格
    private static final int BTN_BG_NORMAL = 0xFF3C3F41;
    private static final int BTN_BG_HOVER  = 0xFF4C5052;
    private static final int BTN_BORDER    = 0xFF555555;

    // 菜单引用，用于数据交互
    private final MerchantMenu menu;
    // 布局参数
    protected int imageWidth = 276;
    protected int imageHeight = 166;
    protected int leftPos;
    protected int topPos;
    protected int titleLabelX = 10;
    protected int titleLabelY = 6;
    protected int inventoryLabelX = 107;
    protected int inventoryLabelY; // 动态计算

    // 【核心修改】不再使用反射，直接定义我们自己的控制变量
    private int scrollOff = 0;
    private int shopItem = -1;
    private boolean isDragging;

    public HeroTradeScreen(MerchantMenu menu, Inventory playerInventory, Component title) {
        super(title);
        this.menu = menu;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public MerchantMenu getMenu() {
        return this.menu;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 手动调用背景渲染
        renderBg(guiGraphics, partialTick, mouseX, mouseY);
        
        // 手动调用标签渲染
        renderLabels(guiGraphics, mouseX, mouseY);

        // 渲染自定义交易列表
        renderTrades(guiGraphics, mouseX, mouseY);

        // 渲染 Tooltip
        renderTradeTooltips(guiGraphics, mouseX, mouseY);
        
        // 渲染物品槽位 (因为不再继承 AbstractContainerScreen，需要手动渲染 Slot)
        // 注意：这里我们只渲染“假”的 Slot 背景，真实的 Slot 交互逻辑需要自己处理或者放弃
        // 如果需要真实的 Slot 交互，必须手动处理鼠标点击和渲染
        // 但考虑到这是一个交易界面，主要交互是点击交易项，Slot 主要是展示
        // 如果需要支持 Shift+点击等操作，这里会变得很复杂
        // 鉴于这是一个权衡方案，我们先假设主要功能是交易
        
        // 渲染菜单中的 Slot
        // 由于不再是 ContainerScreen，我们需要手动遍历 menu.slots 并渲染
        // 这需要将 Slot 的坐标转换为屏幕坐标
        // 这是一个比较大的改动，如果 Slot 功能很重要，这个方案可能不完美
        // 但为了彻底隐藏 JEI，这是必须的代价
        
        // 渲染 Slot 物品
        for (int k = 0; k < this.menu.slots.size(); ++k) {
            net.minecraft.world.inventory.Slot slot = this.menu.slots.get(k);
            if (slot.isActive()) {
                renderSlot(guiGraphics, slot);
            }
        }
        
        // 【新增】渲染鼠标抓取的物品 (Carried Item)
        // 之前因为没有调用 super.render() (AbstractContainerScreen 的实现)，导致鼠标上的物品没有渲染
        renderCarriedItem(guiGraphics, mouseX, mouseY);
        
        // 渲染 Slot 的 Tooltip
        renderSlotTooltip(guiGraphics, mouseX, mouseY);

        // 最后渲染通用的 Tooltip
        // super.render(guiGraphics, mouseX, mouseY, partialTick); // Screen.render 主要是渲染 widget，这里可能不需要
    }
    
    // 【新增】渲染鼠标抓取的物品
    private void renderCarriedItem(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        ItemStack carried = this.menu.getCarried();
        if (!carried.isEmpty()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 232.0F); // 确保在最上层
            guiGraphics.renderItem(carried, mouseX - 8, mouseY - 8);
            guiGraphics.renderItemDecorations(this.font, carried, mouseX - 8, mouseY - 8);
            guiGraphics.pose().popPose();
        }
    }
    
    // 手动渲染 Slot
    private void renderSlot(GuiGraphics guiGraphics, net.minecraft.world.inventory.Slot slot) {
        int i = slot.x;
        int j = slot.y;
        ItemStack itemstack = slot.getItem();
        boolean flag = false;
        boolean flag1 = slot == null; // clickedSlot logic omitted
        ItemStack itemstack1 = this.menu.getCarried();
        String s = null;
        if (slot == null) { // clickedSlot logic omitted
             // ...
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 100.0F);
        
        // 简单的物品渲染
        int x = i + this.leftPos;
        int y = j + this.topPos;
        
        guiGraphics.renderItem(itemstack, x, y);
        guiGraphics.renderItemDecorations(this.font, itemstack, x, y, s);
        
        // 高亮鼠标悬停的 Slot
        if (isHovering(slot, this.minecraft.mouseHandler.xpos() * (double)this.minecraft.getWindow().getGuiScaledWidth() / (double)this.minecraft.getWindow().getScreenWidth(), this.minecraft.mouseHandler.ypos() * (double)this.minecraft.getWindow().getGuiScaledHeight() / (double)this.minecraft.getWindow().getScreenHeight())) {
             renderSlotHighlight(guiGraphics, x, y, 0);
        }
        
        guiGraphics.pose().popPose();
    }
    
    // 【新增】渲染 Slot 高亮
    public static void renderSlotHighlight(GuiGraphics guiGraphics, int x, int y, int blitOffset) {
        // 使用 RenderType.guiOverlay() 替代 RenderSystem.getVertexSorting()
        // 或者直接使用 fillGradient 的简化版本，如果不涉及复杂的混合模式
        guiGraphics.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 16, -2130706433, -2130706433, blitOffset);
    }
    
    private void renderSlotTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty()) {
             net.minecraft.world.inventory.Slot hoveredSlot = findSlot(mouseX, mouseY);
             if (hoveredSlot != null && hoveredSlot.hasItem()) {
                 guiGraphics.renderTooltip(this.font, hoveredSlot.getItem(), mouseX, mouseY);
             }
        }
    }
    
    private net.minecraft.world.inventory.Slot findSlot(double mouseX, double mouseY) {
        for(int i = 0; i < this.menu.slots.size(); ++i) {
            net.minecraft.world.inventory.Slot slot = this.menu.slots.get(i);
            if (isHovering(slot, mouseX, mouseY) && slot.isActive()) {
                return slot;
            }
        }
        return null;
    }
    
    private boolean isHovering(net.minecraft.world.inventory.Slot slot, double mouseX, double mouseY) {
        return this.isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY);
    }
    
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        int i = this.leftPos;
        int j = this.topPos;
        mouseX -= (double)i;
        mouseY -= (double)j;
        return mouseX >= (double)(x - 1) && mouseX < (double)(x + width + 1) && mouseY >= (double)(y - 1) && mouseY < (double)(y + height + 1);
    }

    // 【新增】实现鼠标滚轮滚动逻辑
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double delta = scrollY;
        int i = this.menu.getOffers().size();
        if (i > 7) {
            int j = i - 7;
            this.scrollOff = (int)((double)this.scrollOff - delta);
            this.scrollOff = Mth.clamp(this.scrollOff, 0, j);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // 【新增】实现鼠标拖拽滚动条逻辑
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int i = this.menu.getOffers().size();
        if (this.isDragging) {
            int j = this.topPos + 18;
            int k = j + 139;
            int l = i - 7;
            float f = ((float)mouseY - (float)j - 13.5F) / ((float)(k - j) - 27.0F);
            f = f * (float)l + 0.5F;
            this.scrollOff = Mth.clamp((int)f, 0, l);
            return true;
        }
        // 处理 Slot 的拖拽逻辑比较复杂，这里暂时忽略，或者调用 menu 的逻辑
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.isDragging = false;
        int x = this.leftPos;
        int y = this.topPos;

        // 检查点击区域：列表区域
        if (button == 0 && mouseX >= x + 5 && mouseX < x + 103 && mouseY >= y + 18 && mouseY < y + 158) {
            int index = (int)((mouseY - (y + 18)) / 20);
            if (index >= 0 && index < 7) {
                int realIndex = this.scrollOff + index;
                MerchantOffers offers = this.menu.getOffers();

                if (!offers.isEmpty() && realIndex >= 0 && realIndex < offers.size()) {
                    // 播放音效
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));

                    // 更新选中项
                    this.shopItem = realIndex;

                    // 【核心逻辑】手动发送选择包，模拟原版行为
                    this.menu.setSelectionHint(realIndex);
                    this.minecraft.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSelectTradePacket(realIndex));
                    return true;
                }
            }
        }

        // 检查点击区域：滚动条
        int i = this.menu.getOffers().size();
        if (mouseX >= x + 100 && mouseX < x + 100 + 6 && mouseY >= y + 18 && mouseY < y + 18 + 139) {
            this.isDragging = true;
        }
        
        // 处理 Slot 点击
        net.minecraft.world.inventory.Slot slot = findSlot(mouseX, mouseY);
        if (slot != null) {
             // 这里需要模拟 ContainerScreen 的点击逻辑
             // 这是一个简化的处理，直接发送点击包
             // 注意：这可能不完全准确，特别是对于 Shift+点击等操作
             // 但对于简单的拿取/放入应该是有效的
             // 实际上，正确的做法是调用 minecraft.gameMode.handleInventoryMouseClick
             // 但我们需要 slotId
             
             // 暂时省略复杂的点击逻辑，因为这需要大量的代码来复制 ContainerScreen 的行为
             // 如果用户需要与背包交互，这个方案会有缺陷
             // 但为了隐藏 JEI，这是必须的
             
             // 尝试调用 menu 的点击逻辑？不，menu 是服务端的逻辑镜像
             // 我们需要发送包给服务器
             
             // 既然这是一个交易界面，玩家主要操作是点击交易项，然后从输出槽拿东西
             // 我们可以尝试只处理基本的点击
             
             if (this.minecraft != null && this.minecraft.gameMode != null) {
                 this.minecraft.gameMode.handleInventoryMouseClick(this.menu.containerId, slot.index, button, net.minecraft.world.inventory.ClickType.PICKUP, this.minecraft.player);
                 return true;
             }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 处理 Slot 释放逻辑 (如果需要拖拽物品)
        net.minecraft.world.inventory.Slot slot = findSlot(mouseX, mouseY);
        if (slot != null && this.minecraft != null && this.minecraft.gameMode != null) {
             // 简化的释放逻辑
             // this.minecraft.gameMode.handleInventoryMouseClick(this.menu.containerId, slot.index, button, net.minecraft.world.inventory.ClickType.PICKUP, this.minecraft.player);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    // 必须重写 isPauseScreen
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    // 必须重写 tick 来更新 menu
    @Override
    public void tick() {
        super.tick();
        if (!this.menu.stillValid(this.minecraft.player)) {
            this.minecraft.player.closeContainer();
        }
    }

    private void renderTrades(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        MerchantOffers offers = this.menu.getOffers();
        if (offers.isEmpty()) return;

        int x = this.leftPos;
        int y = this.topPos;
        int startY = y + 18;

        // 背景
        for (int l = 0; l < 7; ++l) {
            int index = this.scrollOff + l;
            if (index >= offers.size()) break;

            int entryY = startY + l * 20;
            boolean isSelected = (index == this.shopItem);
            boolean isHovered = (mouseX >= x + 5 && mouseX < x + 103 && mouseY >= entryY && mouseY < entryY + 20);

            int bgColor = (isSelected || isHovered) ? BTN_BG_HOVER : BTN_BG_NORMAL;
            guiGraphics.fill(x + 6, entryY, x + 102, entryY + 20, bgColor);
            guiGraphics.renderOutline(x + 6, entryY, 96, 20, BTN_BORDER);
        }

        // 物品和文字 (开启深度测试，防止重影)
        RenderSystem.enableDepthTest();
        // 稍微抬高 Z 轴，确保覆盖在刚才画的背景之上
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 5.0F);

        for (int l = 0; l < 7; ++l) {
            int index = this.scrollOff + l;
            if (index >= offers.size()) break;

            MerchantOffer offer = offers.get(index);
            ItemStack costA = offer.getCostA();
            ItemStack costB = offer.getCostB();
            ItemStack result = offer.getResult();
            int entryY = startY + l * 20;

            guiGraphics.renderItem(costA, x + 10, entryY + 2);
            guiGraphics.renderItemDecorations(this.font, costA, x + 10, entryY + 2);

            if (!costB.isEmpty()) {
                guiGraphics.renderItem(costB, x + 35, entryY + 2);
                guiGraphics.renderItemDecorations(this.font, costB, x + 35, entryY + 2);
            }

            guiGraphics.renderItem(result, x + 75, entryY + 2);
            guiGraphics.renderItemDecorations(this.font, result, x + 75, entryY + 2);

            // 绘制箭头
            guiGraphics.drawString(this.font, "->", x + 55, entryY + 6, 0xFF808080, false);

            if (offer.isOutOfStock()) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
                guiGraphics.fill(x + 10, entryY + 10, x + 90, entryY + 11, 0x80FF0000);
                guiGraphics.pose().popPose();
            }
        }

        guiGraphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = this.leftPos;
        int y = this.topPos;
        int topBarHeight = 20;
        int sideBarWidth = 105;

        // 主背景
        guiGraphics.fill(x + sideBarWidth, y + topBarHeight, x + this.imageWidth, y + this.imageHeight, COL_BG_MAIN);
        guiGraphics.fill(x, y + topBarHeight, x + sideBarWidth, y + this.imageHeight, COL_BG_SIDE);
        guiGraphics.fill(x, y, x + this.imageWidth, y + topBarHeight, COL_BG_SIDE);

        guiGraphics.renderOutline(x, y, this.imageWidth, this.imageHeight, COL_BORDER);
        guiGraphics.fill(x + sideBarWidth, y + topBarHeight, x + sideBarWidth + 1, y + this.imageHeight, COL_BORDER);
        guiGraphics.fill(x, y + topBarHeight, x + this.imageWidth, y + topBarHeight + 1, COL_BORDER);

        int tabWidth = 120;
        guiGraphics.fill(x, y, x + tabWidth, y + 2, 0xFF4A88C7);

        guiGraphics.fill(x + 5, y + 16, x + 103, y + 158, COL_BG_SIDE);
        guiGraphics.renderOutline(x + 5, y + 16, 98, 142, COL_BORDER);

        drawSlotBackground(guiGraphics, x + 136, y + 37);
        drawSlotBackground(guiGraphics, x + 162, y + 37);
        drawSlotBackground(guiGraphics, x + 220, y + 37);

        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                drawSlotBackground(guiGraphics, x + 108 + j * 18, y + 84 + i * 18);
            }
        }
        for (int k = 0; k < 9; ++k) {
            drawSlotBackground(guiGraphics, x + 108 + k * 18, y + 142);
        }

        // 绘制箭头 (现在这是唯一的箭头了)
        int arrowX = x + 187;
        int arrowY = y + 37;
        drawArrow(guiGraphics, arrowX, arrowY);

        // 缺货 X 标记 (右侧)
        MerchantOffers offers = this.menu.getOffers();
        if (!offers.isEmpty() && this.shopItem >= 0 && this.shopItem < offers.size()) {
            MerchantOffer offer = offers.get(this.shopItem);
            if (offer.isOutOfStock()) {
                guiGraphics.fill(x + 212 + 35, y + 35, x + 212 + 35 + 28, y + 35 + 21, 0x80FF0000);
                guiGraphics.drawCenteredString(this.font, "X", x + 212 + 35 + 14, y + 35 + 6, 0xFFFF0000);
            }
        }

        // 信任度条
        HeroEntity hero = getHeroEntity();
        if (hero != null) {
            int trust = hero.getTrustLevel();
            int barX = x + 136;
            int barY = y + 65;
            int barWidth = 102;
            guiGraphics.drawString(this.font, "Trust: " + trust, barX, barY - 10, COL_INFO, false);
            guiGraphics.fill(barX, barY, barX + barWidth, barY + 4, 0xFF555555);
            float progress = Math.min(1.0f, (float)trust / 100.0f);
            int color = trust < 30 ? 0xFFFF5555 : (trust < 70 ? 0xFFFFFF55 : 0xFF55FF55);
            guiGraphics.fill(barX, barY, barX + (int)(barWidth * progress), barY + 4, color);
        }

        renderCustomScroller(guiGraphics, x, y);
    }

    private HeroEntity getHeroEntity() {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        HeroEntity closest = null;
        double minDst = Double.MAX_VALUE;
        for (Entity e : this.minecraft.level.entitiesForRendering()) {
            if (e instanceof HeroEntity hero) {
                double dst = hero.distanceToSqr(this.minecraft.player);
                if (dst < minDst) {
                    minDst = dst;
                    closest = hero;
                }
            }
        }
        return (minDst < 256) ? closest : null;
    }

    private void drawArrow(GuiGraphics guiGraphics, int x, int y) {
        int color = 0xFF808080;
        guiGraphics.fill(x, y + 6, x + 16, y + 10, color);
        guiGraphics.fill(x + 16, y + 2, x + 18, y + 14, color);
        guiGraphics.fill(x + 18, y + 4, x + 20, y + 12, color);
        guiGraphics.fill(x + 20, y + 6, x + 22, y + 10, color);
    }

    private void renderCustomScroller(GuiGraphics guiGraphics, int x, int y) {
        MerchantOffers offers = this.menu.getOffers();
        if (!offers.isEmpty()) {
            int i = offers.size() + 1 - 7;
            if (i > 1) {
                int i1 = Math.min(113, this.scrollOff * 113 / i);
                if (this.scrollOff == i - 1) i1 = 113;

                int scrollerX = x + 100;
                int scrollerY = y + 18 + i1;
                guiGraphics.fill(scrollerX, scrollerY, scrollerX + 6, scrollerY + 27, 0xFF808080);
                guiGraphics.renderOutline(scrollerX, scrollerY, 6, 27, COL_BORDER);
            } else {
                int scrollerX = x + 100;
                int scrollerY = y + 18;
                guiGraphics.fill(scrollerX, scrollerY, scrollerX + 6, scrollerY + 27, 0xFF404040);
                guiGraphics.renderOutline(scrollerX, scrollerY, 6, 27, COL_BORDER);
            }
        }
    }

    private void drawSlotBackground(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x, y, x + 18, y + 18, COL_BG_SIDE);
        guiGraphics.renderOutline(x, y, 18, 18, COL_BORDER);
    }

    private void renderTradeTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        int startY = y + 18;

        MerchantOffers offers = this.menu.getOffers();
        if (!offers.isEmpty()) {
            for (int l = 0; l < 7; ++l) {
                int index = this.scrollOff + l;
                if (index >= offers.size()) break;
                int entryY = startY + l * 20;
                if (mouseX >= x + 5 && mouseX < x + 103 && mouseY >= entryY && mouseY < entryY + 20) {
                    MerchantOffer offer = offers.get(index);
                    ItemStack hoveredItem = ItemStack.EMPTY;
                    if (mouseX >= x + 10 && mouseX < x + 26 && mouseY >= entryY + 2 && mouseY < entryY + 18) {
                        hoveredItem = offer.getCostA();
                    } else if (mouseX >= x + 35 && mouseX < x + 51 && mouseY >= entryY + 2 && mouseY < entryY + 18) {
                        hoveredItem = offer.getCostB();
                    } else if (mouseX >= x + 75 && mouseX < x + 91 && mouseY >= entryY + 2 && mouseY < entryY + 18) {
                        hoveredItem = offer.getResult();
                    }
                    if (!hoveredItem.isEmpty()) {
                        guiGraphics.renderTooltip(this.font, hoveredItem, mouseX, mouseY);
                    }
                }
            }
        }
    }

    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.leftPos + this.titleLabelX, this.topPos + this.titleLabelY, COL_TEXT_MAIN, false);
        guiGraphics.drawString(this.font, Component.translatable("container.inventory"), this.leftPos + this.inventoryLabelX, this.topPos + this.inventoryLabelY, COL_TEXT_MAIN, false);
    }
}
