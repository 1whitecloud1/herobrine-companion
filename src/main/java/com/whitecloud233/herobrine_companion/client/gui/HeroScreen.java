package com.whitecloud233.herobrine_companion.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.whitecloud233.herobrine_companion.client.event.ClientHooks;
import com.whitecloud233.herobrine_companion.client.gui.crosschat.CrossSessionHubScreen;
import com.whitecloud233.herobrine_companion.client.service.ConversationStore;
import com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.event.ModEvents;
import com.whitecloud233.herobrine_companion.network.*;

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

    // [新增] 挑战难度状态：0=简单, 1=普通, 2=困难
    private int challengeMode = 0;

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
        if (this.minecraft.level != null) {
            this.dummyHero = ModEvents.HERO.get().create(this.minecraft.level);
            // 同步皮肤状态到 dummyHero 以便预览
            Entity realEntity = this.minecraft.level.getEntity(this.entityId);
            if (realEntity instanceof HeroEntity realHero) {
                this.dummyHero.setSkinVariant(realHero.getSkinVariant());
                this.dummyHero.setBattleModeActive(realHero.isBattleModeActive());
                if (realHero.getSkinVariant() == HeroEntity.SKIN_CUSTOM) {
                    this.dummyHero.setCustomSkinName(realHero.getCustomSkinName());
                }
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

        // 皮肤切换按钮 (左上角)
        int skinBtnX = startX + 4;
        int skinBtnY = startY + 4;
        Button skinBtn = new ThemedButton(
                skinBtnX,
                skinBtnY,
                90, 16,
                Component.translatable("gui.herobrine_companion.change_skin"),
                button -> {
                    Minecraft.getInstance().setScreen(new HeroSkinScreen(this.entityId));
                },
                Tooltip.create(Component.translatable("gui.herobrine_companion.switch_skin_tooltip"))
        );
        this.addRenderableWidget(skinBtn);

        this.actionList = new HeroActionList(this.minecraft, editorWidth - 10, PANEL_HEIGHT - topBarHeight - bottomBarHeight - 10, startY + topBarHeight + 5, 24);
        this.actionList.setX(editorX + 5);

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
            visited = this.minecraft.player.getPersistentData().getBoolean("HasVisitedHeroDimension");
        }

        // Chat Action
        this.actionList.addDynamicAction(() -> Component.translatable(ClientHooks.isApiEnabled() ? "gui.herobrine_companion.chat_cloud" : "gui.herobrine_companion.chat_local"), button -> {
            String activeConversationTitle = null;
            if (this.minecraft != null && this.minecraft.player != null) {
                ConversationStore.getInstance().ensureActiveConversation(this.minecraft.player.getUUID());
                activeConversationTitle = ConversationStore.getInstance().getActiveConversationTitle(this.minecraft.player.getUUID());
            }
            this.onClose();
            ClientHooks.enableChat();
            Minecraft.getInstance().setScreen(new HeroChatScreen(""));
            String msgKey = ClientHooks.isApiEnabled() ? "message.herobrine_companion.system_cloud_connected" : "message.herobrine_companion.system_local_mode";
            Minecraft.getInstance().gui.getChat().addMessage(Component.translatable(msgKey));
            if (ClientHooks.isApiEnabled() && activeConversationTitle != null) {
                Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("message.herobrine_companion.chat_active_conversation", activeConversationTitle));
            }
        }, null);

        this.actionList.addAction(Component.translatable("gui.herobrine_companion.chat_conversations"), button -> {
            Minecraft.getInstance().setScreen(new ConversationManagerScreen(this.entityId));
            }, Tooltip.create(Component.translatable("gui.herobrine_companion.chat_conversations_tooltip")));

        this.actionList.addAction(Component.translatable("gui.herobrine_companion.cross_chat.manage"), button -> {
            Minecraft.getInstance().setScreen(new CrossSessionHubScreen(this.entityId));
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.cross_chat.manage_tooltip")));


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

        // 装扮(衣柜)按钮
        this.actionList.addAction(Component.translatable("gui.herobrine_companion.wardrobe"), button -> {
            PacketHandler.sendToServer(new OpenWardrobePacket(this.entityId));
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.wardrobe_tooltip")));

        // 委托按钮
        this.actionList.addAction(Component.translatable("gui.herobrine_companion.requests"), button -> {
            Minecraft.getInstance().setScreen(new HeroRequestScreen(this.entityId));
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.requests_tooltip")));

        // 奖励按钮
        this.actionList.addAction(Component.translatable("gui.herobrine_companion.rewards"), button -> {
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
        this.actionList.addDynamicAction(() -> {
            boolean currentState = false;
            boolean challengeActive = false;
            if (this.minecraft.level != null) {
                Entity e = this.minecraft.level.getEntity(this.entityId);
                if (e instanceof HeroEntity h) {
                    currentState = h.isBattleModeActive();
                    challengeActive = h.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE);
                }
            }

            String key;
            if (challengeActive) {
                key = "gui.herobrine_companion.battle_mode_blocked";
            } else {
                key = currentState
                        ? "gui.herobrine_companion.battle_mode_disable"
                        : "gui.herobrine_companion.battle_mode_enable";
            }

            return Component.translatable(key).withStyle(style -> style.withColor(0xFF4FC3F7));
        }, button -> {
            PacketHandler.sendToServer(new ToggleBattleModePacket(this.entityId));
            Entity entity = this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getEntity(this.entityId) : null;
            boolean challengeActive = entity instanceof HeroEntity hero && hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE);
            if (this.dummyHero != null && !challengeActive) {
                this.dummyHero.setBattleModeActive(!this.dummyHero.isBattleModeActive());
            }
        }, Tooltip.create(Component.translatable(
                HeroEpicFightCompat.isLoaded()
                        ? "gui.herobrine_companion.battle_mode_tooltip"
                        : HeroEpicFightCompat.getInstallHintKey()
        )));

        // 清除障碍按钮
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

        // 平整地形按钮
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
// [新增] 姿势编辑按钮
        this.actionList.addAction(Component.translatable("gui.herobrine_companion.pose_editor"), button -> {
            // 打开 3D 姿势调节界面
            Minecraft.getInstance().setScreen(new HeroPoseScreen(this.entityId));
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.pose_editor_tooltip")));

        // ==========================================
        // [新增] 挑战模式功能块
        // ==========================================

        // 挑战难度切换按钮
        this.actionList.addDynamicAction(() -> {
            String modeKey = this.challengeMode == 0 ? "gui.herobrine_companion.challenge_easy" :
                    (this.challengeMode == 1 ? "gui.herobrine_companion.challenge_normal" : "gui.herobrine_companion.challenge_hard");

            return Component.translatable("gui.herobrine_companion.challenge_mode")
                    .append(": ")
                    .append(Component.translatable(modeKey))
                    .withStyle(style -> style.withColor(0xFF55FF55));
        }, button -> {
            // 点击循环切换：0(简单) -> 1(普通) -> 2(困难)
            this.challengeMode = (this.challengeMode + 1) % 3;
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.challenge_mode_tooltip")));

        // 确认开始挑战按钮
        this.actionList.addAction(
                Component.translatable("gui.herobrine_companion.challenge_start").withStyle(style -> style.withColor(0xFFFF5555)),
                button -> {
                    // 使用 1.21.1 NeoForge 的 PacketDistributor 发送数据包
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new StartChallengePacket(this.entityId, this.challengeMode));
                    this.onClose();
                },
                Tooltip.create(Component.translatable("gui.herobrine_companion.challenge_start_tooltip"))
        );
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1.21.1 留空：禁用原版自带的世界模糊和黑色背景遮罩
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

        // [修改]
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.dashboard_title"), startX + sideBarWidth + 10, startY + 8, COL_TEXT_MAIN, false);

        // --- 左侧信息栏 ---
        guiGraphics.fill(startX + 5, startY + topBarHeight + 5, startX + sideBarWidth - 5, startY + topBarHeight + 95, 0xFF1E1E1E);

        int varY = startY + topBarHeight + 105;
        int lineHeight = 10;
        int indent = startX + 5;

        // [修改]
        drawInfoLabel(guiGraphics, indent, varY, Component.translatable("gui.herobrine_companion.target"), Component.translatable("gui.herobrine_companion.target_name"));

        int trust = 0;
        UUID uuid = null;
        boolean battleMode = false;
        String epicFightDisplay = HeroEpicFightCompat.getBridgeStatus().name();

        if (this.minecraft.level != null) {
            Entity realEntity = this.minecraft.level.getEntity(this.entityId);
            if (realEntity instanceof HeroEntity hero) {
                trust = hero.getTrustLevel();
                uuid = hero.getUUID();
                battleMode = hero.isBattleModeActive();
                if (battleMode) {
                    epicFightDisplay = HeroEpicFightCompat.getBridgeStatus().name() + " " + HeroEpicFightCompat.describeCurrentSnapshot(hero);
                }
                // 【核心修复】：持续刷新渲染时的皮肤与姿势状态
                this.dummyHero.setSkinVariant(hero.getSkinVariant());
                this.dummyHero.setBattleModeActive(hero.isBattleModeActive());
                if (hero.getSkinVariant() == HeroEntity.SKIN_CUSTOM) {
                    this.dummyHero.setCustomSkinName(hero.getCustomSkinName());
                }
                this.dummyHero.isPoseEditing = hero.isPoseEditing;
                if (hero.isPoseEditing) {
                    for (int i = 0; i < 10; i++) {
                        System.arraycopy(hero.customPoseAngles[i], 0, this.dummyHero.customPoseAngles[i], 0, 3);
                    }
                }
            }
        }


        // 👇 就是这里！把你之前漏掉的声明和绘制字段的代码补上
        drawInfoField(guiGraphics, indent + 5, varY + lineHeight, Component.translatable("gui.herobrine_companion.trust_level"), Component.literal(String.valueOf(trust)));
        drawInfoField(guiGraphics, indent + 5, varY + lineHeight * 2, Component.translatable("gui.herobrine_companion.battle_mode_status"), Component.translatable(battleMode ? "gui.herobrine_companion.battle_mode_status_on" : "gui.herobrine_companion.battle_mode_status_off"));
        drawInfoField(guiGraphics, indent + 5, varY + lineHeight * 3, Component.translatable("gui.herobrine_companion.active_time"), Component.literal(this.dummyHero.tickCount + "").append(Component.translatable("gui.herobrine_companion.ticks")));
        drawInfoField(guiGraphics, indent + 5, varY + lineHeight * 4, Component.translatable("gui.herobrine_companion.epicfight_bridge"), Component.literal(epicFightDisplay));
        drawInfoField(guiGraphics, indent + 5, varY + lineHeight * 5, Component.translatable("gui.herobrine_companion.entity_id"), Component.literal(uuid == null ? "N/A" : "..." + uuid.toString().substring(0, 4)));

        int barY = varY + lineHeight * 6 + 5;
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.sync_status"), indent, barY, COL_INFO, false);
        int maxTrust = 100;
        float progress = Math.min(1.0f, (float)trust / maxTrust);
        int barWidth = sideBarWidth - 10;
        guiGraphics.fill(indent, barY + 10, indent + barWidth, barY + 14, 0xFF555555);
        int color = trust < 30 ? 0xFFFF5555 : (trust < 70 ? 0xFFFFFF55 : 0xFF55FF55);
        guiGraphics.fill(indent, barY + 10, indent + (int)(barWidth * progress), barY + 14, color);

        // --- 右侧主区域 ---
        int mainAreaX = startX + sideBarWidth + 5;
        int mainAreaY = startY + topBarHeight + 5;

        // [修改]
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.available_actions"), mainAreaX, mainAreaY, COL_LABEL, false);
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

    // [修改] 参数从 String 变更为 Component
    private void drawInfoLabel(GuiGraphics g, int x, int y, Component label, Component value) {
        g.drawString(this.font, label, x, y, COL_LABEL, false);
        g.drawString(this.font, Component.literal(" ").append(value), x + this.font.width(label), y, COL_TEXT_MAIN, false);
    }

    // [修改] 参数从 String 变更为 Component
    private void drawInfoField(GuiGraphics g, int x, int y, Component name, Component value) {
        g.drawString(this.font, name, x, y, COL_VALUE, false);
        g.drawString(this.font, ": ", x + this.font.width(name), y, COL_TEXT_MAIN, false);
        g.drawString(this.font, value, x + this.font.width(name) + 10, y, COL_INFO, false);
    }

    private void renderEntityWithMouseFollow(GuiGraphics guiGraphics, int x, int y, int scale, float mouseX, float mouseY, HeroEntity entity) {
        // 计算旋转角
        float f = (float)Math.atan(mouseX / 40.0F);
        float f1 = (float)Math.atan(mouseY / 40.0F);

        // 1.21.1 设置旋转四元数 (JOML)
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

        // 1.21.1 核心：使用新的 renderEntityInInventory 签名
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
        // 配色方案
        private static final int BG_NORMAL = 0xFF3C3F41; // 正常背景
        private static final int BG_HOVER  = 0xFF4C5052; // 悬停背景 (稍亮)
        private static final int BORDER    = 0xFF555555; // 边框颜色
        private static final int TEXT_COL  = 0xFFA9B7C6; // 文字颜色

        public ThemedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        public ThemedButton(int x, int y, int width, int height, Component message, OnPress onPress, Tooltip tooltip) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.setTooltip(tooltip);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // 1. 判断是否悬停
            boolean hovered = this.isHoveredOrFocused();
            int bgColor = hovered ? BG_HOVER : BG_NORMAL;

            // 2. 绘制背景矩形
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

            // 3. 绘制边框
            guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, BORDER);

            // 4. 绘制文字 (居中)
            int defaultColor = hovered ? 0xFFFFFFFF : TEXT_COL;
            int colorToUse = this.getMessage().getStyle().getColor() != null ? this.getMessage().getStyle().getColor().getValue() : defaultColor;

            // 如果悬停且原色不是指定颜色，稍微提亮
            if (hovered && this.getMessage().getStyle().getColor() == null) {
                colorToUse = 0xFFFFFFFF;
            }

            guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, colorToUse);
        }
    }
}