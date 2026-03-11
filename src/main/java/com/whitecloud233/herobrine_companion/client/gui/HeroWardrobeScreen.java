package com.whitecloud233.herobrine_companion.client.gui;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.world.inventory.HeroWardrobeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.fml.ModList;

public class HeroWardrobeScreen extends AbstractContainerScreen<HeroWardrobeMenu> {

    // 1.21.1: 使用 withDefaultNamespace 替代 new ResourceLocation("minecraft", ...)
    private static final ResourceLocation EMPTY_SLOT_HELMET = ResourceLocation.withDefaultNamespace("item/empty_armor_slot_helmet");
    private static final ResourceLocation EMPTY_SLOT_CHESTPLATE = ResourceLocation.withDefaultNamespace("item/empty_armor_slot_chestplate");
    private static final ResourceLocation EMPTY_SLOT_LEGGINGS = ResourceLocation.withDefaultNamespace("item/empty_armor_slot_leggings");
    private static final ResourceLocation EMPTY_SLOT_BOOTS = ResourceLocation.withDefaultNamespace("item/empty_armor_slot_boots");

    private float xMouse;
    private float yMouse;

    public HeroWardrobeScreen(HeroWardrobeMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick); // 1.21.1 背景渲染签名可能略有变动，带上参数更安全
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        this.xMouse = (float)mouseX;
        this.yMouse = (float)mouseY;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        graphics.fill(relX, relY, relX + this.imageWidth, relY + this.imageHeight, 0xFF3C3F41);
        graphics.renderOutline(relX, relY, this.imageWidth, this.imageHeight, 0xFF242424);

        graphics.fill(relX, relY, relX + this.imageWidth, relY + 14, 0xFF242424);
        graphics.fill(relX, relY, relX + this.imageWidth, relY + 2, 0xFF4A88C7);

        drawEnhancedSlotBackgrounds(graphics, relX, relY);

        if (this.menu.getHero() != null) {
            HeroEntity hero = this.menu.getHero();
            int platformX = relX + 56;
            int platformY = relY + 12;
            graphics.fill(platformX, platformY, platformX + 64, platformY + 66, 0xFF121212);
            graphics.renderOutline(platformX, platformY, 64, 66, 0xFF8B8B8B);

            float oldYBodyRotO = hero.yBodyRotO;
            float oldYRotO = hero.yRotO;
            float oldXRotO = hero.xRotO;
            float oldYHeadRotO = hero.yHeadRotO;

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
                    (relX + 88) - 30,       // x1
                    (relY + 72) - 60,       // y1
                    (relX + 88) + 30,       // x2
                    relY + 72,              // y2
                    30,                     // scale
                    0.0625F,                // yOffset
                    this.xMouse,            // 直接传原始鼠标 xMouse
                    this.yMouse,            // 直接传原始鼠标 yMouse
                    hero
            );
            hero.yBodyRotO = oldYBodyRotO;
            hero.yRotO = oldYRotO;
            hero.xRotO = oldXRotO;
            hero.yHeadRotO = oldYHeadRotO;
        }
    }

    private void drawEnhancedSlotBackgrounds(GuiGraphics graphics, int relX, int relY) {
        int slotBgColor = 0xFF8B8B8B;
        int slotInnerShadow = 0xFF373737;
        int slotHighlight = 0xFFFFFFFF;

        // 1.21.1: 更改为 NeoForge 的 ModList
        boolean hasCurios = ModList.get().isLoaded("curios");
        int customSlotCount = hasCurios ? 7 : 6;

        for (Slot slot : this.menu.slots) {
            int x = relX + slot.x - 1;
            int y = relY + slot.y - 1;

            graphics.fill(x, y, x + 18, y + 18, slotInnerShadow);
            graphics.fill(x + 1, y + 1, x + 18, y + 18, slotHighlight);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, slotBgColor);

            if (!slot.hasItem() && slot.index < customSlotCount) {
                renderGhostIcon(graphics, slot, x + 1, y + 1);
            }
        }
    }

    private ResourceLocation getGhostIconForSlot(int index) {
        boolean hasCurios = ModList.get().isLoaded("curios");

        if (index == 0) return EMPTY_SLOT_HELMET;
        if (index == 1) return EMPTY_SLOT_CHESTPLATE;
        if (index == 2) return EMPTY_SLOT_LEGGINGS;
        if (index == 3) return EMPTY_SLOT_BOOTS;

        if (hasCurios) {
            // 1.21.1: 非 Minecraft 命名空间，使用 fromNamespaceAndPath
            if (index == 4) return ResourceLocation.fromNamespaceAndPath("curios", "slot/empty_back_slot");
            if (index == 5) return ResourceLocation.withDefaultNamespace("item/empty_slot_sword");
            if (index == 6) return ResourceLocation.withDefaultNamespace("item/empty_armor_slot_shield");
        } else {
            if (index == 4) return ResourceLocation.withDefaultNamespace("item/empty_slot_sword");
            if (index == 5) return ResourceLocation.withDefaultNamespace("item/empty_armor_slot_shield");
        }

        return null;
    }

    private void renderGhostIcon(GuiGraphics graphics, Slot slot, int x, int y) {
        ResourceLocation icon = getGhostIconForSlot(slot.index);
        if (icon != null) {
            TextureAtlasSprite sprite = this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(icon);
            graphics.blit(x, y, 0, 16, 16, sprite, 1.0F, 1.0F, 1.0F, 0.5F);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY - 2, 0xFFFFFFFF, false);
    }
}