package com.whitecloud233.herobrine_companion.client.gui;

import com.whitecloud233.herobrine_companion.client.event.ClientHooks;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.herobrine_companion.config.LocalModelScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

public class HeroChatScreen extends ChatScreen {
    private Button localModelButton;

    // [性能] 标题/提示文案缓存：原来每帧都要重新构造 Component.translatable 并做一次
    // ClientHooks 状态查询与字符串拼接，而这些值只在 API 开关或跨聊天会话变化时才变。
    // 用「上次计算时的状态快照」作为键，状态不变就直接复用已构建的 Component。
    private Component cachedTitle;
    private Component cachedHint;
    private boolean cachedApiEnabled;
    private boolean cachedCrossChatActive;
    private String cachedPeerName;

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
        boolean show = !ClientHooks.isApiEnabled()
                && !ClientHooks.hasActiveCrossChatSession()
                && !LLMConfig.isLocalModelConnected();
        this.localModelButton.visible = show;
        this.localModelButton.active = show;
    }

    /** 仅在 API 开关 / 跨聊天会话 / 对方名字变化时重建缓存的文案。 */
    private void refreshCachedTexts() {
        boolean apiEnabled = ClientHooks.isApiEnabled();
        boolean crossChatActive = ClientHooks.hasActiveCrossChatSession();
        String peerName = crossChatActive ? ClientHooks.getCrossChatPeerName() : null;
        if (this.cachedTitle != null
                && apiEnabled == this.cachedApiEnabled
                && crossChatActive == this.cachedCrossChatActive
                && java.util.Objects.equals(peerName, this.cachedPeerName)) {
            return;
        }
        this.cachedApiEnabled = apiEnabled;
        this.cachedCrossChatActive = crossChatActive;
        this.cachedPeerName = peerName;
        this.cachedTitle = Component.translatable(apiEnabled
                ? "gui.herobrine_companion.chat_screen.title_cloud"
                : "gui.herobrine_companion.chat_screen.title_local");
        this.cachedHint = crossChatActive
                ? Component.translatable(
                        "gui.herobrine_companion.chat_screen.hint_cross",
                        peerName == null || peerName.isBlank() ? "-" : peerName)
                : Component.translatable("gui.herobrine_companion.chat_screen.hint");
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.updateLocalModelPromptVisibility();
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        this.refreshCachedTexts();

        guiGraphics.drawCenteredString(this.font, this.cachedTitle, this.width / 2, 6, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, this.cachedHint, this.width / 2, 18, 0xA9B7C6);
        if (this.localModelButton != null && this.localModelButton.visible) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.herobrine_companion.chat_screen.local_model_prompt"),
                    this.width / 2, 58, 0xFFD37F);
        }
    }
}

