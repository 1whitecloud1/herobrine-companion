package com.whitecloud233.modid.herobrine_companion.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.ToggleSkinPacket;
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
    
    // 皮肤选项数据结构
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
    
    // 面板尺寸
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 220;
    
    // 配色
    private static final int COL_BG_MAIN    = 0xFF2B2B2B;
    private static final int COL_BORDER     = 0xFF555555;
    private static final int COL_TITLE      = 0xFFCC7832;

    // 当前选中的皮肤ID
    private int selectedSkinId = 0;
    private String customSkinName = "";
    private boolean initializedState = false;
    
    // 分页控制
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 2; // 每页显示2个皮肤

    // 自定义皮肤输入框
    private EditBox customSkinInput;

    public HeroSkinScreen(int entityId) {
        super(Component.translatable("gui.herobrine_companion.skin_title"));
        this.entityId = entityId;
        
        // 初始化皮肤列表
        skinOptions.add(new SkinOption(HeroEntity.SKIN_HEROBRINE, "gui.herobrine_companion.skin.herobrine_name"));
        skinOptions.add(new SkinOption(HeroEntity.SKIN_HERO, "gui.herobrine_companion.skin.hero_name"));
        // 添加自定义皮肤选项
        skinOptions.add(new SkinOption(HeroEntity.SKIN_CUSTOM, "gui.herobrine_companion.skin.custom_name"));
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null || this.minecraft.level == null) return;
        
        // 初始化 dummy 实体
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

        // 获取当前真实状态
        if (!initializedState) {
            Entity realEntity = this.minecraft.level.getEntity(this.entityId);
            if (realEntity instanceof HeroEntity realHero) {
                this.selectedSkinId = realHero.getSkinVariant();
                this.customSkinName = realHero.getCustomSkinName();
                // 更新 dummy 实体的自定义皮肤名
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

        // 计算当前页显示的皮肤
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, skinOptions.size());
        
        int slotWidth = PANEL_WIDTH / ITEMS_PER_PAGE;
        
        for (int i = startIndex; i < endIndex; i++) {
            SkinOption option = skinOptions.get(i);
            int relativeIndex = i - startIndex;
            int itemCenterX = startX + slotWidth * relativeIndex + slotWidth / 2;
            
            boolean isSelected = (this.selectedSkinId == option.variantId);
            
            // 如果是自定义皮肤且被选中，显示输入框和文件选择按钮
            if (option.variantId == HeroEntity.SKIN_CUSTOM && isSelected) {
                this.customSkinInput = new EditBox(this.font, itemCenterX - 50, btnY - 45, 100, 20, Component.translatable("gui.herobrine_companion.skin.custom_input"));
                this.customSkinInput.setMaxLength(256); // 允许更长的路径
                this.customSkinInput.setValue(this.customSkinName);
                this.customSkinInput.setResponder(name -> {
                    this.customSkinName = name;
                    if (option.dummyEntity != null) {
                        option.dummyEntity.setCustomSkinName(name);
                    }
                });
                this.addRenderableWidget(this.customSkinInput);
                
                // 文件选择按钮
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
                
                // 确认按钮
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
                            // 如果切换到非自定义皮肤，直接发送包
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
        
        // 分页按钮
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

        // 返回按钮
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
        // 在新线程中打开文件选择器，避免阻塞渲染线程
        new Thread(() -> {
            try {
                String result = null;
                
                // 尝试使用 LWJGL TinyFileDialogs
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
                    // 如果 TinyFileDialogs 不可用 (例如缺少库)，打印错误
                    t.printStackTrace();
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.displayClientMessage(Component.literal("Error: LWJGL TinyFileDialogs not available. Please paste path manually."), false);
                        }
                    });
                }

                if (result != null) {
                    String filePath = result;
                    // 回到主线程更新UI
                    Minecraft.getInstance().execute(() -> {
                        this.customSkinName = filePath;
                        if (this.customSkinInput != null) {
                            this.customSkinInput.setValue(filePath);
                        }
                        // 更新 dummy 实体
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
        this.renderBackground(guiGraphics);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startX = centerX - PANEL_WIDTH / 2;
        int startY = centerY - PANEL_HEIGHT / 2;

        // 背景
        guiGraphics.fill(startX, startY, startX + PANEL_WIDTH, startY + PANEL_HEIGHT, COL_BG_MAIN);
        guiGraphics.renderOutline(startX, startY, PANEL_WIDTH, PANEL_HEIGHT, COL_BORDER);

        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, centerX, startY + 10, COL_TITLE);

        // 渲染当前页的皮肤预览
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, skinOptions.size());
        int slotWidth = PANEL_WIDTH / ITEMS_PER_PAGE;

        for (int i = startIndex; i < endIndex; i++) {
            SkinOption option = skinOptions.get(i);
            int relativeIndex = i - startIndex;
            int itemCenterX = startX + slotWidth * relativeIndex + slotWidth / 2;
            
            // 皮肤名称
            guiGraphics.drawCenteredString(this.font, Component.translatable(option.nameKey), itemCenterX, startY + 30, 0xFFFFFF);
            
            // 实体预览
            if (option.dummyEntity != null) {
                renderEntity(guiGraphics, itemCenterX, startY + 140, 55, mouseX, mouseY, option.dummyEntity);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderEntity(GuiGraphics guiGraphics, int x, int y, int scale, int mouseX, int mouseY, HeroEntity entity) {
        float lookX = (float)x - mouseX;
        float lookY = (float)(y - 70) - mouseY;

        // 修复 Dummy 实体的 "驱魔人" 扭曲 Bug
        float f = (float)Math.atan(lookX / 40.0F);
        float f1 = (float)Math.atan(lookY / 40.0F);
        entity.yBodyRotO = 180.0F + f * 20.0F;
        entity.yRotO = 180.0F + f * 40.0F;
        entity.xRotO = -f1 * 20.0F;
        entity.yHeadRotO = entity.yRotO;

        guiGraphics.pose().pushPose();
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics,
                x,
                y,
                scale,
                lookX,
                lookY,
                entity
        );
        guiGraphics.pose().popPose();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}