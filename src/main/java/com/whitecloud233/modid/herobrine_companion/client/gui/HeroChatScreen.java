package com.whitecloud233.modid.herobrine_companion.client.gui;

import com.whitecloud233.modid.herobrine_companion.client.event.ClientHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

public class HeroChatScreen extends ChatScreen {
    public HeroChatScreen(String initialText) {
        super(initialText);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        Component title = Component.translatable(
                ClientHooks.isApiEnabled()
                        ? "gui.herobrine_companion.chat_screen.title_cloud"
                        : "gui.herobrine_companion.chat_screen.title_local"
        );
        Component hint = ClientHooks.hasActiveCrossChatSession()
                ? Component.translatable(
                        "gui.herobrine_companion.chat_screen.hint_cross",
                        ClientHooks.getCrossChatPeerName().isBlank() ? "-" : ClientHooks.getCrossChatPeerName()
                )
                : Component.translatable("gui.herobrine_companion.chat_screen.hint");

        guiGraphics.drawCenteredString(this.font, title, this.width / 2, 6, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, hint, this.width / 2, 18, 0xA9B7C6);
    }
}

