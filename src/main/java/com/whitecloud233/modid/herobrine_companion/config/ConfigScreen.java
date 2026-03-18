package com.whitecloud233.modid.herobrine_companion.config;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class ConfigScreen extends Screen {
    private final Screen lastScreen;
    // 【新增】保存当前输入框的Y坐标，供 render 方法精准绘制标题使用
    private int currentEditBoxY;

    public ConfigScreen(Screen lastScreen) {
        super(Component.translatable("gui.herobrine_companion.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int buttonWidth = 150;
        int buttonHeight = 20;

        // 缩小行距，从 24 缩小到 22，给小屏幕留出更多呼吸空间
        int spacingY = 22;
        // 顶部按钮群固定起始 Y 坐标
        int startY = 35;

        int col1X = centerX - buttonWidth - 5;
        int col2X = centerX + 5;

        // 1. Poem of the End Explosion
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.poem_explosion", Config.POEM_OF_THE_END_EXPLOSION.get()),
                        button -> {
                            boolean newValue = !Config.POEM_OF_THE_END_EXPLOSION.get();
                            Config.POEM_OF_THE_END_EXPLOSION.set(newValue);
                            Config.poemOfTheEndExplosion = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.poem_explosion", newValue));
                        })
                .pos(col1X, startY)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.poem_explosion.tooltip")))
                .build()
        );

        // 2. Hero King Aura
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.hero_aura", Config.HERO_KING_AURA_ENABLED.get()).append(Component.literal(" *").withStyle(ChatFormatting.RED)),
                        button -> {
                            boolean newValue = !Config.HERO_KING_AURA_ENABLED.get();
                            Config.HERO_KING_AURA_ENABLED.set(newValue);
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.hero_aura", newValue).append(Component.literal(" *").withStyle(ChatFormatting.RED)));
                        })
                .pos(col2X, startY)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.hero_aura.tooltip").append(Component.translatable("gui.herobrine_companion.config.restart_required").withStyle(ChatFormatting.RED))))
                .build()
        );

        // 3. Block Restoration Toggle
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.block_restoration", Config.HERO_BLOCK_RESTORATION.get()),
                        button -> {
                            boolean newValue = !Config.HERO_BLOCK_RESTORATION.get();
                            Config.HERO_BLOCK_RESTORATION.set(newValue);
                            Config.heroBlockRestoration = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.block_restoration", newValue));
                        })
                .pos(col1X, startY + spacingY)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.block_restoration.tooltip")))
                .build()
        );

        // 4. Clean Items Toggle
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.clean_items", Config.HERO_CLEAN_ITEMS.get()),
                        button -> {
                            boolean newValue = !Config.HERO_CLEAN_ITEMS.get();
                            Config.HERO_CLEAN_ITEMS.set(newValue);
                            Config.heroCleanItems = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.clean_items", newValue));
                        })
                .pos(col2X, startY + spacingY)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.clean_items.tooltip")))
                .build()
        );

        // 5. Cleave Skill Toggle
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.cleave_skill", Config.CLEAVE_SKILL_ENABLED.get()),
                        button -> {
                            boolean newValue = !Config.CLEAVE_SKILL_ENABLED.get();
                            Config.CLEAVE_SKILL_ENABLED.set(newValue);
                            Config.cleaveSkillEnabled = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.cleave_skill", newValue));
                        })
                .pos(col1X, startY + spacingY * 2)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.cleave_skill.tooltip")))
                .build()
        );

        // 6. Soul Bound Pact
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.soul_bound_pact", Config.SOUL_BOUND_PACT_ENABLED.get()),
                        button -> {
                            boolean newValue = !Config.SOUL_BOUND_PACT_ENABLED.get();
                            Config.SOUL_BOUND_PACT_ENABLED.set(newValue);
                            Config.soulBoundPactEnabled = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.soul_bound_pact", newValue));
                        })
                .pos(col2X, startY + spacingY * 2)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.soul_bound_pact.tooltip")))
                .build()
        );

        // 7. Abyssal Gaze
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.abyssal_gaze", Config.ABYSSAL_GAZE_ENABLED.get()),
                        button -> {
                            boolean newValue = !Config.ABYSSAL_GAZE_ENABLED.get();
                            Config.ABYSSAL_GAZE_ENABLED.set(newValue);
                            Config.abyssalGazeEnabled = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.abyssal_gaze", newValue));
                        })
                .pos(col1X, startY + spacingY * 3)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.abyssal_gaze.tooltip")))
                .build()
        );

        // 8. Transcendence Permit
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.transcendence_permit", Config.TRANSCENDENCE_PERMIT_ENABLED.get()),
                        button -> {
                            boolean newValue = !Config.TRANSCENDENCE_PERMIT_ENABLED.get();
                            Config.TRANSCENDENCE_PERMIT_ENABLED.set(newValue);
                            Config.transcendencePermitEnabled = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.transcendence_permit", newValue));
                        })
                .pos(col2X, startY + spacingY * 3)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.transcendence_permit.tooltip")))
                .build()
        );

        // 9. AI Vision Toggle
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.ai_vision", Config.AI_VISION_ENABLED.get()),
                        button -> {
                            boolean newValue = !Config.AI_VISION_ENABLED.get();
                            Config.AI_VISION_ENABLED.set(newValue);
                            Config.aiVisionEnabled = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.ai_vision", newValue));
                        })
                .pos(col1X, startY + spacingY * 4)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.ai_vision.tooltip")))
                .build()
        );

        // 10. AI Vision Interval
        int[] intervals = {10, 20, 30, 60, 120, 300};
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.ai_vision_interval", Config.AI_VISION_INTERVAL.get()),
                        button -> {
                            int current = Config.AI_VISION_INTERVAL.get();
                            int next = intervals[0];
                            for (int i = 0; i < intervals.length; i++) {
                                if (intervals[i] > current) {
                                    next = intervals[i];
                                    break;
                                }
                            }
                            if (current >= intervals[intervals.length - 1]) next = intervals[0];

                            Config.AI_VISION_INTERVAL.set(next);
                            Config.aiVisionInterval = next;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.ai_vision_interval", next));
                        })
                .pos(col2X, startY + spacingY * 4)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.ai_vision_interval.tooltip")))
                .build()
        );

        // 11. 更新检查器 Toggle (第 5 行)
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.update_checker", Config.ENABLE_UPDATE_CHECKER.get()),
                        button -> {
                            boolean newValue = !Config.ENABLE_UPDATE_CHECKER.get();
                            Config.ENABLE_UPDATE_CHECKER.set(newValue);
                            Config.enableUpdateChecker = newValue;
                            Config.SPEC.save();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.update_checker", newValue));
                        })
                .pos(col1X, startY + spacingY * 5)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.update_checker.tooltip")))
                .build()
        );

        // ================= 动态自适应排版系统 =================
        // 默认将这些组件“锚定”在屏幕的最底端，基于 this.height 向上推算
        int doneButtonY = this.height - 28; // 完成按钮紧贴屏幕底端
        int noteY = doneButtonY - 14;       // 提示文字在按钮上方
        int editBoxY = noteY - 26;          // 输入框在提示文字上方

        // 【碰撞检测】如果窗口极其扁平（比如高度被压到了240像素以下），上下可能会重叠
        // 强制计算一个最小的Y坐标，保证无论如何都不覆盖上方的第5行按钮
        int minEditBoxY = startY + spacingY * 5 + buttonHeight + 15;
        if (editBoxY < minEditBoxY) {
            editBoxY = minEditBoxY;
            noteY = editBoxY + 26;
            doneButtonY = noteY + 14;
        }

        // 记录输入框的Y坐标供 render 绘制标题使用
        this.currentEditBoxY = editBoxY;

        // 12. AI 语言风格输入框
        EditBox languageStyleBox = new EditBox(this.font, centerX - 150, editBoxY, 300, 20, Component.translatable("gui.herobrine_companion.config.ai_language_style"));
        languageStyleBox.setMaxLength(256);
        languageStyleBox.setValue(Config.AI_LANGUAGE_STYLE.get());
        languageStyleBox.setTooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.ai_language_style.tooltip")));
        languageStyleBox.setResponder(val -> {
            Config.AI_LANGUAGE_STYLE.set(val);
            Config.aiLanguageStyle = val;
        });
        this.addRenderableWidget(languageStyleBox);

        // 13. 返回按钮
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .pos(centerX - 100, doneButtonY)
                .size(200, 20)
                .build());

        // 14. 重启提示标签
        this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.config.restart_note").withStyle(ChatFormatting.RED), button -> {})
                .pos(centerX - 100, noteY)
                .size(200, 10)
                .build()
        ).active = false;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        // 主标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        // 绘制输入框的小标题提示 (动态跟随在输入框的正上方)
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.config.ai_language_style"), this.width / 2, this.currentEditBoxY - 12, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Config.SPEC.save();
        this.minecraft.setScreen(this.lastScreen);
    }
}