package com.whitecloud233.modid.herobrine_companion.config;

import com.whitecloud233.modid.herobrine_companion.client.gui.HeroScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Read-only, scrollable startup report. Copying is always an explicit player action. */
public final class LocalModelDiagnosticsScreen extends Screen {
    private final Screen parent;
    private final String details;
    private List<FormattedCharSequence> lines = List.of();
    private int left;
    private int contentWidth;
    private int bodyBottom;
    private int visibleLines;
    private int scrollLine;
    private static final int BODY_TOP = 48;

    public LocalModelDiagnosticsScreen(Screen parent, String details) {
        super(Component.translatable("gui.herobrine_companion.local_model.diagnostics_title"));
        this.parent = parent;
        this.details = details;
    }

    @Override
    protected void init() {
        this.left = Math.max(12, (this.width - 680) / 2);
        this.contentWidth = Math.max(1, this.width - this.left * 2);
        this.bodyBottom = Math.max(BODY_TOP + 16, this.height - 44);
        this.visibleLines = Math.max(1, (this.bodyBottom - BODY_TOP - 8) / (this.font.lineHeight + 2));
        this.lines = this.font.split(Component.literal(this.details), Math.max(1, this.contentWidth - 16));
        this.scrollTo(this.scrollLine);
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                this.width / 2 - 124, this.height - 30, 120, 20,
                Component.translatable("gui.herobrine_companion.local_model.copy_diagnostics"), button -> {
                    this.minecraft.keyboardHandler.setClipboard(this.details);
                    button.setMessage(Component.translatable("gui.herobrine_companion.local_model.diagnostics_copied"));
                }, null));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                this.width / 2 + 4, this.height - 30, 120, 20,
                Component.translatable("gui.herobrine_companion.local_model.back"), button -> this.onClose(), null));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xF018191E);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.herobrine_companion.local_model.diagnostics_hint"), this.width / 2, 30, 0xAAAAAA);
        graphics.fill(this.left, BODY_TOP, this.left + this.contentWidth, this.bodyBottom, 0xFF111216);
        graphics.enableScissor(this.left, BODY_TOP, this.left + this.contentWidth, this.bodyBottom);
        int end = Math.min(this.lines.size(), this.scrollLine + this.visibleLines);
        for (int line = this.scrollLine; line < end; line++) {
            graphics.drawString(this.font, this.lines.get(line), this.left + 5,
                    BODY_TOP + 4 + (line - this.scrollLine) * (this.font.lineHeight + 2), 0xE0E0E0, false);
        }
        graphics.disableScissor();
        int maxScroll = Math.max(0, this.lines.size() - this.visibleLines);
        if (maxScroll > 0) {
            int trackHeight = this.bodyBottom - BODY_TOP;
            int thumbHeight = Math.max(12, trackHeight * this.visibleLines / this.lines.size());
            int top = BODY_TOP + (trackHeight - thumbHeight) * this.scrollLine / maxScroll;
            graphics.fill(this.left + this.contentWidth - 4, top, this.left + this.contentWidth - 1,
                    top + thumbHeight, 0xFF888888);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void scrollTo(int line) {
        this.scrollLine = Math.max(0, Math.min(line, Math.max(0, this.lines.size() - this.visibleLines)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        this.scrollTo(this.scrollLine - (int) Math.signum(delta) * 3);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_PAGE_UP -> this.scrollTo(this.scrollLine - this.visibleLines);
            case GLFW.GLFW_KEY_PAGE_DOWN -> this.scrollTo(this.scrollLine + this.visibleLines);
            case GLFW.GLFW_KEY_HOME -> this.scrollTo(0);
            case GLFW.GLFW_KEY_END -> this.scrollTo(this.lines.size());
            case GLFW.GLFW_KEY_UP -> this.scrollTo(this.scrollLine - 1);
            case GLFW.GLFW_KEY_DOWN -> this.scrollTo(this.scrollLine + 1);
            default -> { return super.keyPressed(key, scanCode, modifiers); }
        }
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
