package com.whitecloud233.modid.herobrine_companion.client.gui;

import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.RequestActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HeroRequestScreen extends Screen {

    private final int entityId;
    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 160;
    /** 侧箭头需要左右各 25px，钳制宽度时为它们留边距。 */
    private static final int ARROW_MARGIN = 50;

    // Colors
    private static final int COL_BG = 0xFF2B2B2B;
    private static final int COL_BORDER = 0xFF555555;
    private static final int COL_TEXT = 0xFFA9B7C6;
    private static final int COL_TITLE = 0xFFCC7832;

    private int currentQuestIndex = 0;
    private static final int TOTAL_QUESTS = 2;

    public HeroRequestScreen(int entityId) {
        super(Component.translatable("gui.herobrine_companion.requests_title"));
        this.entityId = entityId;
    }

    /** 面板几何：宽高钳制到窗口（宽度预留侧箭头边距），居中。 */
    private record Panel(int left, int top, int width, int height) {
    }

    private Panel panel() {
        int w = Math.min(PANEL_WIDTH, Math.max(200, this.width - ARROW_MARGIN));
        int h = Math.min(PANEL_HEIGHT, Math.max(140, this.height - 16));
        return new Panel((this.width - w) / 2, Math.max(4, (this.height - h) / 2), w, h);
    }

    @Override
    protected void init() {
        super.init();
        Panel p = panel();
        int centerY = this.height / 2;
        int startX = p.left();
        int startY = p.top();

        // Accept Button
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                startX + p.width() - 85, startY + p.height() - 25, 80, 20,
                Component.translatable("gui.herobrine_companion.request_accept"),
                button -> {
                    // Send packet to accept the quest (ID: currentQuestIndex + 1)
                    PacketHandler.sendToServer(new RequestActionPacket(this.entityId, currentQuestIndex + 1, RequestActionPacket.Action.ACCEPT));
                    this.onClose();
                },
                null
        ));

        // Cancel Button
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                startX + p.width() - 170, startY + p.height() - 25, 80, 20,
                Component.translatable("gui.herobrine_companion.request_cancel"),
                button -> {
                    // Send packet to cancel the current quest
                    PacketHandler.sendToServer(new RequestActionPacket(this.entityId, 0, RequestActionPacket.Action.CANCEL));
                    this.onClose();
                },
                null
        ));

        // Back Button
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                startX + 5, startY + p.height() - 25, 60, 20,
                Component.translatable("gui.herobrine_companion.back"),
                button -> {
                    this.onClose();
                    Minecraft.getInstance().setScreen(new HeroScreen(this.entityId));
                },
                null
        ));

        // Previous Quest Button
        this.addRenderableWidget(new Button.Builder(Component.literal("<"), button -> {
            currentQuestIndex = (currentQuestIndex - 1 + TOTAL_QUESTS) % TOTAL_QUESTS;
        }).bounds(startX - 25, centerY - 10, 20, 20).build());

        // Next Quest Button
        this.addRenderableWidget(new Button.Builder(Component.literal(">"), button -> {
            currentQuestIndex = (currentQuestIndex + 1) % TOTAL_QUESTS;
        }).bounds(startX + p.width() + 5, centerY - 10, 20, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        Panel p = panel();
        int centerX = this.width / 2;
        int startX = p.left();
        int startY = p.top();

        // Background
        guiGraphics.fill(startX, startY, startX + p.width(), startY + p.height(), COL_BG);
        guiGraphics.renderOutline(startX, startY, p.width(), p.height(), COL_BORDER);

        // Title
        guiGraphics.drawCenteredString(this.font, this.title, centerX, startY + 10, COL_TITLE);

        // 内容区 scissor：描述换行超出面板时被截断。
        guiGraphics.enableScissor(startX + 1, startY + 28, startX + p.width() - 1, startY + p.height() - 30);

        // Request Details
        int textX = startX + 15;
        int textY = startY + 35;

        String questNameKey = "gui.herobrine_companion.request_name_" + (currentQuestIndex + 1);
        String questDescKey = "gui.herobrine_companion.request_desc_" + (currentQuestIndex + 1);

        guiGraphics.drawString(this.font, Component.translatable(questNameKey), textX, textY, 0xFFFFC66D, false);
        textY += 15;

        String desc = Component.translatable(questDescKey).getString();
        guiGraphics.drawWordWrap(this.font, Component.literal(desc), textX, textY, p.width() - 30, COL_TEXT);

        textY += 45;
        guiGraphics.drawString(this.font, Component.translatable("gui.herobrine_companion.request_reward"), textX, textY, 0xFF6A8759, false);
        textY += 12;

        if (currentQuestIndex == 0) {
            guiGraphics.drawString(this.font, "- " + Component.translatable("item.herobrine_companion.void_marrow").getString() + " x3", textX + 5, textY, COL_TEXT, false);
            textY += 10;
            guiGraphics.drawString(this.font, "- Trust +15", textX + 5, textY, COL_TEXT, false);
        } else if (currentQuestIndex == 1) {
            guiGraphics.drawString(this.font, "- " + Component.translatable("item.minecraft.ender_pearl").getString() + " x16", textX + 5, textY, COL_TEXT, false);
            textY += 10;
            guiGraphics.drawString(this.font, "- Trust +20", textX + 5, textY, COL_TEXT, false);
        }

        guiGraphics.disableScissor();

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
        // No default background
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
