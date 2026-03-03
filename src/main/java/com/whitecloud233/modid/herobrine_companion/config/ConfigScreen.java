package com.whitecloud233.modid.herobrine_companion.config;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class ConfigScreen extends Screen {
    private final Screen lastScreen;

    public ConfigScreen(Screen lastScreen) {
        super(Component.translatable("gui.herobrine_companion.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 4 - 20; // 稍微整体往上挪一点，给4个按钮腾出空间
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 24;

        // 1. Poem of the End Explosion
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.poem_explosion", Config.POEM_OF_THE_END_EXPLOSION.get()),
                        button -> {
                            boolean current = Config.POEM_OF_THE_END_EXPLOSION.get();
                            boolean newValue = !current;
                            Config.POEM_OF_THE_END_EXPLOSION.set(newValue);
                            Config.poemOfTheEndExplosion = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.poem_explosion", newValue));
                        })
                .pos(centerX - buttonWidth / 2, startY)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.poem_explosion.tooltip")))
                .build()
        );

        // 2. Hero King Aura
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.hero_aura", Config.HERO_KING_AURA_ENABLED.get()).append(Component.literal(" *").withStyle(ChatFormatting.RED)),
                        button -> {
                            boolean current = Config.HERO_KING_AURA_ENABLED.get();
                            boolean newValue = !current;
                            Config.HERO_KING_AURA_ENABLED.set(newValue);
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.hero_aura", newValue).append(Component.literal(" *").withStyle(ChatFormatting.RED)));
                        })
                .pos(centerX - buttonWidth / 2, startY + spacing)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.hero_aura.tooltip").append(Component.translatable("gui.herobrine_companion.config.restart_required").withStyle(ChatFormatting.RED))))
                .build()
        );

        // 3. Block Restoration Toggle
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.block_restoration", Config.HERO_BLOCK_RESTORATION.get()),
                        button -> {
                            boolean current = Config.HERO_BLOCK_RESTORATION.get();
                            boolean newValue = !current;
                            Config.HERO_BLOCK_RESTORATION.set(newValue);
                            Config.heroBlockRestoration = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.block_restoration", newValue));
                        })
                .pos(centerX - buttonWidth / 2, startY + spacing * 2)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.block_restoration.tooltip")))
                .build()
        );

        // 4. 【新增】Clean Items Toggle 掉落物清理开关
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.clean_items", Config.HERO_CLEAN_ITEMS.get()),
                        button -> {
                            boolean current = Config.HERO_CLEAN_ITEMS.get();
                            boolean newValue = !current;
                            Config.HERO_CLEAN_ITEMS.set(newValue);
                            Config.heroCleanItems = newValue; // 立即更新静态变量
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.clean_items", newValue));
                        })
                .pos(centerX - buttonWidth / 2, startY + spacing * 3) // 排在第4位
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.clean_items.tooltip")))
                .build()
        );

        // 返回按钮
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .pos(centerX - buttonWidth / 2, this.height - 40)
                .size(buttonWidth, buttonHeight)
                .build());

        // 重启提示标签 (排在第4个按钮的下方)
        this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.config.restart_note").withStyle(ChatFormatting.RED), button -> {})
                .pos(centerX - buttonWidth / 2, startY + spacing * 4 + 5)
                .size(buttonWidth, 10)
                .build()
        ).active = false;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
}