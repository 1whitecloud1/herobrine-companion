package com.whitecloud233.modid.herobrine_companion.client.gui;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.world.inventory.HeroWardrobeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

public class HeroWardrobeScreen extends AbstractContainerScreen<HeroWardrobeMenu> {

    // 引入原版槽位虚影的纹理路径
    private static final ResourceLocation EMPTY_SLOT_HELMET = new ResourceLocation("minecraft", "item/empty_armor_slot_helmet");
    private static final ResourceLocation EMPTY_SLOT_CHESTPLATE = new ResourceLocation("minecraft", "item/empty_armor_slot_chestplate");
    private static final ResourceLocation EMPTY_SLOT_LEGGINGS = new ResourceLocation("minecraft", "item/empty_armor_slot_leggings");
    private static final ResourceLocation EMPTY_SLOT_BOOTS = new ResourceLocation("minecraft", "item/empty_armor_slot_boots");

    private float xMouse;
    private float yMouse;

    public HeroWardrobeScreen(HeroWardrobeMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        this.xMouse = (float)mouseX;
        this.yMouse = (float)mouseY;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        // 1. 调整主背景颜色：略微调浅至 0xFF3C3F41，增强与物品的对比度
        graphics.fill(relX, relY, relX + this.imageWidth, relY + this.imageHeight, 0xFF3C3F41);
        graphics.renderOutline(relX, relY, this.imageWidth, this.imageHeight, 0xFF242424);

        // 2. 装饰线和标题栏
        graphics.fill(relX, relY, relX + this.imageWidth, relY + 14, 0xFF242424);
        graphics.fill(relX, relY, relX + this.imageWidth, relY + 2, 0xFF4A88C7);

        // 3. 动态绘制槽位底框与虚影
        drawEnhancedSlotBackgrounds(graphics, relX, relY);

        // 4. 渲染 HeroEntity 预览
        if (this.menu.getHero() != null) {
            HeroEntity hero = this.menu.getHero();
            int platformX = relX + 56;
            int platformY = relY + 12;
            graphics.fill(platformX, platformY, platformX + 64, platformY + 66, 0xFF121212);
            graphics.renderOutline(platformX, platformY, 64, 66, 0xFF8B8B8B); // 调亮展示台边框

            // ============== [终极修复] 备份真实实体的上一帧数据 ==============
            float oldYBodyRotO = hero.yBodyRotO;
            float oldYRotO = hero.yRotO;
            float oldXRotO = hero.xRotO;
            float oldYHeadRotO = hero.yHeadRotO;
            // ==============================================================

            // 强杀驱魔人扭曲 Bug
            float lookX = (float)(relX + 88) - this.xMouse;
            float lookY = (float)(relY + 25) - this.yMouse;
            float f = (float)Math.atan((double)(lookX / 40.0F));
            float f1 = (float)Math.atan((double)(lookY / 40.0F));
            hero.yBodyRotO = 180.0F + f * 20.0F;
            hero.yRotO = 180.0F + f * 40.0F;
            hero.xRotO = -f1 * 20.0F;
            hero.yHeadRotO = hero.yRotO;

            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    relX + 88,
                    relY + 72,
                    30,
                    lookX,
                    lookY,
                    hero
            );

            // ============== [终极修复] 渲染完毕，立即把真实数据还给实体 ==============
            hero.yBodyRotO = oldYBodyRotO;
            hero.yRotO = oldYRotO;
            hero.xRotO = oldXRotO;
            hero.yHeadRotO = oldYHeadRotO;
            // =====================================================================
        }
    }

    private void drawEnhancedSlotBackgrounds(GuiGraphics graphics, int relX, int relY) {
        int slotBgColor = 0xFF8B8B8B;
        int slotInnerShadow = 0xFF373737;
        int slotHighlight = 0xFFFFFFFF;

        // 关键修复 1：动态计算专属槽位总数
        // 4个护甲槽 + 2个手持槽 = 6。如果加载了 Curios，再加 1 个背部槽 = 7。
        boolean hasCurios = net.minecraftforge.fml.ModList.get().isLoaded("curios");
        int customSlotCount = hasCurios ? 7 : 6;

        for (Slot slot : this.menu.slots) {
            int x = relX + slot.x - 1;
            int y = relY + slot.y - 1;

            // 绘制槽位底框
            graphics.fill(x, y, x + 18, y + 18, slotInnerShadow);
            graphics.fill(x + 1, y + 1, x + 18, y + 18, slotHighlight);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, slotBgColor);

            // 关键修复 2：使用动态的 customSlotCount，防止漏掉第 6 槽（副手）
            if (!slot.hasItem() && slot.index < customSlotCount) {
                renderGhostIcon(graphics, slot, x + 1, y + 1);
            }
        }
    }

    private ResourceLocation getGhostIconForSlot(int index) {
        boolean hasCurios = net.minecraftforge.fml.ModList.get().isLoaded("curios");

        // 0-3 永远是固定的护甲槽
        if (index == 0) return EMPTY_SLOT_HELMET;
        if (index == 1) return EMPTY_SLOT_CHESTPLATE;
        if (index == 2) return EMPTY_SLOT_LEGGINGS;
        if (index == 3) return EMPTY_SLOT_BOOTS;

        // 关键修复 3：根据 Curios 是否加载，动态分配 4、5、6 槽位的图标
        if (hasCurios) {
            // 加载了 Curios，所有手持槽位往后推一位
            if (index == 4) return new ResourceLocation("curios", "slot/empty_back_slot"); // Curios 自带的背部虚影
            if (index == 5) return new ResourceLocation("minecraft", "item/empty_slot_sword"); // 主手
            if (index == 6) return new ResourceLocation("minecraft", "item/empty_armor_slot_shield"); // 副手
        } else {
            // 没加载 Curios，紧挨着护甲的就是手持槽位
            if (index == 4) return new ResourceLocation("minecraft", "item/empty_slot_sword"); // 主手
            if (index == 5) return new ResourceLocation("minecraft", "item/empty_armor_slot_shield"); // 副手
        }

        return null;
    }

    private void renderGhostIcon(GuiGraphics graphics, Slot slot, int x, int y) {
        // 关键修复 2：使用 slot.index (Menu 中的绝对排序索引)
        // 而不是 slot.getSlotIndex() (各自独立 Inventory 的内部索引)
        ResourceLocation icon = getGhostIconForSlot(slot.index);
        if (icon != null) {
            TextureAtlasSprite sprite = this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(icon);
            graphics.blit(x, y, 0, 16, 16, sprite, 1.0F, 1.0F, 1.0F, 0.5F);
        }
    }


    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题文本改为纯白色，增加可读性
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY - 2, 0xFFFFFFFF, false);
    }
}