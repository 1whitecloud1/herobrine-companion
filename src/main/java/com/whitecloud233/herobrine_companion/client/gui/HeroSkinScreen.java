package com.whitecloud233.herobrine_companion.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.event.ModEvents;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.ToggleSkinPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.util.ArrayList;
import java.util.List;

public class HeroSkinScreen extends Screen {

    private final int entityId;

    private static class SkinOption {
        int variantId;
        String nameKey;
        HeroEntity dummyEntity;

        public SkinOption(int id, String key) {
            this.variantId = id;
            this.nameKey = key;
        }
    }

    private final List<SkinOption> skinOptions = new ArrayList<>();

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 220;

    private static final int COL_BG_MAIN    = 0xFF2B2B2B;
    private static final int COL_BORDER     = 0xFF555555;
    private static final int COL_TITLE      = 0xFFCC7832;

    private int selectedSkinId = 0;
    private String customSkinName = "";
    private boolean initializedState = false;

    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 2;

    private EditBox customSkinInput;

    public HeroSkinScreen(int entityId) {
        super(Component.translatable("gui.herobrine_companion.skin_title"));
        this.entityId = entityId;

        skinOptions.add(new SkinOption(HeroEntity.SKIN_HEROBRINE, "gui.herobrine_companion.skin.herobrine_name"));
        skinOptions.add(new SkinOption(HeroEntity.SKIN_HERO, "gui.herobrine_companion.skin.hero_name"));
        skinOptions.add(new SkinOption(HeroEntity.SKIN_CUSTOM, "gui.herobrine_companion.skin.custom_name"));
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null || this.minecraft.level == null) return;

        for (SkinOption option : skinOptions) {
            if (option.dummyEntity == null) {
                option.dummyEntity = ModEvents.HERO.get().create(this.minecraft.level);
                if (option.dummyEntity != null) {
                    option.dummyEntity.setSkinVariant(option.variantId);
                    if (option.variantId == HeroEntity.SKIN_CUSTOM) {
                        option.dummyEntity.setCustomSkinName(this.customSkinName);
                    }
                }
            }
        }

        if (!initializedState) {
            Entity realEntity = this.minecraft.level.getEntity(this.entityId);
            if (realEntity instanceof HeroEntity realHero) {
                this.selectedSkinId = realHero.getSkinVariant();
                this.customSkinName = realHero.getCustomSkinName();
                for (SkinOption option : skinOptions) {
                    if (option.variantId == HeroEntity.SKIN_CUSTOM && option.dummyEntity != null) {
                        option.dummyEntity.setCustomSkinName(this.customSkinName);
                    }
                }
            }
            initializedState = true;
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startX = centerX - PANEL_WIDTH / 2;
        int startY = centerY - PANEL_HEIGHT / 2;
        int btnY = startY + PANEL_HEIGHT - 50;

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, skinOptions.size());

        int slotWidth = PANEL_WIDTH / ITEMS_PER_PAGE;

        for (int i = startIndex; i < endIndex; i++) {
            SkinOption option = skinOptions.get(i);
            int relativeIndex = i - startIndex;
            int itemCenterX = startX + slotWidth * relativeIndex + slotWidth / 2;

            boolean isSelected = (this.selectedSkinId == option.variantId);

            if (option.variantId == HeroEntity.SKIN_CUSTOM && isSelected) {
                this.customSkinInput = new EditBox(this.font, itemCenterX - 50, btnY - 45, 100, 20, Component.translatable("gui.herobrine_companion.skin.custom_input"));
                this.customSkinInput.setMaxLength(256);
                this.customSkinInput.setValue(this.customSkinName);
                this.customSkinInput.setResponder(name -> {
                    this.customSkinName = name;
                    if (option.dummyEntity != null) {
                        option.dummyEntity.setCustomSkinName(name);
                    }
                });
                this.addRenderableWidget(this.customSkinInput);

                Button fileBtn = new HeroScreen.ThemedButton(
                        itemCenterX - 50,
                        btnY - 20,
                        100, 20,
                        Component.translatable("gui.herobrine_companion.skin.select_file"),
                        button -> {
                            openFileChooser();
                        },
                        null
                );
                this.addRenderableWidget(fileBtn);

                Button confirmBtn = new HeroScreen.ThemedButton(
                        itemCenterX - 40,
                        btnY + 5,
                        80, 20,
                        Component.translatable("gui.herobrine_companion.confirm"),
                        button -> {
                            PacketHandler.sendToServer(new ToggleSkinPacket(this.entityId, HeroEntity.SKIN_CUSTOM, this.customSkinName));
                        },
                        null
                );
                this.addRenderableWidget(confirmBtn);
            } else {
                Button selectBtn = new HeroScreen.ThemedButton(
                        itemCenterX - 40,
                        btnY,
                        80, 20,
                        Component.translatable(isSelected ? "gui.herobrine_companion.skin.selected" : "gui.herobrine_companion.skin.select"),
                        button -> {
                            if (this.selectedSkinId != option.variantId) {
                                this.selectedSkinId = option.variantId;
                                if (option.variantId != HeroEntity.SKIN_CUSTOM) {
                                    PacketHandler.sendToServer(new ToggleSkinPacket(this.entityId, option.variantId));
                                }
                                this.rebuildWidgets();
                            }
                        },
                        null
                );
                selectBtn.active = !isSelected;
                this.addRenderableWidget(selectBtn);
            }
        }

        if (skinOptions.size() > ITEMS_PER_PAGE) {
            if (currentPage > 0) {
                this.addRenderableWidget(new HeroScreen.ThemedButton(
                        startX + 10, centerY, 20, 20, Component.literal("<"),
                        b -> { currentPage--; rebuildWidgets(); }, null
                ));
            }

            if (endIndex < skinOptions.size()) {
                this.addRenderableWidget(new HeroScreen.ThemedButton(
                        startX + PANEL_WIDTH - 30, centerY, 20, 20, Component.literal(">"),
                        b -> { currentPage++; rebuildWidgets(); }, null
                ));
            }
        }

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                centerX - 40,
                startY + PANEL_HEIGHT - 25,
                80, 20,
                Component.translatable("gui.herobrine_companion.back"),
                button -> {
                    this.onClose();
                    Minecraft.getInstance().setScreen(new HeroScreen(this.entityId));
                },
                null
        ));
    }

    private void openFileChooser() {
        new Thread(() -> {
            try {
                String result = null;

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    PointerBuffer filters = stack.mallocPointer(1);
                    filters.put(stack.UTF8("*.png"));
                    filters.flip();

                    result = TinyFileDialogs.tinyfd_openFileDialog(
                            "Select Skin Texture",
                            "",
                            filters,
                            "PNG Images",
                            false
                    );
                } catch (Throwable t) {
                    t.printStackTrace();
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.displayClientMessage(Component.literal("Error: LWJGL TinyFileDialogs not available. Please paste path manually."), false);
                        }
                    });
                }

                if (result != null) {
                    String filePath = result;
                    Minecraft.getInstance().execute(() -> {
                        this.customSkinName = filePath;
                        if (this.customSkinInput != null) {
                            this.customSkinInput.setValue(filePath);
                        }
                        for (SkinOption option : skinOptions) {
                            if (option.variantId == HeroEntity.SKIN_CUSTOM && option.dummyEntity != null) {
                                option.dummyEntity.setCustomSkinName(filePath);
                            }
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "SkinFileChooserThread").start();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1.21.1: renderBackground 需要这几个额外参数来正确渲染暗色遮罩
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startX = centerX - PANEL_WIDTH / 2;
        int startY = centerY - PANEL_HEIGHT / 2;

        guiGraphics.fill(startX, startY, startX + PANEL_WIDTH, startY + PANEL_HEIGHT, COL_BG_MAIN);
        guiGraphics.renderOutline(startX, startY, PANEL_WIDTH, PANEL_HEIGHT, COL_BORDER);

        guiGraphics.drawCenteredString(this.font, this.title, centerX, startY + 10, COL_TITLE);

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, skinOptions.size());
        int slotWidth = PANEL_WIDTH / ITEMS_PER_PAGE;

        for (int i = startIndex; i < endIndex; i++) {
            SkinOption option = skinOptions.get(i);
            int relativeIndex = i - startIndex;
            int itemCenterX = startX + slotWidth * relativeIndex + slotWidth / 2;

            guiGraphics.drawCenteredString(this.font, Component.translatable(option.nameKey), itemCenterX, startY + 30, 0xFFFFFF);

            if (option.dummyEntity != null) {
                renderEntity(guiGraphics, itemCenterX, startY + 140, 55, mouseX, mouseY, option.dummyEntity);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderEntity(GuiGraphics guiGraphics, int x, int y, int scale, int mouseX, int mouseY, HeroEntity entity) {
        float lookX = (float)x - mouseX;
        float lookY = (float)(y - 70) - mouseY;

        float f = (float)Math.atan(lookX / 40.0F);
        float f1 = (float)Math.atan(lookY / 40.0F);
        entity.yBodyRotO = 180.0F + f * 20.0F;
        entity.yRotO = 180.0F + f * 40.0F;
        entity.xRotO = -f1 * 20.0F;
        entity.yHeadRotO = entity.yRotO;

        guiGraphics.pose().pushPose();
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics,
                x - scale,              // x1: 渲染边界左侧
                y - scale * 2,          // y1: 渲染边界顶部
                x + scale,              // x2: 渲染边界右侧
                y,                      // y2: 渲染边界底部 (实体的脚部基准线)
                scale,                  // scale: 缩放比例
                0.0625F,                // yOffset: 1.21.1 原版标准的Y轴微调值
                mouseX,                 // 原始鼠标X (无需传 lookX)
                mouseY,                 // 原始鼠标Y (无需传 lookY)
                entity                  // 目标实体
        );
        guiGraphics.pose().popPose();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 留空：禁用原版自带的世界模糊和黑色背景遮罩，保持和 HeroScreen 视觉一致
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}