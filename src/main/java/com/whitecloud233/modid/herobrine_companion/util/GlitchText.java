package com.whitecloud233.modid.herobrine_companion.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * 物品名的「乱码抖动」样式生成器。
 *
 * <p>它只改样式、不改文字：被判为乱码的字符保留原字符，只额外挂上
 * {@link Style#withObfuscated(Boolean)}。原版字体渲染器每帧都会为这些字符随机挑选
 * 同宽字形，于是看上去一直在变成别的字；而 {@code getString()} 仍然返回原文，
 * JEI 搜索、AI 物品描述、按名字比较物品的代码读到的都是真名。</p>
 *
 * <p>同一个 {@code millis} 下的结果固定，跨帧才变化，因此工具提示每帧重建名字时得到的是
 * 连续动画，而不是无法复现的随机噪声。这里只用到公共聊天组件，不引用任何客户端类，
 * 客户端与服务端都能安全加载；是否真正显示乱码由调用方按阵营决定
 * （见 {@code DestructionGodSpawnEggItem}）。</p>
 */
public final class GlitchText {
    /** 每 90ms 重掷一次骰子（约 11 帧/秒）：成片的字符转移比逐帧翻动更容易看清。 */
    public static final long STEP_MILLIS = 90L;
    /** 单个字符进入乱码态的概率（百分比）。取 1/3 上下：既认得出原名，又一直在坏。 */
    public static final int GLITCH_PERCENT = 34;
    /** 稳定字符的底色。 */
    private static final ChatFormatting BASE_COLOR = ChatFormatting.DARK_RED;
    /** 乱码字符的配色池：灰、深灰、灰、亮红，按第二个骰子的结果轮流取。 */
    private static final ChatFormatting[] FLICKER_COLORS = {
            ChatFormatting.GRAY, ChatFormatting.DARK_GRAY, ChatFormatting.GRAY, ChatFormatting.RED
    };
    /** 两个互不相同的盐，保证「是否乱码」与「乱码成什么颜色」不会同步。 */
    private static final long GLITCH_SALT = 0x9E3779B97F4A7C15L;
    private static final long COLOR_SALT = 0xC2B2AE3D27D4EB4FL;

    private GlitchText() {
    }

    /**
     * 把 {@code text} 拆成逐字符的样式序列。
     *
     * @param text   原始文本（通常是已本地化的物品名）
     * @param millis 当前时间戳，动画按 {@link #STEP_MILLIS} 分帧
     */
    public static Component animate(String text, long millis) {
        MutableComponent animated = Component.empty();
        if (text == null || text.isEmpty()) {
            return animated;
        }
        long frame = Math.floorDiv(millis, STEP_MILLIS);
        int[] codePoints = text.codePoints().toArray();
        for (int index = 0; index < codePoints.length; index++) {
            int codePoint = codePoints[index];
            String character = new String(Character.toChars(codePoint));
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                // 空白保持原样，乱码不去动排版。
                animated.append(Component.literal(character));
                continue;
            }
            Style style;
            if (bucket(mix(frame, index, GLITCH_SALT), 100) < GLITCH_PERCENT) {
                // 乱码态：换色 + 原版逐帧随机字形。
                style = Style.EMPTY
                        .withColor(FLICKER_COLORS[bucket(mix(frame, index, COLOR_SALT), FLICKER_COLORS.length)])
                        .withObfuscated(true);
            } else {
                // 稳定态只带底色，不写 obfuscated=false，少一层样式负担。
                style = Style.EMPTY.withColor(BASE_COLOR);
            }
            animated.append(Component.literal(character).withStyle(style));
        }
        return animated;
    }

    /** 把 (帧号, 字符序号, 盐) 混成一个稳定的伪随机数：同帧同字符永远得到同一个值。 */
    private static long mix(long frame, int index, long salt) {
        long hash = salt + frame * 0x9E3779B97F4A7C15L + (index + 1L) * 0xC2B2AE3D27D4EB4FL;
        hash ^= hash >>> 33;
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= hash >>> 33;
        hash *= 0xC4CEB9FE1A85EC53L;
        hash ^= hash >>> 33;
        return hash;
    }

    private static int bucket(long hash, int size) {
        return Math.floorMod(hash, size);
    }
}
