package com.whitecloud233.herobrine_companion.client.tts;

import java.util.Locale;

/**
 * 音色性别标注（仅用于界面提示与挑选，不参与合成）。
 *
 * <p>系统音色的性别没有统一 API 可查，只能按已知音色名推断；推断不出来就显示问号，
 * 由玩家自己听一遍判断——不假装知道。</p>
 */
public enum VoiceGender {
    MALE("♂"),
    FEMALE("♀"),
    UNKNOWN("?");

    private final String marker;

    VoiceGender(String marker) {
        this.marker = marker;
    }

    public String marker() {
        return this.marker;
    }

    /** 是否女性（用于中文音色名的快速判别）。 */
    public boolean isFemale() {
        return this == FEMALE;
    }

    /**
     * 按音色标识推断性别。
     *
     * <p>覆盖三大平台的常见内置音色；命中不了返回 {@link #UNKNOWN}。</p>
     */
    public static VoiceGender infer(String voiceName) {
        if (voiceName == null || voiceName.isBlank()) {
            return UNKNOWN;
        }
        String normalized = voiceName.toLowerCase(Locale.ROOT);

        // 先判男性，避免 "xiao" 之类的子串误伤
        if (matches(normalized, "kangkang", "yunxi", "yunyang", "yunjian", "yunfeng", "yunhao",
                "david", "mark", "george", "james", "daniel", "guy", "alex", "fred", "ralph",
                "zhiwei", "li-mu", "liang", "male", "man", "boy", "yunze", "yunye")) {
            return MALE;
        }
        if (matches(normalized, "huihui", "yaoyao", "xiaoxiao", "xiaoyi", "xiaohan", "xiaomo",
                "xiaoxuan", "xiaorui", "xiaoshuang", "xiaochen", "xiaomeng", "xiaobei", "nuan",
                "zira", "hazel", "susan", "samantha", "victoria", "karen", "moira", "tessa",
                "tracy", "female", "woman", "girl", "lin", "mei-jia", "ting-ting", "sin-ji",
                "lili", "cherry", "serena", "female")) {
            return FEMALE;
        }
        return UNKNOWN;
    }

    private static boolean matches(String normalizedVoice, String... keywords) {
        for (String keyword : keywords) {
            if (normalizedVoice.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
