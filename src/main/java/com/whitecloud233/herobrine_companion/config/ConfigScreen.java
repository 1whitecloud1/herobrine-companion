package com.whitecloud233.herobrine_companion.config;

import com.whitecloud233.herobrine_companion.client.network.ClientServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ConfigScreen extends Screen {
    public static final IConfigScreenFactory FACTORY = (container, screen) -> new ConfigScreen(screen);

    private static final String PREFIX = "gui.herobrine_companion.config.";
    private final Screen lastScreen;
    private final List<Runnable> serverRefreshers = new ArrayList<>();
    private long lastRevision = -1;
    private boolean querySent;
    private Button status;

    public ConfigScreen(Screen lastScreen) {
        super(Component.translatable(PREFIX + "title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        super.init();
        serverRefreshers.clear();
        int left = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        // 矮窗口（如 1080p 自动缩放的 270 高）下压缩起始与行距，防止新增的战斗限制三行把按钮挤出屏幕。
        int top = this.height < 340 ? 8 : 25;
        int row = this.height < 340 ? 18 : 20;
        addRuleToggle(ServerRule.POEM_EXPLOSION, "poem_explosion", left, top, false);
        addRuleToggle(ServerRule.KING_AURA, "hero_aura", right, top, false);
        addRuleToggle(ServerRule.BLOCK_RESTORATION, "block_restoration", left, top + row, false);
        addRuleToggle(ServerRule.CLEAN_ITEMS, "clean_items", right, top + row, false);
        addRuleToggle(ServerRule.CLEAVE_SKILL, "cleave_skill", left, top + row * 2, false);
        addRuleToggle(ServerRule.SOUL_BOUND_PACT, "soul_bound_pact", right, top + row * 2, false);
        addRuleToggle(ServerRule.ABYSSAL_GAZE, "abyssal_gaze", left, top + row * 3, false);
        addRuleToggle(ServerRule.TRANSCENDENCE_PERMIT, "transcendence_permit", right, top + row * 3, false);
        addLocalToggle(Config.AI_VISION_ENABLED, "ai_vision", left, top + row * 4, true);
        addRuleCycle(ServerRule.AI_VISION_INTERVAL, "ai_vision_interval", right, top + row * 4,
                new int[]{10, 20, 30, 60, 120, 300});
        addLocalToggle(Config.ENABLE_UPDATE_CHECKER, "update_checker", left, top + row * 5, false);
        addRuleToggle(ServerRule.AWAKENED_DIALOGUE, "awakened_mob_ai_dialogue", right, top + row * 5, false);
        addRuleToggle(ServerRule.LEAF_VANISH, "leaf_vanish", left, top + row * 6, false);
        addLocalToggle(Config.AI_VISION_BUBBLE_ONLY, "ai_vision_bubble_only", right, top + row * 6, true);
        addLocalToggle(Config.AI_VISION_SPEECH_ENABLED, "ai_vision_speech", left, top + row * 7, true);
        addLocalCycle(Config.SPEECH_BUBBLE_DURATION_SECONDS, "bubble_duration", right, top + row * 7,
                new int[]{4, 6, 8, 12, 16, 20, 30});
        addRuleToggle(ServerRule.PRANKSTER_FAKE_CHEST, "fake_chest", left, top + row * 8, true);
        addRuleToggle(ServerRule.POEM_ACTIONS, "poem_actions", right, top + row * 8, true);
        addRuleCycle(ServerRule.BATTLE_MODE_TRUST_THRESHOLD, "battle_trust_threshold", left, top + row * 9,
                new int[]{0, 100, 150, 200, 250, 300, 500, 1000});
        addRuleCycle(ServerRule.BATTLE_MODE_DURATION_SECONDS, "battle_duration", right, top + row * 9,
                new int[]{0, 60, 120, 300, 600, 1200, 1800, 3600});
        addRuleCycle(ServerRule.BATTLE_MODE_COOLDOWN_SECONDS, "battle_cooldown", left, top + row * 10,
                new int[]{0, 60, 120, 300, 600, 1200, 1800, 3600});

        addLocalToggle(Config.POEM_OUTLINE_ENABLED, "poem_outline", right, top + row * 10, true);

        int noteY = Math.max(this.height - 42, top + row * 10 + 18 + 15);
        status = addRenderableWidget(Button.builder(Component.empty(), button -> ClientServerConfig.requestCurrent())
                .pos(this.width / 2 - 150, noteY).size(300, 10).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .pos(this.width / 2 - 100, noteY + 14).size(200, 20).build());
        if (!querySent) {
            ClientServerConfig.requestCurrent();
            querySent = true;
        }
        refreshServerControls();
    }

    private void addRuleToggle(ServerRule rule, String key, int x, int y, boolean readableState) {
        Button button = addRenderableWidget(Button.builder(Component.empty(), clicked -> {
            int next = ClientServerConfig.snapshot().enabled(rule) ? 0 : 1;
            ClientServerConfig.update(rule, next);
            refreshServerControls();
        }).pos(x, y).size(150, 18).tooltip(tooltip(key)).build());
        serverRefreshers.add(() -> {
            Object value = !ClientServerConfig.hasSnapshot() ? "?" : readableState
                    ? stateLabel(ClientServerConfig.snapshot().enabled(rule)) : ClientServerConfig.snapshot().enabled(rule);
            button.setMessage(Component.translatableEscape(PREFIX + key, value));
            button.active = ClientServerConfig.editable();
        });
    }

    private void addRuleCycle(ServerRule rule, String key, int x, int y, int[] options) {
        Button button = addRenderableWidget(Button.builder(Component.empty(), clicked -> {
            ClientServerConfig.update(rule, nextValue(ClientServerConfig.snapshot().value(rule), options));
            refreshServerControls();
        }).pos(x, y).size(150, 18).tooltip(tooltip(key)).build());
        serverRefreshers.add(() -> {
            button.setMessage(Component.translatable(PREFIX + key,
                    ClientServerConfig.hasSnapshot() ? ClientServerConfig.snapshot().value(rule) : "?"));
            button.active = ClientServerConfig.editable();
        });
    }

    private void addLocalToggle(ModConfigSpec.BooleanValue option, String key, int x, int y, boolean readableState) {
        addRenderableWidget(Button.builder(Component.translatable(PREFIX + key,
                        readableState ? stateLabel(option.get()) : option.get()), button -> {
            boolean next = !option.get();
            LocalConfigAccess.update(option, next);
            button.setMessage(Component.translatable(PREFIX + key, readableState ? stateLabel(next) : next));
        }).pos(x, y).size(150, 18).tooltip(tooltip(key)).build());
    }

    private void addLocalCycle(ModConfigSpec.IntValue option, String key, int x, int y, int[] values) {
        addRenderableWidget(Button.builder(Component.translatable(PREFIX + key, option.get()), button -> {
            int next = nextValue(option.get(), values);
            LocalConfigAccess.update(option, next);
            button.setMessage(Component.translatable(PREFIX + key, next));
        }).pos(x, y).size(150, 18).tooltip(tooltip(key)).build());
    }

    private static int nextValue(int current, int[] values) {
        for (int value : values) if (value > current) return value;
        return values[0];
    }

    private static Tooltip tooltip(String key) {
        var text = Component.translatable(PREFIX + key + ".tooltip");
        if ("hero_aura".equals(key)) text.append(Component.translatable(PREFIX + "restart_required").withStyle(ChatFormatting.RED));
        return Tooltip.create(text);
    }

    private void refreshServerControls() {
        serverRefreshers.forEach(Runnable::run);
        String key = ClientServerConfig.statusKey();
        status.setMessage(Component.translatable(key.isEmpty() ? PREFIX + "restart_note" : key));
        status.active = key.equals(PREFIX + "server_timeout");
        status.setTooltip(Tooltip.create(Component.translatable(key.isEmpty() ? PREFIX + "restart_note" : key)));
        lastRevision = ClientServerConfig.revision();
    }

    @Override
    public void tick() {
        super.tick();
        if (lastRevision != ClientServerConfig.revision()) refreshServerControls();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private static Component stateLabel(boolean enabled) {
        return Component.translatable(PREFIX + (enabled ? "state.enabled" : "state.disabled"));
    }

    @Override
    public void onClose() { this.minecraft.setScreen(this.lastScreen); }
}
