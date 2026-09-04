package com.whitecloud233.herobrine_companion.client.gui;

import com.whitecloud233.herobrine_companion.BuildFlags;
import com.whitecloud233.herobrine_companion.client.event.ClientHooks;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.herobrine_companion.config.LocalModelScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

public class HeroChatScreen extends ChatScreen {
    private Button localModelButton;

    public HeroChatScreen(String initialText) {
        super(initialText);
    }

    @Override
    protected void init() {
        super.init();
        this.localModelButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.chat_screen.local_model_button"),
                        button -> this.minecraft.setScreen(new LocalModelScreen(this)))
                .bounds(Math.max(4, this.width / 2 - 82), 34, 164, 20)
                .build());
        this.updateLocalModelPromptVisibility();
    }

    private void updateLocalModelPromptVisibility() {
        if (this.localModelButton == null) {
            return;
        }
        boolean show = !BuildFlags.CF_SAFE
                && !ClientHooks.isApiEnabled()
                && !ClientHooks.hasActiveCrossChatSession()
                && !LLMConfig.isLocalModelConnected();
        this.localModelButton.visible = show;
        this.localModelButton.active = show;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.updateLocalModelPromptVisibility();
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
        if (this.localModelButton != null && this.localModelButton.visible) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.herobrine_companion.chat_screen.local_model_prompt"),
                    this.width / 2, 58, 0xFFD37F);
        }
    }
}

