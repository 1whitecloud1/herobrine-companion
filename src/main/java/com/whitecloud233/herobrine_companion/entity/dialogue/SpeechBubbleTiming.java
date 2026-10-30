package com.whitecloud233.herobrine_companion.entity.dialogue;

import com.whitecloud233.herobrine_companion.config.Config;
import net.minecraft.network.chat.Component;

/**
 * 头顶气泡显示时长的统一计算入口。
 *
 * <p>基准是模组设置里的「气泡显示时长」({@code speechBubbleDurationSeconds}，单位秒)，
 * 台词越长在此基础上自动延长，避免长句还没读完气泡就消失；
 * 另有硬下限与 30 秒上限保护，任何一侧算错都不会出现"闪一下就没"或"永久挂着"的气泡。</p>
 */
public final class SpeechBubbleTiming {
    /** 硬下限：即使配置被写坏，气泡也至少显示 2 秒。 */
    private static final int MIN_TICKS_FLOOR = 40;
    /** 上限：最长 30 秒，避免气泡无限期停在头顶。 */
    private static final int MAX_TICKS = 600;
    /** 每多一个字延长 0.25 秒。 */
    private static final int TICKS_PER_CHARACTER = 5;

    private SpeechBubbleTiming() {
    }

    public static int durationTicks(Component line) {
        return durationTicks(line == null ? "" : line.getString());
    }

    public static int durationTicks(String text) {
        int length = text == null ? 0 : text.trim().length();
        int configured = Math.max(MIN_TICKS_FLOOR, Config.speechBubbleDurationSeconds * 20);
        int scaledByLength = MIN_TICKS_FLOOR + length * TICKS_PER_CHARACTER;
        return Math.max(configured, Math.min(MAX_TICKS, scaledByLength));
    }
}
