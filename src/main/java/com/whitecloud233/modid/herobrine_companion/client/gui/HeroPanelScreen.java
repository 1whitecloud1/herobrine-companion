package com.whitecloud233.modid.herobrine_companion.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Hero 系列"面板页"的公共基类。
 *
 * <p>这些页面此前各自写了两段一模一样的样板：{@code renderBackground} 空方法体
 * （禁用原版世界模糊 + 渐变遮罩）与 {@code isPauseScreen() -> false}。样板本身只有两行，
 * 但漏写任何一处都会让页面莫名其妙地暂停游戏或糊上一层灰，因此收进基类，
 * 新增页面只要 {@code extends HeroPanelScreen} 就自动正确。</p>
 *
 * <p>面板几何不在这里拍板，统一交给 {@link HeroTheme#frame}：设计尺寸、最小尺寸、
 * 窗口间距三个参数按页面传入，钳制与居中的算法只有一份。</p>
 *
 * <p><b>不适用</b>：需要保留原版背景的页面（世界内正常交互界面）、
 * 需要半透明黑色遮罩的二次确认弹窗（用 {@link HeroTheme#dimOverlay}）、
 * 以及继承 {@code ChatScreen} / {@code AbstractContainerScreen} 的页面。</p>
 */
public abstract class HeroPanelScreen extends Screen {

    /** 面板与窗口边缘的最小间距（沿用各页面历史值 8）。 */
    protected static final int PANEL_MARGIN = 8;

    protected HeroPanelScreen(Component title) {
        super(title);
    }

    /**
     * 按窗口钳制出居中的面板几何。
     *
     * @param maxWidth  设计宽度（窗口够大时的宽度）
     * @param maxHeight 设计高度
     * @param minWidth  允许压缩到的最小宽度
     * @param minHeight 允许压缩到的最小高度
     */
    protected final HeroTheme.Frame panelFrame(int maxWidth, int maxHeight, int minWidth, int minHeight) {
        return HeroTheme.frame(this.width, this.height, maxWidth, maxHeight, minWidth, minHeight, PANEL_MARGIN);
    }

    /**
     * 面板自己铺底，禁用原版模糊与渐变遮罩。
     *
     * <p><b>1.21.1 注意</b>：{@code Screen#renderBackground} 在那里是四参
     * {@code (GuiGraphics, int, int, float)}，同步到 1.21.1 时必须一并改成四参签名，
     * 否则会变成"能编译但永远不会被调用"的空重载。</p>
     */
    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
    }

    /** 面板叠加在世界之上，不暂停游戏。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 关屏并回到上一页。
     *
     * <p>crosschat 系列页面原先各自写了一份
     * {@code Minecraft.getInstance().setScreen(this.previousScreen)}，
     * 与 {@code onClose()} 组成"返回按钮 / ESC / 关闭"三条等价路径。
     * 收敛到基类后，子类只需在 {@link #init()} 里记住 {@code previousScreen} 并调用本方法。</p>
     */
    protected final void returnToPrevious(Screen previousScreen) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(previousScreen);
        }
    }
}
