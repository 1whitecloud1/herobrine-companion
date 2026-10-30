package com.whitecloud233.herobrine_companion.client.gui;

import com.whitecloud233.herobrine_companion.entity.logic.data.PeacefulProtection;

import com.mojang.blaze3d.systems.RenderSystem;
import com.whitecloud233.herobrine_companion.client.animation.HeroPoemPreview;
import com.whitecloud233.herobrine_companion.client.gui.ConversationManagerScreen;
import com.whitecloud233.herobrine_companion.client.gui.crosschat.CrossSessionHubScreen;
import com.whitecloud233.herobrine_companion.client.event.ClientHooks;
import com.whitecloud233.herobrine_companion.client.service.ConversationStore;
import com.whitecloud233.herobrine_companion.client.network.ClientServerConfig;
import com.whitecloud233.herobrine_companion.config.ServerRule;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.herobrine_companion.init.ModEntities;
import com.whitecloud233.herobrine_companion.network.ClearAreaPacket;
import com.whitecloud233.herobrine_companion.network.DesolateAreaPacket;
import com.whitecloud233.herobrine_companion.network.FlattenAreaPacket;
import com.whitecloud233.herobrine_companion.network.OpenTradePacket;
import com.whitecloud233.herobrine_companion.network.OpenWardrobePacket;
import com.whitecloud233.herobrine_companion.network.OfferGiftPacket;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.PeacefulPacket;
import com.whitecloud233.herobrine_companion.network.ToggleCompanionPacket;
import com.whitecloud233.herobrine_companion.network.ToggleBattleModePacket;
import com.whitecloud233.herobrine_companion.network.StartChallengePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public class HeroScreen extends HeroPanelScreen {
    private static final String DUMMY_BATTLE_SYNC_INIT = "HeroScreen.init.syncDummyBattleMode";
    private static final String DUMMY_BATTLE_SYNC_BUTTON = "HeroScreen.button.toggleBattlePreview";
    private static final String DUMMY_BATTLE_SYNC_RENDER = "HeroScreen.render.syncDummyBattleMode";

    private final int entityId;
    private HeroEntity dummyHero;
    private HeroActionList actionList;

    private boolean confirmingVoid = false;
    private long confirmTime = 0;

    private boolean confirmingDesolate = false;
    private long confirmDesolateTime = 0;

    private boolean confirmingFlatten = false;
    private long confirmFlattenTime = 0;

    // 挑战难度状态：0=简单, 1=普通, 2=困难
    private int challengeMode = 0;

    // 配色方案
    private static final int COL_BG_MAIN    = HeroTheme.BG_MAIN;
    private static final int COL_BG_SIDE    = HeroTheme.BG_SIDE;
    private static final int COL_BORDER     = HeroTheme.BORDER;
    private static final int COL_TEXT_MAIN  = HeroTheme.TEXT;
    private static final int COL_LABEL      = HeroTheme.LABEL;
    private static final int COL_VALUE      = HeroTheme.VALUE;
    private static final int COL_INFO       = HeroTheme.INFO;

    /** 设计尺寸（未钳制）。运行期按窗口钳制后写入 panelW/panelH。 */
    private static final int PANEL_BASE_WIDTH = 340;
    private static final int PANEL_BASE_HEIGHT = 210;

    /** 面板实际尺寸：init/render 时按窗口钳制后写入，供布局使用。 */
    private int panelW = PANEL_BASE_WIDTH;
    private int panelH = PANEL_BASE_HEIGHT;

    /** 面板几何：宽高钳制到窗口内，居中。返回 {startX, startY}，并更新 panelW/panelH。 */
    private int[] panelDims() {
        HeroTheme.Frame frame = HeroTheme.frame(this.width, this.height,
                PANEL_BASE_WIDTH, PANEL_BASE_HEIGHT, 260, 170, 8);
        this.panelW = frame.width();
        this.panelH = frame.height();
        return new int[]{frame.left(), frame.top()};
    }

    public HeroScreen(int entityId) {
        super(Component.translatable("gui.herobrine_companion.title"));
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        if (this.minecraft == null) return;
        super.init();
        if (this.minecraft.level != null) {
            this.dummyHero = ModEntities.HERO.get().create(this.minecraft.level);
            // 同步皮肤状态与姿势到 dummyHero 以便预览
            Entity realEntity = this.minecraft.level.getEntity(this.entityId);
            if (realEntity instanceof HeroEntity realHero) {
                this.dummyHero.setSkinVariant(realHero.getSkinVariant());
                this.dummyHero.setBattleModeActiveFrom(DUMMY_BATTLE_SYNC_INIT, realHero.isBattleModeActive());
                if (realHero.getSkinVariant() == HeroEntity.SKIN_CUSTOM) {
                    this.dummyHero.setCustomSkinName(realHero.getCustomSkinName());
                }

                // 【核心修复】：将真实实体的自定义姿势同步给预览用的 dummyHero
                this.dummyHero.isPoseEditing = realHero.isPoseEditing;
                if (realHero.isPoseEditing) {
                    for (int i = 0; i < 10; i++) {
                        System.arraycopy(realHero.customPoseAngles[i], 0, this.dummyHero.customPoseAngles[i], 0, 3);
                    }
                }
            }
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int[] dims = panelDims();
        int startX = dims[0];
        int startY = dims[1];

        int sideBarWidth = 100;
        int editorX = startX + sideBarWidth;
        int editorWidth = panelW - sideBarWidth;
        int topBarHeight = 25;
        int bottomBarHeight = 20;

        int btnX = startX + panelW - 50;
        int btnY = startY + 4;

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

        int skinBtnX = startX + 5;
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


        this.actionList = new HeroActionList(this.minecraft, editorWidth - 10, panelH - topBarHeight - bottomBarHeight - 10, startY + topBarHeight + 5, 24);
        // 1.21.1 的 ObjectSelectionList 去掉了 setLeftPos，改为 AbstractWidget#setX
        this.actionList.setX(editorX + 5);

        populateActionList();
        this.addRenderableWidget(this.actionList);

        this.addRenderableWidget(new ThemedButton(
                editorX + editorWidth - 85, startY + panelH - 18, 80, 16,
                Component.translatable("gui.herobrine_companion.leave"),
                button -> this.onClose(),
                null
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

        this.actionList.addAction(Component.translatable("gui.herobrine_companion.templates.title"), button -> {
            Minecraft.getInstance().setScreen(new TemplateLibraryScreen(this));
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.templates.tooltip")));

        this.actionList.addAction(Component.translatable("gui.herobrine_companion.cross_chat.manage"), button -> {
            Minecraft.getInstance().setScreen(new CrossSessionHubScreen(this.entityId));
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.cross_chat.manage_tooltip")));

        boolean protectionUnlocked = visited;

        // Protection Toggle
        this.actionList.addDynamicAction(() -> {
            boolean isProtected = PeacefulProtection.isEnabled(this.minecraft == null ? null : this.minecraft.player);
            if (!protectionUnlocked) {
                return Component.translatable(isProtected ? "gui.herobrine_companion.disable_protection_locked" : "gui.herobrine_companion.enable_protection_locked").withStyle(style -> style.withColor(0xFF808080));
            }
            return Component.translatable(isProtected ? "gui.herobrine_companion.disable_protection" : "gui.herobrine_companion.enable_protection");
        }, button -> {
            boolean isProtected = PeacefulProtection.isEnabled(this.minecraft == null ? null : this.minecraft.player);

            PacketHandler.sendToServer(new PeacefulPacket(!isProtected));
            this.onClose();
        }, Tooltip.create(Component.translatable(protectionUnlocked ? "gui.herobrine_companion.protection_tooltip" : "gui.herobrine_companion.protection_locked_tooltip"))).active = protectionUnlocked;

        this.actionList.addAction(Component.translatable("gui.herobrine_companion.trade"), button -> {
            PacketHandler.sendToServer(new OpenTradePacket(this.entityId));
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.trade_tooltip")));

        // 托付(赠礼):把手中物品托付给祂;服务端权威判定
        this.actionList.addAction(Component.translatable("gui.herobrine_companion.offer"), button -> {
            PacketHandler.sendToServer(new OfferGiftPacket(this.entityId, "offer_carried_item_to_hero"));
            this.onClose();
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.offer_tooltip")));

        this.actionList.addAction(Component.translatable("gui.herobrine_companion.wardrobe"), button -> {
            PacketHandler.sendToServer(new OpenWardrobePacket(this.entityId));
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.wardrobe_tooltip")));

        this.actionList.addAction(Component.translatable("gui.herobrine_companion.requests"), button -> {
            Minecraft.getInstance().setScreen(new HeroRequestScreen(this.entityId));
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.requests_tooltip")));

        this.actionList.addAction(Component.translatable("gui.herobrine_companion.rewards"), button -> {
            Minecraft.getInstance().setScreen(new HeroRewardScreen(this.entityId));
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.rewards_tooltip")));


        int currentTrust = 0;
        boolean currentlyFollowing = false;
        boolean currentlyBattle = false;
        if (this.minecraft.level != null) {
            Entity entity = this.minecraft.level.getEntity(this.entityId);
            if (entity instanceof HeroEntity hero) {
                currentTrust = hero.getTrustLevel();
                currentlyFollowing = hero.isCompanionMode();
                currentlyBattle = hero.isBattleModeActive();
            }
        }

        // Companion Mode
        int followTrustThreshold = com.whitecloud233.herobrine_companion.entity.companion.CompanionModePolicy.FOLLOW_TRUST_THRESHOLD;
        boolean companionUnlocked = currentlyFollowing || currentTrust >= followTrustThreshold;
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
                return Component.translatable("gui.herobrine_companion.companion_locked", followTrustThreshold, finalCurrentTrust).withStyle(style -> style.withColor(0xFF808080));
            }

            String key = currentState ? "gui.herobrine_companion.companion_disable" : "gui.herobrine_companion.companion_enable";
            return Component.translatable(key).withStyle(style -> style.withColor(0xFFFFC66D));
        }, button -> {
            PacketHandler.sendToServer(new ToggleCompanionPacket(this.entityId));
            this.onClose();
        }, Tooltip.create(Component.translatable(companionUnlocked ? "gui.herobrine_companion.companion_tooltip_unlocked" : "gui.herobrine_companion.companion_tooltip_locked", followTrustThreshold, currentTrust))).active = companionUnlocked;

        // 战斗模式按钮：无 Epic Fight 时走内置战斗 AI（终末之诗镰刀普攻），始终显示。
        // 开启要过服务器规则的信任门槛（0 = 关闭）；收刀（关闭）永远可用，已开启时按钮保持可点。
        int battleTrustThreshold = ClientServerConfig.snapshot().value(ServerRule.BATTLE_MODE_TRUST_THRESHOLD);
        boolean battleUnlocked = currentlyBattle || battleTrustThreshold <= 0 || currentTrust >= battleTrustThreshold;
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
            } else if (!currentState && !battleUnlocked) {
                return Component.translatable("gui.herobrine_companion.battle_mode_locked").withStyle(style -> style.withColor(0xFF808080));
            } else {
                key = currentState
                        ? "gui.herobrine_companion.battle_mode_disable"
                        : "gui.herobrine_companion.battle_mode_enable";
            }

            return Component.translatable(key).withStyle(style -> style.withColor(0xFF4FC3F7));
        }, button -> {
            Entity entity = this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getEntity(this.entityId) : null;
            boolean challengeActive = entity instanceof HeroEntity hero && hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE);
            boolean canToggle = battleUnlocked && !challengeActive;
            if (canToggle) {
                PacketHandler.sendToServer(new ToggleBattleModePacket(this.entityId));
            }
            if (this.dummyHero != null && canToggle) {
                this.dummyHero.setBattleModeActiveFrom(DUMMY_BATTLE_SYNC_BUTTON, !this.dummyHero.isBattleModeActive());
            }
        }, Tooltip.create(battleUnlocked
                ? Component.translatable("gui.herobrine_companion.battle_mode_tooltip")
                : Component.translatable("gui.herobrine_companion.battle_mode_tooltip_locked", battleTrustThreshold, finalCurrentTrust))).active = battleUnlocked;

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

        this.actionList.addAction(Component.translatable("gui.herobrine_companion.pose_editor"), button -> {
            Minecraft.getInstance().setScreen(new HeroPoseScreen(this.entityId));
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.pose_editor_tooltip")));

        this.actionList.addDynamicAction(() -> {
            String modeKey = this.challengeMode == 0 ? "gui.herobrine_companion.challenge_easy" :
                    (this.challengeMode == 1 ? "gui.herobrine_companion.challenge_normal" : "gui.herobrine_companion.challenge_hard");

            return Component.translatable("gui.herobrine_companion.challenge_mode")
                    .append(": ")
                    .append(Component.translatable(modeKey))
                    .withStyle(style -> style.withColor(0xFF55FF55));
        }, button -> {
            this.challengeMode = (this.challengeMode + 1) % 3;
        }, Tooltip.create(Component.translatable("gui.herobrine_companion.challenge_mode_tooltip")));

        this.actionList.addAction(
                Component.translatable("gui.herobrine_companion.challenge_start").withStyle(style -> style.withColor(0xFFFF5555)),
                button -> {
                    PacketHandler.sendToServer(new StartChallengePacket(this.entityId, this.challengeMode));
                    this.onClose();
                },
                Tooltip.create(Component.translatable("gui.herobrine_companion.challenge_start_tooltip"))
        );
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
        int[] dims = panelDims();
        int startX = dims[0];
        int startY = dims[1];

        int sideBarWidth = 100;
        int topBarHeight = 25;
        int bottomBarHeight = 20;

        // --- 背景绘制 ---
        guiGraphics.fill(startX + sideBarWidth, startY + topBarHeight, startX + panelW, startY + panelH - bottomBarHeight, COL_BG_MAIN);
        guiGraphics.fill(startX, startY + topBarHeight, startX + sideBarWidth, startY + panelH - bottomBarHeight, COL_BG_SIDE);
        guiGraphics.fill(startX, startY, startX + panelW, startY + topBarHeight, COL_BG_SIDE);
        guiGraphics.fill(startX, startY + panelH - bottomBarHeight, startX + panelW, startY + panelH, COL_BG_SIDE);

        guiGraphics.renderOutline(startX, startY, panelW, panelH, COL_BORDER);
        guiGraphics.fill(startX + sideBarWidth, startY + topBarHeight, startX + sideBarWidth + 1, startY + panelH - bottomBarHeight, COL_BORDER);

        // --- 顶部标题栏 ---
        int tabWidth = 140;
        guiGraphics.fill(startX + sideBarWidth, startY, startX + sideBarWidth + tabWidth, startY + topBarHeight - 2, COL_BG_MAIN);
        guiGraphics.fill(startX + sideBarWidth, startY, startX + sideBarWidth + tabWidth, startY + 2, 0xFF4A88C7);

        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.dashboard_title"), startX + sideBarWidth + 10, startY + 8, COL_TEXT_MAIN, false);
        // --- 左侧信息栏 ---
        guiGraphics.fill(startX + 5, startY + topBarHeight + 5, startX + sideBarWidth - 5, startY + topBarHeight + 95, 0xFF1E1E1E);

        int varY = startY + topBarHeight + 105;
        int lineHeight = 10;
        int indent = startX + 5;

        drawInfoLabel(guiGraphics, indent, varY, Component.translatable("gui.herobrine_companion.target"), Component.translatable("gui.herobrine_companion.target_name"));

        int trust = 0;
        UUID uuid = null;
        boolean battleMode = false;
        SimpleNeuralNetwork.MindState mindState = SimpleNeuralNetwork.MindState.OBSERVER;
        if (this.minecraft.level != null) {
            Entity realEntity = this.minecraft.level.getEntity(this.entityId);
            if (realEntity instanceof HeroEntity hero) {
                trust = hero.getTrustLevel();
                uuid = hero.getUUID();
                battleMode = hero.isBattleModeActive();
                mindState = hero.getMindState();

                // 【核心修复】：持续刷新渲染时的皮肤与姿势状态（dummyHero 仅在 level 非空时创建，这里兜底判空）
                if (this.dummyHero != null) {
                    this.dummyHero.setSkinVariant(hero.getSkinVariant());
                    if (this.dummyHero.isBattleModeActive() != hero.isBattleModeActive()) {
                        this.dummyHero.setBattleModeActiveFrom(DUMMY_BATTLE_SYNC_RENDER, hero.isBattleModeActive());
                    }
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
        }

        drawInfoField(guiGraphics, indent + 5, varY + lineHeight, Component.translatable("gui.herobrine_companion.trust_level"), Component.literal(String.valueOf(trust)));
        drawInfoField(guiGraphics, indent + 5, varY + lineHeight * 2, Component.translatable("gui.herobrine_companion.battle_mode_status"), Component.translatable(battleMode ? "gui.herobrine_companion.battle_mode_status_on" : "gui.herobrine_companion.battle_mode_status_off"));
        drawInfoField(guiGraphics, indent + 5, varY + lineHeight * 3, Component.translatable("gui.herobrine_companion.mind_state"), getMindStateComponent(mindState));
        drawInfoField(guiGraphics, indent + 5, varY + lineHeight * 4, Component.translatable("gui.herobrine_companion.entity_id"), Component.literal(uuid == null ? "N/A" : "..." + uuid.toString().substring(0, 4)));

        int barY = varY + lineHeight * 5 + 5;
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

        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.available_actions"), mainAreaX, mainAreaY, COL_LABEL, false);
        guiGraphics.fill(mainAreaX, mainAreaY + 10, startX + panelW - 5, mainAreaY + 11, COL_BORDER);

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

    private void drawInfoLabel(GuiGraphics g, int x, int y, Component label, Component value) {
        int labelWidth = this.font.width(label);
        g.drawString(this.font, label, x, y, COL_LABEL, false);
        g.drawString(this.font, Component.literal(" ").append(value), x + labelWidth, y, COL_TEXT_MAIN, false);
    }

    private void drawInfoField(GuiGraphics g, int x, int y, Component name, Component value) {
        // [性能] 字体度量只算一次：原实现每个字段调用两次 font.width(name)，
        // 而 width() 需要完整遍历字形序列（StringDecomposer），5 个字段每帧就是 10 次重复测量。
        int nameWidth = this.font.width(name);
        g.drawString(this.font, name, x, y, COL_VALUE, false);
        g.drawString(this.font, ": ", x + nameWidth, y, COL_TEXT_MAIN, false);
        g.drawString(this.font, value, x + nameWidth + 10, y, COL_INFO, false);
    }

    private Component getMindStateComponent(SimpleNeuralNetwork.MindState state) {
        String suffix = switch (state) {
            case OBSERVER -> "observer";
            case PROTECTOR -> "protector";
            case JUDGE -> "judge";
            case PRANKSTER -> "prankster";
            case MAINTAINER -> "maintainer";
            case GLITCH_LORD -> "glitch_lord";
            case MONSTER_KING -> "monster_king";
            case REMINISCING -> "reminiscing";
        };
        return Component.translatable("gui.herobrine_companion.mind_state." + suffix);
    }

    private void renderEntityWithMouseFollow(GuiGraphics guiGraphics, int x, int y, int scale, float mouseX, float mouseY, HeroEntity entity) {
        float f = (float)Math.atan(mouseX / 40.0F);
        float f1 = (float)Math.atan(mouseY / 40.0F);

        entity.yBodyRotO = 180.0F + f * 20.0F;
        entity.yRotO = 180.0F + f * 40.0F;
        entity.xRotO = -f1 * 20.0F;
        entity.yHeadRotO = entity.yRotO;

        // 纸娃娃不会 tick（tickCount 恒为 0），登记成预览实体后由真实时间驱动
        // 终末之诗的骨架动作，界面里看到的才是会动的动作而不是冻结的首帧。
        HeroPoemPreview.drive(entity);

        // 1.21.1 的签名是 10 个参数（x1,y1,x2,y2,scale,yOffset,mouseX,mouseY,entity）
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics,
                x - scale,
                y - scale * 2,
                x + scale,
                y,
                scale,
                0.0625F,
                mouseX,
                mouseY,
                entity
        );
    }

    @Override
    public void removed() {
        if (this.dummyHero != null) {
            HeroPoemPreview.release(this.dummyHero);
        }
        super.removed();
    }

    // --- [新增] 自定义按钮类 (保持与 1.21.1 风格一致) ---
    public static class ThemedButton extends Button {
        private static final int BG_NORMAL = 0xFF3C3F41;
        private static final int BG_HOVER  = 0xFF4C5052;
        private static final int BORDER    = 0xFF555555;
        private static final int TEXT_COL  = 0xFFA9B7C6;

        /** 无 tooltip 的简写：1.21.1 分支里多处界面只传这 6 个参数。 */
        public ThemedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            this(x, y, width, height, message, onPress, null);
        }

        public ThemedButton(int x, int y, int width, int height, Component message, OnPress onPress, Tooltip tooltip) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            if (tooltip != null) {
                this.setTooltip(tooltip);
            }
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = this.isHoveredOrFocused();
            int bgColor = hovered ? BG_HOVER : BG_NORMAL;

            // 绘制背景
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            // 绘制边框
            guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, BORDER);
            // 绘制文字
            int textColor = hovered ? 0xFFFFFFFF : TEXT_COL;
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, textColor);
        }
    }
}
