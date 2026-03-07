package com.whitecloud233.herobrine_companion.config;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.jetbrains.annotations.NotNull;

public class ConfigScreen extends Screen {
    public static final IConfigScreenFactory FACTORY = (container, screen) -> new ConfigScreen(screen);

    private final Screen lastScreen;

    public ConfigScreen(Screen lastScreen) {
        super(Component.translatable("gui.herobrine_companion.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 4 - 20;
        int buttonWidth = 150;
        int buttonHeight = 20;
        int spacingY = 24;

        // 双列 X 坐标
        int col1X = centerX - buttonWidth - 5;
        int col2X = centerX + 5;

        // 1.21.1 更新：全部使用 .bounds(x, y, width, height) 替代旧的 .pos() 和 .size()

        // 1. Poem of the End Explosion (左侧第1行)
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.poem_explosion", Config.POEM_OF_THE_END_EXPLOSION.get()),
                        button -> {
                            boolean newValue = !Config.POEM_OF_THE_END_EXPLOSION.get();
                            Config.POEM_OF_THE_END_EXPLOSION.set(newValue);
                            Config.poemOfTheEndExplosion = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.poem_explosion", newValue));
                        })
                .bounds(col1X, startY, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.poem_explosion.tooltip")))
                .build()
        );

        // 2. Hero King Aura (右侧第1行)
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.hero_aura", Config.HERO_KING_AURA_ENABLED.get()).append(Component.literal(" *").withStyle(ChatFormatting.RED)),
                        button -> {
                            boolean newValue = !Config.HERO_KING_AURA_ENABLED.get();
                            Config.HERO_KING_AURA_ENABLED.set(newValue);
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.hero_aura", newValue).append(Component.literal(" *").withStyle(ChatFormatting.RED)));
                        })
                .bounds(col2X, startY, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.hero_aura.tooltip").append(Component.translatable("gui.herobrine_companion.config.restart_required").withStyle(ChatFormatting.RED))))
                .build()
        );

        // 3. Block Restoration Toggle (左侧第2行)
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.block_restoration", Config.HERO_BLOCK_RESTORATION.get()),
                        button -> {
                            boolean newValue = !Config.HERO_BLOCK_RESTORATION.get();
                            Config.HERO_BLOCK_RESTORATION.set(newValue);
                            Config.heroBlockRestoration = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.block_restoration", newValue));
                        })
                .bounds(col1X, startY + spacingY, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.block_restoration.tooltip")))
                .build()
        );

        // 4. Clean Items Toggle (右侧第2行)
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.clean_items", Config.HERO_CLEAN_ITEMS.get()),
                        button -> {
                            boolean newValue = !Config.HERO_CLEAN_ITEMS.get();
                            Config.HERO_CLEAN_ITEMS.set(newValue);
                            Config.heroCleanItems = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.clean_items", newValue));
                        })
                .bounds(col2X, startY + spacingY, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.clean_items.tooltip")))
                .build()
        );

        // 5. Cleave Skill Toggle (左侧第3行)
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.cleave_skill", Config.CLEAVE_SKILL_ENABLED.get()),
                        button -> {
                            boolean newValue = !Config.CLEAVE_SKILL_ENABLED.get();
                            Config.CLEAVE_SKILL_ENABLED.set(newValue);
                            Config.cleaveSkillEnabled = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.cleave_skill", newValue));
                        })
                .bounds(col1X, startY + spacingY * 2, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.cleave_skill.tooltip")))
                .build()
        );

        // 6. Soul Bound Pact (右侧第3行)
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.soul_bound_pact", Config.SOUL_BOUND_PACT_ENABLED.get()),
                        button -> {
                            boolean newValue = !Config.SOUL_BOUND_PACT_ENABLED.get();
                            Config.SOUL_BOUND_PACT_ENABLED.set(newValue);
                            Config.soulBoundPactEnabled = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.soul_bound_pact", newValue));
                        })
                .bounds(col2X, startY + spacingY * 2, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.soul_bound_pact.tooltip")))
                .build()
        );

        // 7. Abyssal Gaze (左侧第4行)
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.abyssal_gaze", Config.ABYSSAL_GAZE_ENABLED.get()),
                        button -> {
                            boolean newValue = !Config.ABYSSAL_GAZE_ENABLED.get();
                            Config.ABYSSAL_GAZE_ENABLED.set(newValue);
                            Config.abyssalGazeEnabled = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.abyssal_gaze", newValue));
                        })
                .bounds(col1X, startY + spacingY * 3, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.abyssal_gaze.tooltip")))
                .build()
        );

        // 8. Transcendence Permit (右侧第4行)
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.transcendence_permit", Config.TRANSCENDENCE_PERMIT_ENABLED.get()),
                        button -> {
                            boolean newValue = !Config.TRANSCENDENCE_PERMIT_ENABLED.get();
                            Config.TRANSCENDENCE_PERMIT_ENABLED.set(newValue);
                            Config.transcendencePermitEnabled = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.transcendence_permit", newValue));
                        })
                .bounds(col2X, startY + spacingY * 3, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.transcendence_permit.tooltip")))
                .build()
        );

        // 返回按钮
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(centerX - 100, this.height - 40, 200, 20)
                .build());

        // 重启提示标签
        this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.config.restart_note").withStyle(ChatFormatting.RED), button -> {})
                .bounds(centerX - 100, startY + spacingY * 4 + 10, 200, 10)
                .build()
        ).active = false;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1.21.1 更新：renderBackground 需要传递额外的三个参数
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
}