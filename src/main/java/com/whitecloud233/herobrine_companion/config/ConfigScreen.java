package com.whitecloud233.herobrine_companion.config;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.jetbrains.annotations.NotNull;

public class ConfigScreen extends Screen {
    private final Screen lastScreen;

    public ConfigScreen(ModContainer container, Screen lastScreen) {
        super(Component.translatable("gui.herobrine_companion.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 4;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 24;

        // Poem of the End Explosion Toggle
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.herobrine_companion.config.poem_explosion", Config.POEM_OF_THE_END_EXPLOSION.get()),
                button -> {
                    boolean current = Config.POEM_OF_THE_END_EXPLOSION.get();
                    boolean newValue = !current;
                    Config.POEM_OF_THE_END_EXPLOSION.set(newValue);
                    Config.poemOfTheEndExplosion = newValue; // Update static field immediately
                    Config.SPEC.save(); // Force save to disk
                    button.setMessage(Component.translatable("gui.herobrine_companion.config.poem_explosion", newValue));
                })
                .pos(centerX - buttonWidth / 2, startY)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.poem_explosion.tooltip")))
                .build()
        );

        // Hero King Aura Toggle
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.herobrine_companion.config.hero_aura", Config.HERO_KING_AURA_ENABLED.get()).append(Component.literal(" *").withStyle(ChatFormatting.RED)),
                button -> {
                    boolean current = Config.HERO_KING_AURA_ENABLED.get();
                    boolean newValue = !current;
                    Config.HERO_KING_AURA_ENABLED.set(newValue);
                    Config.SPEC.save(); // Force save to disk
                    // Do NOT update static field immediately for this one, as it requires restart
                    // Config.heroKingAuraEnabled = newValue; 
                    button.setMessage(Component.translatable("gui.herobrine_companion.config.hero_aura", newValue).append(Component.literal(" *").withStyle(ChatFormatting.RED)));
                })
                .pos(centerX - buttonWidth / 2, startY + spacing)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.hero_aura.tooltip").append(Component.translatable("gui.herobrine_companion.config.restart_required").withStyle(ChatFormatting.RED))))
                .build()
        );

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .pos(centerX - buttonWidth / 2, this.height - 40)
                .size(buttonWidth, buttonHeight)
                .build());
        
        // Add a label explaining the asterisk
        this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.config.restart_note").withStyle(ChatFormatting.RED), button -> {})
                .pos(centerX - buttonWidth / 2, startY + spacing + 25)
                .size(buttonWidth, 10)
                .build()
        ).active = false; // Just a label
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
    
    // Factory for NeoForge
    public static final IConfigScreenFactory FACTORY = (container, screen) -> new ConfigScreen(container, screen);
}
