package com.whitecloud233.modid.herobrine_companion.client.service;

import com.whitecloud233.modid.herobrine_companion.client.llm.LlmFailure;
import com.whitecloud233.modid.herobrine_companion.config.ApiKeyInputScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/** 请求线程只上报结构化错误，界面切换统一回到客户端主线程。 */
final class AIClientErrorHandler {
    private AIClientErrorHandler() {
    }

    static void handle(LlmFailure failure) {
        if (failure == null || failure.kind() != LlmFailure.Kind.AUTHENTICATION) return;
        Minecraft client = Minecraft.getInstance();
        client.tell(() -> {
            if (!(client.screen instanceof ApiKeyInputScreen)) {
                client.setScreen(new ApiKeyInputScreen(new ChatScreen("")));
            }
        });
    }
}
