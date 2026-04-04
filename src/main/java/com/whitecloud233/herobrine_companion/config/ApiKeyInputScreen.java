package com.whitecloud233.herobrine_companion.config;

import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ApiKeyInputScreen extends Screen {
    private final Screen lastScreen;
    private EditBox apiKeyBox;

    public ApiKeyInputScreen(Screen lastScreen) {
        // 使用翻译键替换纯文本
        super(Component.translatable("gui.herobrine_companion.api_setup.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        // 创建 API Key 输入框，使用翻译键
        this.apiKeyBox = new EditBox(this.font, centerX - 100, 80, 200, 20, Component.translatable("gui.herobrine_companion.api_setup.api_key"));
        this.apiKeyBox.setMaxLength(256);
        this.apiKeyBox.setValue(LLMConfig.aiApiKey.equals("YOUR_API_KEY_HERE") ? "" : LLMConfig.aiApiKey);
        this.addRenderableWidget(this.apiKeyBox);

        // 保存按钮，使用翻译键
        this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.api_setup.confirm_save"), (button) -> {
            LLMConfig.aiApiKey = this.apiKeyBox.getValue();
            LLMConfig.save(); // 写入根目录私密文件
            this.minecraft.setScreen(this.lastScreen);
        }).pos(centerX - 100, 110).size(200, 20).build());
    }

    // 👇 就是这里！学习 HeroScreen 的做法，拦截并清空默认的背景渲染
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1.21.1 留空：禁用原版自带的世界模糊和黑色背景遮罩
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1.21.1：补全缺失的 3 个参数 (mouseX, mouseY, partialTick)
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 渲染提示文字，使用翻译键
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.api_setup.prompt_input"), this.width / 2, 50, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.api_setup.prompt_safe"), this.width / 2, 65, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}