package com.whitecloud233.modid.herobrine_companion.client.gui.crosschat;

import com.whitecloud233.modid.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.ai.RespondCrossChatInvitePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class CrossChatInviteScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 120;
    private static final int BG = 0xEE2B2B2B;
    private static final int BORDER = 0xFF555555;
    private static final int TITLE = 0xFFD16D9E;
    private static final int TEXT = 0xFFA9B7C6;

    private final Screen previousScreen;
    private final UUID requesterId;
    private final String requesterName;

    public CrossChatInviteScreen(Screen previousScreen, UUID requesterId, String requesterName) {
        super(Component.translatable("gui.herobrine_companion.cross_chat.invite.title"));
        this.previousScreen = previousScreen;
        this.requesterId = requesterId;
        this.requesterName = requesterName == null ? "" : requesterName;
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + 18, top + 78, 96, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.accept"),
                button -> {
                    PacketHandler.sendToServer(new RespondCrossChatInvitePacket(this.requesterId, true));
                    this.closeAndReturn();
                }, null
        ));

        this.addRenderableWidget(new HeroScreen.ThemedButton(
                left + PANEL_WIDTH - 114, top + 78, 96, 20,
                Component.translatable("gui.herobrine_companion.cross_chat.deny"),
                button -> {
                    PacketHandler.sendToServer(new RespondCrossChatInvitePacket(this.requesterId, false));
                    this.closeAndReturn();
                }, null
        ));
    }

    private void closeAndReturn() {
        Minecraft.getInstance().setScreen(this.previousScreen);
    }

    @Override
    public void onClose() {
        this.closeAndReturn();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BG);
        guiGraphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top + 12, TITLE);
        guiGraphics.drawWordWrap(this.font,
                Component.translatable("gui.herobrine_companion.cross_chat.invite.body", this.requesterName),
                left + 14, top + 34, PANEL_WIDTH - 28, TEXT);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

