package com.whitecloud233.modid.herobrine_companion.config;

import com.whitecloud233.modid.herobrine_companion.client.event.ClientHooks;
import com.whitecloud233.modid.herobrine_companion.client.gui.HeroPanelScreen;
import com.whitecloud233.modid.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.modid.herobrine_companion.client.gui.HeroTheme;
import com.whitecloud233.modid.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.modid.herobrine_companion.client.service.LocalModelConnector;
import com.whitecloud233.modid.herobrine_companion.client.service.LocalModelEndpoint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Manual connection settings; the inference service is owned and managed by the player. */
public class LocalModelScreen extends HeroPanelScreen {
    private static final String PREFIX = "gui.herobrine_companion.local_model.";
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 6;
    private final Screen lastScreen;
    private String endpointDraft;
    private String modelDraft;
    private String keyDraft;
    private EditBox endpointBox;
    private EditBox modelBox;
    private EditBox keyBox;
    private HeroScreen.ThemedButton testButton;
    private HeroScreen.ThemedButton saveButton;
    private HeroScreen.ThemedButton disableButton;
    private Component status = Component.empty();
    private int statusColor = HeroTheme.INFO;
    private boolean testing;
    private int requestVersion;

    public LocalModelScreen(Screen lastScreen) {
        super(Component.translatable(PREFIX + "title"));
        this.lastScreen = lastScreen;
        LLMConfig.ensureLoaded();
        this.endpointDraft = LLMConfig.getLocalModelEndpoint().isBlank()
                ? LocalModelConnector.DEFAULT_ENDPOINT : LLMConfig.getLocalModelEndpoint();
        this.modelDraft = LLMConfig.getLocalModelId();
        this.keyDraft = LLMConfig.getLocalModelApiKey();
    }

    @Override
    protected void init() {
        super.init();
        Layout layout = this.layout();
        this.endpointBox = this.createBox(layout, 0, "endpoint", this.endpointDraft, 1024);
        this.modelBox = this.createBox(layout, 1, "model", this.modelDraft, 256);
        this.keyBox = this.createBox(layout, 2, "api_key", this.keyDraft, 512);
        this.keyBox.setFormatter((value, offset) -> FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY));
        this.testButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.left(), layout.actionY(), layout.buttonWidth(), BUTTON_HEIGHT,
                Component.translatable(PREFIX + "test"), button -> this.testConnection(),
                Tooltip.create(Component.translatable(PREFIX + "test_hint"))));
        this.saveButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.left() + layout.buttonWidth() + GAP, layout.actionY(), layout.buttonWidth(), BUTTON_HEIGHT,
                Component.translatable(PREFIX + "save"), button -> this.saveSettings(),
                Tooltip.create(Component.translatable(PREFIX + "save_hint"))));
        this.disableButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.left(), layout.actionY() + BUTTON_HEIGHT + GAP, layout.buttonWidth(), BUTTON_HEIGHT,
                Component.translatable(PREFIX + "disable"), button -> this.disableLocalModel(),
                Tooltip.create(Component.translatable(PREFIX + "disable_hint"))));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.left() + layout.buttonWidth() + GAP, layout.actionY() + BUTTON_HEIGHT + GAP,
                layout.buttonWidth(), BUTTON_HEIGHT,
                Component.translatable(PREFIX + "back"), button -> this.onClose(), null));
        this.updateButtons();
        this.setInitialFocus(this.endpointBox);
    }

    private EditBox createBox(Layout layout, int row, String name, String value, int maxLength) {
        EditBox box = this.addRenderableWidget(new ThemedEditBox(this.font, layout.left(),
                layout.fieldsY() + row * 36 + 11, layout.width(), BUTTON_HEIGHT, Component.translatable(PREFIX + name)));
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setTooltip(Tooltip.create(Component.translatable(PREFIX + name + "_hint")));
        box.setResponder(text -> {
            switch (row) {
                case 0 -> this.endpointDraft = text;
                case 1 -> this.modelDraft = text;
                case 2 -> this.keyDraft = text;
            }
            this.invalidateTest();
            this.status = Component.empty();
            this.updateButtons();
        });
        return box;
    }

    private boolean validSettings() {
        return !LocalModelEndpoint.normalize(this.endpointDraft).isEmpty() && !this.modelDraft.isBlank()
                && LocalModelConnector.isValidApiKey(this.keyDraft);
    }

    private void updateButtons() {
        boolean valid = this.validSettings();
        if (this.testButton != null) {
            this.testButton.active = valid && !this.testing;
            this.testButton.setMessage(Component.translatable(PREFIX + (this.testing ? "testing" : "test")));
        }
        if (this.saveButton != null) this.saveButton.active = valid;
        if (this.disableButton != null) this.disableButton.active = LLMConfig.isLocalModelConnected();
    }

    private void invalidateTest() {
        this.requestVersion++;
        this.testing = false;
    }

    private void testConnection() {
        if (this.testing || !this.validSettings()) return;
        this.testing = true;
        int request = ++this.requestVersion;
        this.status = Component.translatable(PREFIX + "testing");
        this.statusColor = HeroTheme.INFO;
        this.updateButtons();
        LocalModelConnector.probeEndpointAsync(this.endpointDraft, this.modelDraft, this.keyDraft)
                .thenAccept(result -> {
                    if (this.minecraft == null) return;
                    this.minecraft.execute(() -> {
                        if (this.minecraft.screen != this || this.requestVersion != request) return;
                        this.testing = false;
                        this.status = Component.translatable(result.status().translationKey(),
                                result.modelId(), result.statusCode());
                        this.statusColor = result.reachable() ? 0xFF80FF80 : HeroTheme.WARN;
                        this.updateButtons();
                    });
                });
    }

    private void saveSettings() {
        if (!this.validSettings()) return;
        this.invalidateTest();
        LocalModelConnector.applyConfig(this.endpointDraft, this.modelDraft, this.keyDraft);
        ClientHooks.setApiEnabled(false);
        this.status = Component.translatable(PREFIX + "saved", this.modelDraft.trim());
        this.statusColor = 0xFF80FF80;
        this.updateButtons();
    }

    private void disableLocalModel() {
        this.invalidateTest();
        LLMConfig.clearLocalModel();
        this.keyDraft = "";
        this.keyBox.setValue("");
        this.status = Component.translatable(PREFIX + "disabled_note");
        this.statusColor = HeroTheme.INFO;
        this.updateButtons();
    }

    @Override
    public void removed() {
        this.invalidateTest();
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        this.endpointBox.tick();
        this.modelBox.tick();
        this.keyBox.tick();
    }

    private Layout layout() {
        int width = Math.max(1, Math.min(460, this.width - 32));
        int height = Math.min(344, this.height - 16);
        int top = Math.max(8, (this.height - height) / 2);
        int left = (this.width - width) / 2;
        int hintLines = this.font.split(Component.translatable(PREFIX + "manual_hint"), width).size();
        int hintHeight = Math.min(hintLines * 11, Math.max(0, height - 258));
        int fieldsY = top + 28 + hintHeight;
        int actionY = top + height - 52;
        return new Layout(left, top, width, height, fieldsY, actionY, Math.max(1, (width - GAP) / 2));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = this.layout();
        HeroTheme.plainPanel(graphics, layout.left() - 8, layout.top(), layout.width() + 16, layout.height());
        graphics.drawCenteredString(this.font, this.title, this.width / 2, layout.top() + 10, HeroTheme.LABEL);
        this.drawWrapped(graphics, Component.translatable(PREFIX + "manual_hint"), layout.left(), layout.top() + 26,
                layout.width(), layout.fieldsY() - 2, HeroTheme.INFO);
        for (int row = 0; row < 3; row++) {
            String key = switch (row) { case 0 -> "endpoint"; case 1 -> "model"; default -> "api_key"; };
            graphics.drawString(this.font, Component.translatable(PREFIX + key), layout.left(),
                    layout.fieldsY() + row * 36, HeroTheme.TEXT, false);
        }
        Component message = this.status;
        int color = this.statusColor;
        if (message.getString().isEmpty()) {
            boolean valid = this.validSettings();
            message = Component.translatable(PREFIX + (!valid ? "required"
                    : LLMConfig.isLocalModelConnected() ? "state_enabled" : "state_disabled"));
            color = valid ? HeroTheme.INFO : HeroTheme.WARN;
        }
        this.drawWrapped(graphics, message, layout.left(), layout.fieldsY() + 112,
                layout.width(), layout.actionY() - 4, color);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int bottom, int color) {
        for (FormattedCharSequence line : this.font.split(text, width)) {
            if (y + this.font.lineHeight > bottom) break;
            graphics.drawString(this.font, line, x, y, color, false);
            y += 11;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return MouseSelectableEditBox.dispatchKey(this.editBoxes(), keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return MouseSelectableEditBox.dispatchChar(this.editBoxes(), codePoint, modifiers)
                || super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return MouseSelectableEditBox.dispatchDrag(this.editBoxes(), mouseX, mouseY, button, dragX, dragY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return MouseSelectableEditBox.dispatchRelease(this.editBoxes(), mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button);
    }

    private List<EditBox> editBoxes() {
        return List.of(this.endpointBox, this.modelBox, this.keyBox);
    }

    @Override
    public void onClose() {
        this.returnToPrevious(this.lastScreen);
    }

    private record Layout(int left, int top, int width, int height, int fieldsY, int actionY, int buttonWidth) {
    }
}
