package com.whitecloud233.modid.herobrine_companion.client.gui.crosschat;

import com.whitecloud233.modid.herobrine_companion.client.event.ClientHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

public class CrossChatScreen extends ChatScreen {
    public CrossChatScreen(String initialText) {
        super(initialText);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        String peerName = ClientHooks.getCrossChatPeerName();
        Component directTarget = Component.translatable(ClientHooks.isCrossChatHbInputMode()
                ? "gui.herobrine_companion.cross_chat.archive_mode_hb"
                : "gui.herobrine_companion.cross_chat.screen_target_peer_hb");
        Component autoStatus = Component.translatable(ClientHooks.isCrossChatAutoChatEnabled()
                ? "gui.herobrine_companion.cross_chat.screen_status_on"
                : "gui.herobrine_companion.cross_chat.screen_status_off");
        Component title = Component.translatable(
                "gui.herobrine_companion.cross_chat.screen_title",
                peerName == null || peerName.isBlank()
                        ? Component.translatable("gui.herobrine_companion.cross_chat.screen_disconnected")
                        : Component.literal(peerName)
        );
        Component hint = Component.translatable("gui.herobrine_companion.cross_chat.screen_hint", directTarget);
        Component autoHint = Component.translatable("gui.herobrine_companion.cross_chat.screen_auto_hint", autoStatus);
        guiGraphics.drawCenteredString(this.font, title, this.width / 2, 6, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, hint, this.width / 2, 18, 0xA9B7C6);
        guiGraphics.drawCenteredString(this.font, autoHint, this.width / 2, 30, 0xA9B7C6);
    }
}

