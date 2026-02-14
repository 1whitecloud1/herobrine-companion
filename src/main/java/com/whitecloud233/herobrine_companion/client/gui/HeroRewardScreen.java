package com.whitecloud233.herobrine_companion.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.HeroRewards;
import com.whitecloud233.herobrine_companion.network.ClaimRewardPacket;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class HeroRewardScreen extends Screen {
    private final int entityId;
    private HeroEntity hero;
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 200;
    
    // 滚动相关
    private float scrollOffset = 0;
    private boolean isScrolling = false;
    private static final int ITEM_HEIGHT = 25; // 每行高度
    private static final int LIST_TOP_MARGIN = 30;
    private static final int LIST_BOTTOM_MARGIN = 30;
    
    // 缓存按钮以便在滚动时调整位置
    private final List<RewardButton> rewardButtons = new ArrayList<>();

    public HeroRewardScreen(int entityId) {
        super(Component.translatable("gui.herobrine_companion.rewards_title"));
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft.level != null) {
            Entity entity = this.minecraft.level.getEntity(this.entityId);
            if (entity instanceof HeroEntity h) {
                this.hero = h;
            }
        }

        int startX = (this.width - PANEL_WIDTH) / 2;
        int startY = (this.height - PANEL_HEIGHT) / 2;

        rewardButtons.clear();
        
        // 初始化按钮，但不添加到 Screen 的 renderables 中（我们手动渲染和处理）
        // 或者添加到 renderables 但在 render 中手动控制可见性和位置
        // 这里我们选择：添加到 renderables，但在 render 中更新它们的 Y 坐标，并根据裁剪区域设置 visible
        
        int index = 0;
        for (HeroRewards.Reward reward : HeroRewards.REWARDS) {
            boolean unlocked = hero != null && hero.getTrustLevel() >= reward.requiredTrust;
            boolean claimed = hero != null && hero.hasClaimedReward(reward.id);

            RewardButton btn = new RewardButton(startX + 200, 0, 80, 20, 
                Component.translatable(claimed ? "gui.herobrine_companion.claimed" : (unlocked ? "gui.herobrine_companion.claim" : "gui.herobrine_companion.locked")), 
                button -> {
                    if (unlocked && !claimed) {
                        PacketHandler.sendToServer(new ClaimRewardPacket(this.entityId, reward.id));
                        if (hero != null) {
                            hero.claimReward(reward.id);
                        }
                        button.setMessage(Component.translatable("gui.herobrine_companion.claimed"));
                        button.active = false;
                    }
                },
                Tooltip.create(Component.translatable("gui.herobrine_companion.reward_tooltip", reward.requiredTrust)),
                index
            );
            
            btn.active = unlocked && !claimed;
            this.addRenderableWidget(btn);
            rewardButtons.add(btn);
            
            index++;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> {
            this.onClose();
            Minecraft.getInstance().setScreen(new HeroScreen(this.entityId));
        }).bounds(startX + PANEL_WIDTH / 2 - 40, startY + PANEL_HEIGHT - 25, 80, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 留空
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null) return;
        
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        
        int startX = (this.width - PANEL_WIDTH) / 2;
        int startY = (this.height - PANEL_HEIGHT) / 2;

        // 绘制背景
        guiGraphics.fill(startX, startY, startX + PANEL_WIDTH, startY + PANEL_HEIGHT, 0xFF2B2B2B);
        guiGraphics.renderOutline(startX, startY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF555555);

        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, startY + 10, 0xFFFFFF);

        // --- 滚动列表区域 ---
        int listX = startX + 10;
        int listY = startY + LIST_TOP_MARGIN;
        int listWidth = PANEL_WIDTH - 20;
        int listHeight = PANEL_HEIGHT - LIST_TOP_MARGIN - LIST_BOTTOM_MARGIN;
        
        int contentHeight = HeroRewards.REWARDS.size() * ITEM_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - listHeight);
        
        // 限制滚动范围
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll);

        // 启用裁剪 (Scissor Test)
        // 注意：enableScissor 使用的是窗口坐标，Y轴向上，需要转换
        double scale = this.minecraft.getWindow().getGuiScale();
        int scissorX = (int) (listX * scale);
        int scissorY = (int) ((this.height - (listY + listHeight)) * scale);
        int scissorW = (int) (listWidth * scale);
        int scissorH = (int) (listHeight * scale);
        
        RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);

        // 渲染列表内容
        int currentY = (int) (listY - scrollOffset);
        
        int index = 0;
        for (HeroRewards.Reward reward : HeroRewards.REWARDS) {
            // 优化：只渲染可见范围内的条目
            if (currentY + ITEM_HEIGHT > listY && currentY < listY + listHeight) {
                int itemX = listX;
                
                // 渲染物品图标
                for (ItemStack item : reward.items) {
                    guiGraphics.renderItem(item, itemX, currentY + 2);
                    guiGraphics.renderItemDecorations(this.font, item, itemX, currentY + 2);
                    
                    // Tooltip 处理 (需要加上裁剪区域判断，防止在区域外显示)
                    if (isHovering(itemX, currentY + 2, 16, 16, mouseX, mouseY) && 
                        mouseY >= listY && mouseY <= listY + listHeight) {
                        // 延迟渲染 Tooltip，避免被裁剪
                        // 这里我们先不渲染，等裁剪结束后再渲染
                        // 或者使用 guiGraphics.renderTooltip，它通常会处理得比较好，但在 Scissor 下可能会被剪掉
                        // 最好的做法是记录下要渲染的 Tooltip，最后统一渲染。
                        // 但为了简单，我们先直接渲染，看看效果。如果被剪掉，再改。
                        // 实际上 renderTooltip 会在最上层绘制，不受 Scissor 影响（通常）。
                        // 但是 Scissor 是 GL 级别的，会影响所有绘制。
                        // 所以我们需要在 disableScissor 后再绘制 Tooltip。
                    }
                    
                    itemX += 18;
                }
                
                if (reward.items.size() == 1) {
                    guiGraphics.drawString(this.font, reward.items.get(0).getHoverName(), itemX + 4, currentY + 6, 0xFFFFFF, false);
                }
            }
            
            // 更新按钮位置和可见性
            if (index < rewardButtons.size()) {
                RewardButton btn = rewardButtons.get(index);
                btn.setY(currentY);
                btn.visible = currentY + btn.getHeight() > listY && currentY < listY + listHeight;
            }

            currentY += ITEM_HEIGHT;
            index++;
        }

        RenderSystem.disableScissor();

        // --- 绘制滚动条 ---
        if (maxScroll > 0) {
            int scrollBarX = startX + PANEL_WIDTH - 8;
            int scrollBarY = listY;
            int scrollBarHeight = listHeight;
            
            int barHeight = (int) ((float) (listHeight * listHeight) / contentHeight);
            barHeight = Math.max(32, barHeight);
            
            int barY = (int) (scrollOffset * (listHeight - barHeight) / maxScroll) + listY;
            
            guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + 6, scrollBarY + scrollBarHeight, 0xFF000000);
            guiGraphics.fill(scrollBarX, barY, scrollBarX + 6, barY + barHeight, 0xFF808080);
            guiGraphics.fill(scrollBarX, barY, scrollBarX + 5, barY + barHeight - 1, 0xFFC0C0C0);
        }

        // 调用 super.render 绘制按钮 (它们会自动处理 visible)
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // --- 再次遍历绘制 Tooltip (在 Scissor 之外) ---
        currentY = (int) (listY - scrollOffset);
        for (HeroRewards.Reward reward : HeroRewards.REWARDS) {
            if (currentY + ITEM_HEIGHT > listY && currentY < listY + listHeight) {
                int itemX = listX;
                for (ItemStack item : reward.items) {
                    if (isHovering(itemX, currentY + 2, 16, 16, mouseX, mouseY) && 
                        mouseY >= listY && mouseY <= listY + listHeight) {
                        guiGraphics.renderTooltip(this.font, item, mouseX, mouseY);
                    }
                    itemX += 18;
                }
            }
            currentY += ITEM_HEIGHT;
        }
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int contentHeight = HeroRewards.REWARDS.size() * ITEM_HEIGHT;
        int listHeight = PANEL_HEIGHT - LIST_TOP_MARGIN - LIST_BOTTOM_MARGIN;
        int maxScroll = Math.max(0, contentHeight - listHeight);
        
        if (maxScroll > 0) {
            this.scrollOffset = (float) Mth.clamp(this.scrollOffset - scrollY * 10, 0, maxScroll); // 滚动速度 10
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 简单的滚动条拖动逻辑 (可选，这里先只实现滚轮)
        // 如果需要拖动滚动条，需要判断鼠标是否在滚动条区域
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean isHovering(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    // 简单的按钮包装类，方便管理
    private static class RewardButton extends Button {
        public final int index;
        public RewardButton(int x, int y, int width, int height, Component message, OnPress onPress, Tooltip tooltip, int index) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.setTooltip(tooltip);
            this.index = index;
        }
    }
}
