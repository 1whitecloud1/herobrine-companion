package com.whitecloud233.modid.herobrine_companion.client.gui;

import com.whitecloud233.modid.herobrine_companion.api.compat.HeroAWCompat;
import com.whitecloud233.modid.herobrine_companion.api.compat.HeroAccessoriesClientCompat;
import com.whitecloud233.modid.herobrine_companion.api.compat.HeroAccessoriesCompat;
import com.whitecloud233.modid.herobrine_companion.compat.screens.ExternalScreenShortcuts;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.world.inventory.HeroWardrobeMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

@SuppressWarnings("removal")
public class HeroWardrobeScreen extends AbstractContainerScreen<HeroWardrobeMenu> {

    private static final ResourceLocation EMPTY_SLOT_HELMET = new ResourceLocation("minecraft", "item/empty_armor_slot_helmet");
    private static final ResourceLocation EMPTY_SLOT_CHESTPLATE = new ResourceLocation("minecraft", "item/empty_armor_slot_chestplate");
    private static final ResourceLocation EMPTY_SLOT_LEGGINGS = new ResourceLocation("minecraft", "item/empty_armor_slot_leggings");
    private static final ResourceLocation EMPTY_SLOT_BOOTS = new ResourceLocation("minecraft", "item/empty_armor_slot_boots");

    // === 标题栏右侧的两个快捷按钮（时装工坊 / 饰品栏）===
    private static final int SHORTCUT_BUTTON_WIDTH = 44;
    private static final int SHORTCUT_BUTTON_HEIGHT = 12;
    private static final int SHORTCUT_BUTTON_GAP = 3;

    private float xMouse;
    private float yMouse;

    public HeroWardrobeScreen(HeroWardrobeMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = menu.getScreenWidth();
        this.imageHeight = menu.getScreenHeight();
    }

    @Override
    protected void init() {
        super.init();

        // 标题栏只有 14px 高、标题文字靠左，右侧这块是页面上唯一的空白区，
        // 所以两个小按钮就贴在标题栏右端，完全不占用任何槽位。
        // 位置固定按「两个按钮」预留，某个模组没装就只空出那一格，不影响另一个的位置。
        int rightEdge = this.leftPos + this.imageWidth - 2;
        int buttonY = this.topPos + 1;

        if (HeroAWCompat.isLoaded()) {
            this.addRenderableWidget(new ShortcutButton(
                    rightEdge - SHORTCUT_BUTTON_WIDTH * 2 - SHORTCUT_BUTTON_GAP,
                    buttonY,
                    Component.translatable("gui.herobrine_companion.wardrobe.shortcut.aw"),
                    this::openArmourersWorkshopWardrobe));
        }

        if (HeroAccessoriesCompat.isLoaded()) {
            this.addRenderableWidget(new ShortcutButton(
                    rightEdge - SHORTCUT_BUTTON_WIDTH,
                    buttonY,
                    Component.translatable("gui.herobrine_companion.wardrobe.shortcut.accessories"),
                    () -> HeroAccessoriesClientCompat.openAccessoriesScreen(this.menu.getHero())));
        }
    }

    private void openArmourersWorkshopWardrobe() {
        HeroEntity hero = this.menu.getHero();
        if (hero == null) {
            if (this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        Component.translatable("message.herobrine_companion.wardrobe.hero_unavailable"), true);
            }
            return;
        }

        // 传入 Hero 的实体，而不是触发 AW 的玩家快捷键；AW 会据此打开 Herobrine 的玩家型时装页面。
        ExternalScreenShortcuts.runAfterClosingScreen(() -> {
            if (!HeroAWCompat.openWardrobeFor(hero) && Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.translatable("message.herobrine_companion.wardrobe.aw_open_failed"), true);
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        this.xMouse = (float) mouseX;
        this.yMouse = (float) mouseY;
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

        if (this.menu.getHero() == null) {
            return;
        }

        HeroEntity hero = this.menu.getHero();
        int platformX = relX + 56;
        int platformY = relY + 12;
        graphics.fill(platformX, platformY, platformX + 64, platformY + 66, 0xFF121212);
        graphics.renderOutline(platformX, platformY, 64, 66, 0xFF8B8B8B);

        float oldYBodyRotO = hero.yBodyRotO;
        float oldYRotO = hero.yRotO;
        float oldXRotO = hero.xRotO;
        float oldYHeadRotO = hero.yHeadRotO;

        float lookX = (float) (relX + 88) - this.xMouse;
        float lookY = (float) (relY + 25) - this.yMouse;
        float f = (float) Math.atan(lookX / 40.0F);
        float f1 = (float) Math.atan(lookY / 40.0F);
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

        hero.yBodyRotO = oldYBodyRotO;
        hero.yRotO = oldYRotO;
        hero.xRotO = oldXRotO;
        hero.yHeadRotO = oldYHeadRotO;
    }

    private void drawEnhancedSlotBackgrounds(GuiGraphics graphics, int relX, int relY) {
        int slotBgColor = 0xFF8B8B8B;
        int slotInnerShadow = 0xFF373737;
        int slotHighlight = 0xFFFFFFFF;

        for (Slot slot : this.menu.slots) {
            int x = relX + slot.x - 1;
            int y = relY + slot.y - 1;

            graphics.fill(x, y, x + 18, y + 18, slotInnerShadow);
            graphics.fill(x + 1, y + 1, x + 18, y + 18, slotHighlight);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, slotBgColor);

            ResourceLocation icon = getGhostIconForSlot(slot.index);
            if (!slot.hasItem() && icon != null) {
                renderGhostIcon(graphics, icon, x + 1, y + 1);
            }
        }
    }

    private ResourceLocation getGhostIconForSlot(int index) {
        if (index == 0) return EMPTY_SLOT_HELMET;
        if (index == 1) return EMPTY_SLOT_CHESTPLATE;
        if (index == 2) return EMPTY_SLOT_LEGGINGS;
        if (index == 3) return EMPTY_SLOT_BOOTS;
        if (index == this.menu.getCurioBackSlotIndex()) return CuriosClientSafeInvoker.getBackSlotIcon(this.menu.getHero());
        if (index == this.menu.getMainHandSlotIndex()) return new ResourceLocation("minecraft", "item/empty_slot_sword");
        if (index == this.menu.getOffHandSlotIndex()) return new ResourceLocation("minecraft", "item/empty_armor_slot_shield");
        return null;
    }

    private void renderGhostIcon(GuiGraphics graphics, ResourceLocation icon, int x, int y) {
        TextureAtlasSprite sprite = this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(icon);
        if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
            // Curios bridges/resource packs may provide the PNG without stitching it into this atlas.
            ResourceLocation texture = new ResourceLocation(icon.getNamespace(), "textures/" + icon.getPath() + ".png");
            if (this.minecraft.getResourceManager().getResource(texture).isPresent()) {
                graphics.blit(texture, x, y, 0, 0, 16, 16, 16, 16);
                return;
            }
            // An unavailable optional-mod icon must never render the purple/black missing sprite.
            sprite = this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(EMPTY_SLOT_CHESTPLATE);
        }
        graphics.blit(x, y, 0, 16, 16, sprite, 1.0F, 1.0F, 1.0F, 0.5F);
    }

    private static final class CuriosClientSafeInvoker {
        private static ResourceLocation getBackSlotIcon(HeroEntity hero) {
            return top.theillusivec4.curios.api.CuriosApi.getSlot("back", hero.level())
                    .map(top.theillusivec4.curios.api.type.ISlotType::getIcon)
                    .orElse(EMPTY_SLOT_CHESTPLATE);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY - 2, 0xFFFFFFFF, false);
    }

    /**
     * 标题栏用的小尺寸扁平按钮。
     *
     * <p>原版 {@code Button} 的贴图固定按 20px 高绘制，压到 12px 会被裁切，所以这里
     * 自绘一个与页面配色（面板 0xFF3C3F41 / 强调色 0xFF4A88C7）一致的极简按钮。
     */
    private static final class ShortcutButton extends AbstractWidget {

        private final Runnable action;

        private ShortcutButton(int x, int y, Component message, Runnable action) {
            super(x, y, SHORTCUT_BUTTON_WIDTH, SHORTCUT_BUTTON_HEIGHT, message);
            this.action = action;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            this.action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            boolean highlighted = this.isHoveredOrFocused();

            graphics.fill(x, y, x + this.width, y + this.height,
                    highlighted ? 0xFF4A88C7 : 0xFF4A4A4A);
            graphics.renderOutline(x, y, this.width, this.height, 0xFF1E1E1E);
            graphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                    x + this.width / 2, y + 2, 0xFFFFFFFF);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, this.getMessage());
        }
    }
}
