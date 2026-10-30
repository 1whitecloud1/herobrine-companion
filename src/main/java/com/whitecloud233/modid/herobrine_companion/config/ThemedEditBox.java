package com.whitecloud233.modid.herobrine_companion.config;

import com.whitecloud233.modid.herobrine_companion.client.gui.HeroTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * HeroScreen 风格的单行输入框。
 *
 * <p>在 {@link MouseSelectableEditBox}（鼠标拖选 + Ctrl+A/C/X/V + 方向键）之上只改外观。</p>
 *
 * <p><b>为什么保留原版描边</b>：1.20.1 的 {@code EditBox} 在 {@code bordered=false} 时把文字画在
 * {@code getY()}，也就是贴住框的顶边；只有 {@code bordered=true} 才会走
 * {@code getY() + (height - 8) / 2} 做垂直居中。关掉描边虽然省了黑色底，却会让文字明显偏上。
 * 因此这里保留 {@code bordered=true} 拿它的居中排版，再在外圈覆一层主题边框，
 * 把它自带的灰白描边盖掉——两者位置只差 1px，视觉上完全被替换。</p>
 */
public class ThemedEditBox extends MouseSelectableEditBox {
    private static final int TEXT_COLOR = 0xFFE6E6E6;
    private static final int TEXT_DISABLED = 0xFF7A7A7A;

    /** 1.20.1 的 EditBox 没有 isEditable()，这里自己记一份用于决定边框与字色。 */
    private boolean editable = true;

    public ThemedEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        // 必须保持 bordered：它是原版"文字垂直居中"的唯一开关（见类注释）。
        this.setBordered(true);
        this.setTextColor(TEXT_COLOR);
        this.setTextColorUneditable(TEXT_DISABLED);
    }

    public boolean isEditable() {
        return this.editable;
    }

    @Override
    public void setEditable(boolean editable) {
        this.editable = editable;
        super.setEditable(editable);
        this.setTextColor(editable ? TEXT_COLOR : TEXT_DISABLED);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 原版：铺内底 + 垂直居中排文字 + 自己描 1px 边（画在框外一圈）。
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        // 覆写那一圈描边为主题色（位置正好重合，等于换色）。
        HeroTheme.outlineInput(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(),
                this.isFocused(), this.editable);
    }
}
