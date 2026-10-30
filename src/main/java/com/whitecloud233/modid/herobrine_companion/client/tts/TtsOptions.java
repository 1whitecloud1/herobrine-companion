package com.whitecloud233.modid.herobrine_companion.client.tts;

import com.whitecloud233.modid.herobrine_companion.util.NumberInputs;

/**
 * 一次语音合成所需的全部参数快照（不可变）。
 *
 * <p>把"设置"与"执行"解耦：{@link TtsSettings} 负责读写配置，
 * 各 {@link SpeechOutput} 实现只认这份快照，不直接读全局静态字段。</p>
 *
 * <p>按引擎分组（{@link SystemVoice} / {@link CloudVoice} / {@link LocalVoice}），
 * 这样每加一种引擎只新增一个分组，公共字段与既有引擎互不干扰。</p>
 */
public record TtsOptions(
        TtsEngine engine,
        double volume,
        double speed,
        int maxChars,
        boolean interruptCurrent,
        SystemVoice system,
        CloudVoice cloud,
        LocalVoice local
) {

    public static final double MIN_VOLUME = 0.0D;
    public static final double MAX_VOLUME = 1.0D;
    public static final double MIN_SPEED = 0.5D;
    public static final double MAX_SPEED = 2.0D;
    public static final int MIN_MAX_CHARS = 20;
    public static final int MAX_MAX_CHARS = 600;
    public static final String DEFAULT_CLOUD_MODEL = "tts-1";
    public static final String DEFAULT_CLOUD_VOICE = "alloy";
    public static final String DEFAULT_LOCAL_MODEL = "tts-1";
    public static final String DEFAULT_LOCAL_VOICE = "alloy";
    public static final String DEFAULT_CUSTOM_BODY = "{\"text\": \"${text}\", \"voice\": \"${voice}\"}";

    public TtsOptions {
        engine = engine == null ? TtsEngine.SYSTEM : engine;
        volume = NumberInputs.finite(volume, MIN_VOLUME, MAX_VOLUME, 0.9D);
        speed = NumberInputs.finite(speed, MIN_SPEED, MAX_SPEED, 1.0D);
        maxChars = NumberInputs.positive(maxChars, MIN_MAX_CHARS, MAX_MAX_CHARS, 200);
        system = system == null ? SystemVoice.EMPTY : system;
        cloud = cloud == null ? CloudVoice.EMPTY : cloud;
        local = local == null ? LocalVoice.EMPTY : local;
    }

    /**
     * 当前引擎在配置上是否就绪（能否发起播报）。
     *
     * <p>系统语音只要求平台支持；云端与本地都要求端点填好，云端额外要求密钥。</p>
     */
    public boolean isEngineConfigured() {
        return switch (this.engine) {
            case SYSTEM -> true;
            case CLOUD -> this.cloud.isReady();
            case LOCAL -> this.local.isReady();
        };
    }

    // ---------------- 分组 ----------------

    /** 系统语音：只要音色名（空 = 系统默认）。 */
    public record SystemVoice(String voiceName) {
        static final SystemVoice EMPTY = new SystemVoice("");

        public SystemVoice {
            voiceName = trimmed(voiceName);
        }
    }

    /** 云端语音：端点 + 密钥 + 模型 + 音色。 */
    public record CloudVoice(String endpoint, String apiKey, String model, String voiceName) {
        static final CloudVoice EMPTY = new CloudVoice("", "", "", "");

        public CloudVoice {
            endpoint = trimmed(endpoint);
            apiKey = trimmed(apiKey);
            model = trimmed(model).isEmpty() ? DEFAULT_CLOUD_MODEL : trimmed(model);
            voiceName = trimmed(voiceName).isEmpty() ? DEFAULT_CLOUD_VOICE : trimmed(voiceName);
        }

        public boolean isReady() {
            return !this.endpoint.isEmpty() && !this.apiKey.isEmpty();
        }
    }

    /** 本地部署语音：端点 + 接口形态 + 模型 + 音色 + 参考音频/文本/语言 + 自定义请求体 + 可选密钥。 */
    public record LocalVoice(
            String endpoint,
            LocalTtsProtocol protocol,
            String model,
            String voiceName,
            String referenceAudio,
            String referenceText,
            String referenceLang,
            String customBody,
            String apiKey
    ) {
        static final LocalVoice EMPTY = new LocalVoice("", LocalTtsProtocol.OPENAI, "", "", "", "", "", "", "");

        public LocalVoice {
            endpoint = trimmed(endpoint);
            protocol = protocol == null ? LocalTtsProtocol.OPENAI : protocol;
            model = trimmed(model).isEmpty() ? DEFAULT_LOCAL_MODEL : trimmed(model);
            voiceName = trimmed(voiceName).isEmpty() ? DEFAULT_LOCAL_VOICE : trimmed(voiceName);
            referenceAudio = trimmed(referenceAudio);
            referenceText = trimmed(referenceText);
            referenceLang = trimmed(referenceLang).isEmpty() ? "zh" : trimmed(referenceLang);
            customBody = trimmed(customBody).isEmpty() ? DEFAULT_CUSTOM_BODY : trimmed(customBody);
            apiKey = trimmed(apiKey);
        }

        /** 本地服务通常不需要密钥：只要端点填了就算就绪。 */
        public boolean isReady() {
            return !this.endpoint.isEmpty();
        }
    }

    // ---------------- 工具 ----------------

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
