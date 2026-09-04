package com.whitecloud233.herobrine_companion.config;

import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.SyncServerConfigPacket;
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
        int buttonWidth = 150;
        int buttonHeight = 18;

        // 紧凑排布，给小屏幕留出更多呼吸空间
        int spacingY = 20;
        // 顶部按钮群固定起始 Y 坐标
        int startY = 25;

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
                            sendServerConfigSync();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.poem_explosion", newValue));
                        })
                .bounds(col1X, startY, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.poem_explosion.tooltip")))
                .build()
        );

        // 2. Hero King Aura
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.hero_aura", Config.HERO_KING_AURA_ENABLED.get()),
                        button -> {
                            boolean newValue = !Config.HERO_KING_AURA_ENABLED.get();
                            Config.HERO_KING_AURA_ENABLED.set(newValue);
                            Config.heroKingAuraEnabled = newValue;
                            Config.SPEC.save();
                            sendServerConfigSync();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.hero_aura", newValue));
                        })
                .bounds(col2X, startY, buttonWidth, buttonHeight)
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
                            sendServerConfigSync();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.block_restoration", newValue));
                        })
                .bounds(col1X, startY + spacingY, buttonWidth, buttonHeight)
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
                            sendServerConfigSync();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.clean_items", newValue));
                        })
                .bounds(col2X, startY + spacingY, buttonWidth, buttonHeight)
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
                            sendServerConfigSync();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.cleave_skill", newValue));
                        })
                .bounds(col1X, startY + spacingY * 2, buttonWidth, buttonHeight)
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
                            sendServerConfigSync();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.soul_bound_pact", newValue));
                        })
                .bounds(col2X, startY + spacingY * 2, buttonWidth, buttonHeight)
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
                            sendServerConfigSync();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.abyssal_gaze", newValue));
                        })
                .bounds(col1X, startY + spacingY * 3, buttonWidth, buttonHeight)
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
                            sendServerConfigSync();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.transcendence_permit", newValue));
                        })
                .bounds(col2X, startY + spacingY * 3, buttonWidth, buttonHeight)
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
                .bounds(col1X, startY + spacingY * 4, buttonWidth, buttonHeight)
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
                            sendServerConfigSync();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.ai_vision_interval", next));
                        })
                .bounds(col2X, startY + spacingY * 4, buttonWidth, buttonHeight)
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
                .bounds(col1X, startY + spacingY * 5, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.update_checker.tooltip")))
                .build()
        );

        // 12. 觉醒怪物 AI 对话 Toggle (第 5 行)
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.awakened_mob_ai_dialogue", Config.AWAKENED_MOB_AI_DIALOGUE_ENABLED.get()),
                        button -> {
                            boolean newValue = !Config.AWAKENED_MOB_AI_DIALOGUE_ENABLED.get();
                            Config.AWAKENED_MOB_AI_DIALOGUE_ENABLED.set(newValue);
                            Config.awakenedMobAiDialogueEnabled = newValue;
                            Config.SPEC.save();
                            sendServerConfigSync();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.awakened_mob_ai_dialogue", newValue));
                        })
                .bounds(col2X, startY + spacingY * 5, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.awakened_mob_ai_dialogue.tooltip")))
                .build()
        );

        // 13. Leaf Vanish Toggle
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.herobrine_companion.config.leaf_vanish", Config.HERO_LEAF_VANISH_ENABLED.get()),
                        button -> {
                            boolean newValue = !Config.HERO_LEAF_VANISH_ENABLED.get();
                            Config.HERO_LEAF_VANISH_ENABLED.set(newValue);
                            Config.heroLeafVanishEnabled = newValue;
                            Config.SPEC.save();
                            sendServerConfigSync();
                            button.setMessage(Component.translatable("gui.herobrine_companion.config.leaf_vanish", newValue));
                        })
                .bounds(col1X, startY + spacingY * 6, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.herobrine_companion.config.leaf_vanish.tooltip")))
                .build()
        );

        // ================= 动态自适应排版系统 =================
        // 默认将这些组件“锚定”在屏幕的最底端，基于 this.height 向上推算
        int doneButtonY = this.height - 28; // 完成按钮紧贴屏幕底端
        int noteY = doneButtonY - 14;       // 提示文字在按钮上方

        // 【碰撞检测】如果窗口极其扁平（比如高度被压到了240像素以下），上下可能会重叠
        // 强制计算一个最小的Y坐标，保证无论如何都不覆盖上方的配置按钮
        int minNoteY = startY + spacingY * 6 + buttonHeight + 15;
        if (noteY < minNoteY) {
            noteY = minNoteY;
            doneButtonY = noteY + 14;
        }

        // 14. 返回按钮
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(centerX - 100, doneButtonY, 200, 20)
                .build());

        // 16. 重启提示标签
        this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.config.restart_note").withStyle(ChatFormatting.RED), button -> {})
                .bounds(centerX - 100, noteY, 200, 10)
                .build()
        ).active = false;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 主标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void sendServerConfigSync() {
        PacketHandler.sendToServer(new SyncServerConfigPacket(
                Config.poemOfTheEndExplosion,
                Config.heroKingAuraEnabled,
                Config.heroBlockRestoration,
                Config.heroCleanItems,
                Config.cleaveSkillEnabled,
                Config.soulBoundPactEnabled,
                Config.abyssalGazeEnabled,
                Config.transcendencePermitEnabled,
                Config.aiVisionInterval,
                Config.awakenedMobAiDialogueEnabled,
                Config.heroLeafVanishEnabled
        ));
    }

    @Override
    public void onClose() {
        Config.SPEC.save();
        this.minecraft.setScreen(this.lastScreen);
    }
}
