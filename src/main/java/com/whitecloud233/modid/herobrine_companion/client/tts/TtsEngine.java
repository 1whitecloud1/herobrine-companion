package com.whitecloud233.modid.herobrine_companion.client.tts;

import java.util.Locale;

/**
 * 语音合成方式（客户端本地特性，与服务器无关）。
 *
 * <p>持久化只看 {@link #id()}，不依赖 {@link #ordinal()}：以后增删枚举项也不会让老配置错位。</p>
 */
public enum TtsEngine {
    /** 操作系统自带语音（Windows SAPI / macOS say / Linux espeak）：离线、免密钥。 */
    SYSTEM("system"),
    /** 远程 OpenAI 兼容的 {@code /audio/speech} 端点：音色可控，需要端点与密钥。 */
    CLOUD("cloud"),
    /** 本地部署的 TTS 服务（本机/局域网）：免密钥优先，支持多种本地接口形态。 */
    LOCAL("local");

    private final String id;

    TtsEngine(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    /** 界面里的引擎名翻译键。 */
    public String labelKey() {
        return "gui.herobrine_companion.tts.engine." + this.id;
    }

    public TtsEngine next() {
        TtsEngine[] engines = values();
        return engines[(this.ordinal() + 1) % engines.length];
    }

    public static TtsEngine fromSaved(String value) {
        if (value == null || value.isBlank()) {
            return SYSTEM;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (TtsEngine engine : values()) {
            if (engine.id.equals(normalized) || engine.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return engine;
            }
        }
        return SYSTEM;
    }
}
