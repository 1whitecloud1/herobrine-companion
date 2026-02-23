package com.whitecloud233.herobrine_companion.client.gui;
import com.whitecloud233.herobrine_companion.network.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.whitecloud233.herobrine_companion.client.ClientHooks;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.event.ModEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

public class HeroScreen extends Screen {

    private final int entityId;
    private HeroEntity dummyHero;
    private HeroActionList actionList;

    private boolean confirmingVoid = false;
    private long confirmTime = 0;

    private boolean confirmingDesolate = false;
    private long confirmDesolateTime = 0;

    private boolean confirmingFlatten = false;
    private long confirmFlattenTime = 0;

    // 配色方案
    private static final int COL_BG_MAIN    = 0xFF2B2B2B;
    private static final int COL_BG_SIDE    = 0xFF3C3F41;
    private static final int COL_BORDER     = 0xFF555555;
    private static final int COL_TEXT_MAIN  = 0xFFA9B7C6;
    private static final int COL_LABEL      = 0xFFCC7832;
    private static final int COL_VALUE      = 0xFF9876AA;
    private static final int COL_INFO       = 0xFF6A8759;

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 210;

    public HeroScreen(int entityId) {
        super(Component.translatable("gui.herobrine_companion.title"));
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        if (this.minecraft == null) return;
        super.init();
        int currentSkin = HeroEntity.SKIN_AUTO;
        if (this.minecraft.level != null) {
            this.dummyHero = ModEvents.HERO.get().create(this.minecraft.level);
            // 同步皮肤状态到 dummyHero 以便预览
            Entity realEntity = this.minecraft.level.getEntity(this.entityId);
            if (realEntity instanceof HeroEntity realHero) {
                this.dummyHero.setSkinVariant(realHero.getSkinVariant());
                currentSkin = realHero.getSkinVariant();
            }
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startX = centerX - PANEL_WIDTH / 2;
        int startY = centerY - PANEL_HEIGHT / 2;

        int sideBarWidth = 100;
        int editorX = startX + sideBarWidth;
        int editorWidth = PANEL_WIDTH - sideBarWidth;
        int topBarHeight = 25;
        int bottomBarHeight = 20;

        int btnX = startX + PANEL_WIDTH - 50;
        int btnY = startY + 4;

        // API Toggle Button
        Button apiBtn = new ThemedButton(
                btnX, btnY, 46, 16,
                Component.translatable(ClientHooks.isApiEnabled() ? "gui.herobrine_companion.run_cloud" : "gui.herobrine_companion.run_local"),
                button -> {
                    ClientHooks.toggleApiEnabled();
                    button.setMessage(Component.translatable(ClientHooks.isApiEnabled() ? "gui.herobrine_companion.run_cloud" : "gui.herobrine_companion.run_local"));
                },
                Tooltip.create(Component.translatable("gui.herobrine_companion.api_tooltip"))
        );
        this.addRenderableWidget(apiBtn);

        // [新增] 皮肤切换按钮 (左上角)
        int skinBtnX = startX + 4;
        int skinBtnY = startY + 4;
        int finalCurrentSkin = currentSkin;
        Button skinBtn = new ThemedButton(
                skinBtnX, skinBtnY, 90, 16,
                Component.translatable(finalCurrentSkin == HeroEntity.SKIN_HEROBRINE ? "gui.herobrine_companion.switch_skin_hero" : "gui.herobrine_companion.switch_skin_herobrine"),
                button -> {
                    int newSkin = (finalCurrentSkin == HeroEntity.SKIN_HEROBRINE) ? HeroEntity.SKIN_HERO : HeroEntity.SKIN_HEROBRINE;
                    PacketHandler.sendToServer(new SwitchSkinPacket(this.entityId, newSkin));
                    if (this.dummyHero != null) {
                        this.dummyHero.setSkinVariant(newSkin);
                    }
                    this.onClose();
                },
                Tooltip.create(Component.translatable("gui.herobrine_companion.switch_skin_tooltip"))
        );
        this.addRenderableWidget(skinBtn);

        this.actionList = new HeroActionList(this.minecraft, editorWidth - 10, PANEL_HEIGHT - topBarHeight - bottomBarHeight - 10, startY + topBarHeight + 5, 24);
        this.actionList.setX(editorX + 5); // 1.21 change: setLeftPos -> setX usually, or keep setLeftPos if implemented in custom list

        populateActionList();
        this.addRenderableWidget(this.actionList);

        this.addRenderableWidget(new ThemedButton(
                editorX + editorWidth - 85, startY + PANEL_HEIGHT - 18, 80, 16,
                Component.translatable("gui.herobrine_companion.leave"),
                button -> this.onClose()
        ));
    }

    private void populateActionList() {
        if (this.minecraft == null) return;
        boolean visited = false;

        if (this.minecraft.player != null) {
            // [Fix] 使用 getPersistentData() 获取 HasVisitedHeroDimension 标记
            // 注意：在客户端，getPersistentData() 可能不会自动同步，需要服务端发包同步
            // 我们之前在 HeroDimensionHandler 中已经添加了 SyncHeroVisitPacket
            // 所以这里应该能读到同步后的数据
            visited = this.minecraft.player.getPersistentData().getBoolean("HasVisitedHeroDimension");
        }

        // Chat Action
        this.actionList.addDynamicAction(() -> Component.translatable(ClientHooks.isApiEnabled() ? "gui.herobrine_companion.chat_cloud" : "gui.herobrine_companion.chat_local"), button -> {
            this.onClose();
            ClientHooks.enableChat();
            Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
            String msgKey = ClientHooks.isApiEnabled() ? "message.herobrine_companion.system_cloud_connected" : "message.herobrine_companion.system_local_mode";
            Minecraft.getInstance().gui.getChat().addMessage(Component.translatable(msgKey));
        }, null);

        boolean protectionUnlocked = visited;

        // Protection Toggle
        this.actionList.addDynamicAction(() -> {
            boolean isProtected = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.getTags().contains("herobrine_companion_peaceful");
            if (!protectionUnlocked) {
                return Component.translatable(isProtected ? "gui.herobrine_companion.disable_protection_locked" : "gui.herobrine_companion.enable_protection_locked").withStyle(style -> style.withColor(0xFF808080));
            }
            return Component.translatable(isProtected ? "gui.herobrine_companion.disable_protection" : "gui.herobrine_companion.enable_protection");
        }, button -> {
            boolean isProtected = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.getTags().contains("herobrine_companion_peaceful");
            
            // [核心修复] 在客户端手动同步这个 Tag 的状态，保持两端一致
            if (this.minecraft != null && this.minecraft.player != null) {
                if (isProtected) {
                    this.minecraft.player.removeTag("herobrine_companion_peaceful");
                } else {
                    this.minecraft.player.addTag("herobrine_companion_peaceful");
                }
            }
            
            PacketHandler.sendToServer(new PeacefulPacket(!isProtected));
            this.onClose();
        }, Tooltip.create(Component.translatable(protectionUnlocked ? "gui.herobrine_companion.protection_tooltip" : "gui.herobrine_companion.protection_locked_tooltip"))).active = protectionUnlocked;

        this.actionList.addAction(Component.translatable("gui.herobrine_companion.trade"), button -> {
            PacketHandler.sendToServer(new OpenTradePacket(this.entityId));
            this.onClose();
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.trade_tooltip")));
        // [新增] 委托按钮
        this.actionList.addAction(Component.translatable("gui.herobrine_companion.requests"), button -> {
            // 打开委托界面
            Minecraft.getInstance().setScreen(new HeroRequestScreen(this.entityId));
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.requests_tooltip")));

        // [新增] 奖励按钮
        this.actionList.addAction(Component.translatable("gui.herobrine_companion.rewards"), button -> {
            // 打开奖励界面
            Minecraft.getInstance().setScreen(new HeroRewardScreen(this.entityId));
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.rewards_tooltip")));


        int currentTrust = 0;
        if (this.minecraft.level != null) {
            Entity entity = this.minecraft.level.getEntity(this.entityId);
            if (entity instanceof HeroEntity hero) {
                currentTrust = hero.getTrustLevel();
                hero.isCompanionMode();
            }
        }

        // Companion Mode
        boolean companionUnlocked = currentTrust >= 50;
        int finalCurrentTrust = currentTrust;
        this.actionList.addDynamicAction(() -> {
            boolean currentState = false;
            if (this.minecraft.level != null) {
                Entity e = this.minecraft.level.getEntity(this.entityId);
                if (e instanceof HeroEntity h) {
                    currentState = h.isCompanionMode();
                }
            }

            if (!companionUnlocked) {
                return Component.translatable("gui.herobrine_companion.companion_locked", 50, finalCurrentTrust).withStyle(style -> style.withColor(0xFF808080));
            }

            String key = currentState ? "gui.herobrine_companion.companion_disable" : "gui.herobrine_companion.companion_enable";
            return Component.translatable(key).withStyle(style -> style.withColor(0xFFFFC66D));
        }, button -> {
            PacketHandler.sendToServer(new ToggleCompanionPacket(this.entityId));
            this.onClose();
        }, Tooltip.create(Component.translatable(companionUnlocked ? "gui.herobrine_companion.companion_tooltip_unlocked" : "gui.herobrine_companion.companion_tooltip_locked", currentTrust))).active = companionUnlocked;

        boolean finalVisited = visited;
        this.actionList.addDynamicAction(() -> {
            if (!finalVisited) {
                return Component.translatable("gui.herobrine_companion.create_void_domain_locked").withStyle(style -> style.withColor(0xFF808080));
            }
            return confirmingVoid ? Component.translatable("gui.herobrine_companion.confirm_void").withStyle(ChatFormatting.WHITE)
                    : Component.translatable("gui.herobrine_companion.create_void_domain");
        }, button -> {
            if (confirmingVoid) {
                PacketHandler.sendToServer(new ClearAreaPacket());
                this.onClose();
            } else {
                confirmingVoid = true;
                confirmTime = System.currentTimeMillis();
            }
        }, Tooltip.create(Component.translatable(visited ? "gui.herobrine_companion.void_warning" : "gui.herobrine_companion.void_locked_tooltip"))).active = visited;

        // [新增] 清除障碍按钮
        boolean desolateUnlocked = currentTrust >= 70;
        this.actionList.addDynamicAction(() -> {
            if (!desolateUnlocked) {
                return Component.translatable("gui.herobrine_companion.desolate_area_locked", 70, finalCurrentTrust).withStyle(style -> style.withColor(0xFF808080));
            }
            return confirmingDesolate ? Component.translatable("gui.herobrine_companion.confirm_desolate").withStyle(ChatFormatting.WHITE)
                    : Component.translatable("gui.herobrine_companion.desolate_area");
        }, button -> {
            if (confirmingDesolate) {
                PacketHandler.sendToServer(new DesolateAreaPacket(this.entityId));
                this.onClose();
            } else {
                confirmingDesolate = true;
                confirmDesolateTime = System.currentTimeMillis();
            }
        }, Tooltip.create(Component.translatable(desolateUnlocked ? "gui.herobrine_companion.desolate_warning" : "gui.herobrine_companion.desolate_locked_trust_tooltip", currentTrust))).active = desolateUnlocked;
        // [新增] 平整地形按钮
        boolean flattenUnlocked = currentTrust >= 50;
        this.actionList.addDynamicAction(() -> {
            if (!flattenUnlocked) {
                return Component.translatable("gui.herobrine_companion.flatten_area_locked", 50, finalCurrentTrust).withStyle(style -> style.withColor(0xFF808080));
            }
            return confirmingFlatten ? Component.translatable("gui.herobrine_companion.confirm_flatten").withStyle(ChatFormatting.WHITE)
                    : Component.translatable("gui.herobrine_companion.flatten_area");
        }, button -> {
            if (confirmingFlatten) {
                PacketHandler.sendToServer(new FlattenAreaPacket(this.entityId));
                this.onClose();
            } else {
                confirmingFlatten = true;
                confirmFlattenTime = System.currentTimeMillis();
            }
        }, Tooltip.create(Component.translatable(flattenUnlocked ? "gui.herobrine_companion.flatten_warning" : "gui.herobrine_companion.flatten_locked_trust_tooltip", currentTrust))).active = flattenUnlocked;
    }



    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 留空：禁用原版自带的世界模糊和黑色背景遮罩
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null) return;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (confirmingVoid && System.currentTimeMillis() - confirmTime > 3000) {
            confirmingVoid = false;
        }

        if (confirmingDesolate && System.currentTimeMillis() - confirmDesolateTime > 3000) {
            confirmingDesolate = false;
        }

        if (confirmingFlatten && System.currentTimeMillis() - confirmFlattenTime > 3000) {
            confirmingFlatten = false;
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startX = centerX - PANEL_WIDTH / 2;
        int startY = centerY - PANEL_HEIGHT / 2;

        int sideBarWidth = 100;
        int topBarHeight = 25;
        int bottomBarHeight = 20;

        // --- 背景绘制 ---
        guiGraphics.fill(startX + sideBarWidth, startY + topBarHeight, startX + PANEL_WIDTH, startY + PANEL_HEIGHT - bottomBarHeight, COL_BG_MAIN);
        guiGraphics.fill(startX, startY + topBarHeight, startX + sideBarWidth, startY + PANEL_HEIGHT - bottomBarHeight, COL_BG_SIDE);
        guiGraphics.fill(startX, startY, startX + PANEL_WIDTH, startY + topBarHeight, COL_BG_SIDE);
        guiGraphics.fill(startX, startY + PANEL_HEIGHT - bottomBarHeight, startX + PANEL_WIDTH, startY + PANEL_HEIGHT, COL_BG_SIDE);

        guiGraphics.renderOutline(startX, startY, PANEL_WIDTH, PANEL_HEIGHT, COL_BORDER);
        guiGraphics.fill(startX + sideBarWidth, startY + topBarHeight, startX + sideBarWidth + 1, startY + PANEL_HEIGHT - bottomBarHeight, COL_BORDER);

        // --- 顶部标题栏 ---
        int tabWidth = 140;
        guiGraphics.fill(startX + sideBarWidth, startY, startX + sideBarWidth + tabWidth, startY + topBarHeight - 2, COL_BG_MAIN);
        guiGraphics.fill(startX + sideBarWidth, startY, startX + sideBarWidth + tabWidth, startY + 2, 0xFF4A88C7);

        guiGraphics.drawString(this.font, "Control Dashboard", startX + sideBarWidth + 10, startY + 8, COL_TEXT_MAIN, false);

        // --- 左侧信息栏 ---
        guiGraphics.fill(startX + 5, startY + topBarHeight + 5, startX + sideBarWidth - 5, startY + topBarHeight + 95, 0xFF1E1E1E);

        int varY = startY + topBarHeight + 105;
        int lineHeight = 10;
        int indent = startX + 5;

        drawInfoLabel(guiGraphics, indent, varY, "Target:", "Herobrine");

        int trust = 0;
        UUID uuid = null;
        if (this.minecraft.level != null) {
            Entity realEntity = this.minecraft.level.getEntity(this.entityId);
            if (realEntity instanceof HeroEntity hero) {
                trust = hero.getTrustLevel();
                uuid = hero.getUUID();
            }
        }

        drawInfoField(guiGraphics, indent + 5, varY + lineHeight, "TrustLevel", String.valueOf(trust));
        drawInfoField(guiGraphics, indent + 5, varY + lineHeight * 2, "Active Time", this.dummyHero.tickCount + " ticks");
        drawInfoField(guiGraphics, indent + 5, varY + lineHeight * 3, "Entity ID", uuid == null ? "N/A" : "..." + uuid.toString().substring(0, 4));

        // 信任条
        int barY = varY + lineHeight * 4 + 5;
        guiGraphics.drawString(this.font, "Sync Status:", indent, barY, COL_INFO, false);
        int maxTrust = 100;
        float progress = Math.min(1.0f, (float)trust / maxTrust);
        int barWidth = sideBarWidth - 10;
        guiGraphics.fill(indent, barY + 10, indent + barWidth, barY + 14, 0xFF555555);
        int color = trust < 30 ? 0xFFFF5555 : (trust < 70 ? 0xFFFFFF55 : 0xFF55FF55);
        guiGraphics.fill(indent, barY + 10, indent + (int)(barWidth * progress), barY + 14, color);

        // --- 右侧主区域 ---
        int mainAreaX = startX + sideBarWidth + 5;
        int mainAreaY = startY + topBarHeight + 5;

        guiGraphics.drawString(this.font, "Available Actions", mainAreaX, mainAreaY, COL_LABEL, false);
        guiGraphics.fill(mainAreaX, mainAreaY + 10, startX + PANEL_WIDTH - 5, mainAreaY + 11, COL_BORDER);

        // --- 实体模型渲染 ---
        if (this.dummyHero != null) {
            guiGraphics.pose().pushPose();

            renderEntityWithMouseFollow(
                    guiGraphics,
                    startX + sideBarWidth / 2,
                    startY + topBarHeight + 85,
                    40,
                    (float)(startX + sideBarWidth / 2) - mouseX,
                    (float)(startY + topBarHeight + 50) - mouseY,
                    this.dummyHero
            );

            guiGraphics.pose().popPose();

            RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawInfoLabel(GuiGraphics g, int x, int y, String label, String value) {
        g.drawString(this.font, label, x, y, COL_LABEL, false);
        g.drawString(this.font, " " + value, x + this.font.width(label), y, COL_TEXT_MAIN, false);
    }

    private void drawInfoField(GuiGraphics g, int x, int y, String name, String value) {
        g.drawString(this.font, name, x, y, COL_VALUE, false);
        g.drawString(this.font, ": ", x + this.font.width(name), y, COL_TEXT_MAIN, false);
        g.drawString(this.font, value, x + this.font.width(name) + 10, y, COL_INFO, false);
    }

    private void renderEntityWithMouseFollow(GuiGraphics guiGraphics, int x, int y, int scale, float mouseX, float mouseY, HeroEntity entity) {
        // 计算旋转角
        float f = (float)Math.atan(mouseX / 40.0F);
        float f1 = (float)Math.atan(mouseY / 40.0F);

        // 设置旋转四元数
        Quaternionf quaternionf = (new Quaternionf()).rotateZ((float)Math.PI);
        Quaternionf quaternionf1 = (new Quaternionf()).rotateX(f1 * 20.0F * ((float)Math.PI / 180F));
        quaternionf.mul(quaternionf1);

        // 备份实体状态
        float f2 = entity.yBodyRot;
        float f3 = entity.getYRot();
        float f4 = entity.getXRot();
        float f5 = entity.yHeadRotO;
        float f6 = entity.yHeadRot;

        // 应用旋转
        entity.yBodyRot = 180.0F + f * 20.0F;
        entity.setYRot(180.0F + f * 40.0F);
        entity.setXRot(-f1 * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();

        // 1.21.1 核心修复：使用新的 renderEntityInInventory 签名
        // 参数：GuiGraphics, x, y, scale, translation(Vector3f), pose(Quaternionf), cameraOrientation(Quaternionf, nullable), entity(LivingEntity)
        InventoryScreen.renderEntityInInventory(
                guiGraphics,
                (float)x,
                (float)y,
                scale,
                new Vector3f(0,0,0), // Translation
                quaternionf,        // Pose
                null,               // Camera Orientation
                entity
        );

        // 恢复实体状态
        entity.yBodyRot = f2;
        entity.setYRot(f3);
        entity.setXRot(f4);
        entity.yHeadRotO = f5;
        entity.yHeadRot = f6;
    }

    @Override public boolean isPauseScreen() { return false; }

    public static class ThemedButton extends Button {
        // 配色方案 (参考了你的 COL 常量)
        private static final int BG_NORMAL = 0xFF3C3F41; // 正常背景
        private static final int BG_HOVER  = 0xFF4C5052; // 悬停背景 (稍亮)
        private static final int BORDER    = 0xFF555555; // 边框颜色
        private static final int TEXT_COL  = 0xFFA9B7C6; // 文字颜色

        public ThemedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            // 使用默认的 Narration 行为
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        public ThemedButton(int x, int y, int width, int height, Component message, OnPress onPress, Tooltip tooltip) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.setTooltip(tooltip);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // 1. 判断是否悬停
            boolean hovered = this.isHoveredOrFocused();
            int bgColor = hovered ? BG_HOVER : BG_NORMAL;

            // 2. 绘制背景矩形
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

            // 3. 绘制边框 (可选)
            guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, BORDER);

            // 4. 绘制文字 (居中)
            // 修改：如果文字样式中已经指定了颜色（例如灰色），则优先使用样式颜色，否则使用默认颜色
            int defaultColor = hovered ? 0xFFFFFFFF : TEXT_COL;
            int colorToUse = this.getMessage().getStyle().getColor() != null ? this.getMessage().getStyle().getColor().getValue() : defaultColor;
            
            // 如果悬停且原色不是白色，稍微提亮一点（可选，这里简单处理直接用原色或白色）
            if (hovered && this.getMessage().getStyle().getColor() == null) {
                colorToUse = 0xFFFFFFFF;
            }

            guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, colorToUse);
        }
    }
}