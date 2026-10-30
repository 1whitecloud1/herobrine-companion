package com.whitecloud233.herobrine_companion.util;

import net.minecraft.network.chat.Component;

/**
 * {@link GlitchText} 的回归检查。
 *
 * <p>乱码特效只允许改样式、不允许改文字：一旦 {@code getString()} 不再等于原文，
 * JEI 搜索、AI 物品描述、按名字比较物品的代码都会开始出错。这里同时钉住
 * 「同帧可复现」与「跨帧会变」，避免动画退化成静态噪声或逐帧全随机闪烁。</p>
 */
public final class GlitchTextChecks {
    private static final String SAMPLE = "不要在已经游玩的存档释放！";
    private static int checks;

    public static void main(String[] args) {
        Component first = GlitchText.animate(SAMPLE, 1_000L);
        check("plain text is preserved", SAMPLE.equals(first.getString()));
        check("same timestamp is reproducible", first.toString().equals(GlitchText.animate(SAMPLE, 1_000L).toString()));
        check("next frame changes",
                !first.toString().equals(GlitchText.animate(SAMPLE, 1_000L + GlitchText.STEP_MILLIS).toString()));

        check("empty text stays empty", GlitchText.animate("", 42L).getString().isEmpty());
        check("null text stays empty", GlitchText.animate(null, 42L).getString().isEmpty());

        String spaced = "a b";
        check("whitespace is kept verbatim", spaced.equals(GlitchText.animate(spaced, 7L).getString()));

        int glitched = 0;
        int total = 0;
        int frames = 200;
        for (int frame = 0; frame < frames; frame++) {
            String rendered = GlitchText.animate(SAMPLE, frame * GlitchText.STEP_MILLIS).toString();
            total += SAMPLE.codePointCount(0, SAMPLE.length());
            for (int index = rendered.indexOf("obfuscated"); index >= 0; index = rendered.indexOf("obfuscated", index + 1)) {
                glitched++;
            }
        }
        check("characters do glitch", glitched > 0);
        double ratio = (double) glitched / total;
        check("glitch ratio stays near " + GlitchText.GLITCH_PERCENT + "% (was " + Math.round(ratio * 100) + "%)",
                Math.abs(ratio - GlitchText.GLITCH_PERCENT / 100.0) < 0.08);
        check("every frame keeps a color", first.toString().contains("color="));

        System.out.println("GlitchTextChecks: " + checks + " checks passed");
    }

    private static void check(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
