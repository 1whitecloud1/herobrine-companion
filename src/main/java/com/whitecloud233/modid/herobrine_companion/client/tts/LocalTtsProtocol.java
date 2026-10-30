package com.whitecloud233.modid.herobrine_companion.client.tts;

import java.util.Locale;

/**
 * 本地部署 TTS 服务的接口形态。
 *
 * <p>本地项目各家的 HTTP 约定差别很大，模组不做"猜"：由玩家显式选一种，
 * 选错只会在日志里报出真实响应，不会误改任何配置。</p>
 */
public enum LocalTtsProtocol {
    /** OpenAI 兼容：{@code POST {endpoint}/v1/audio/speech}，最常见（openedai-speech、各类兼容网关）。 */
    OPENAI("openai"),
    /** GPT-SoVITS api_v2：{@code GET {endpoint}/tts?text=&text_lang=&ref_audio_path=&prompt_text=&prompt_lang=}。 */
    GPT_SOVITS("gpt_sovits"),
    /** 自定义：{@code POST {endpoint}}，请求体由玩家模板决定（支持 ${text}/${voice}/${model}/${speed}）。 */
    CUSTOM("custom");

    private final String id;

    LocalTtsProtocol(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public String labelKey() {
        return "gui.herobrine_companion.tts.local.protocol." + this.id;
    }

    public String hintKey() {
        return "gui.herobrine_companion.tts.local.protocol_hint." + this.id;
    }

    public LocalTtsProtocol next() {
        LocalTtsProtocol[] protocols = values();
        return protocols[(this.ordinal() + 1) % protocols.length];
    }

    public static LocalTtsProtocol fromSaved(String value) {
        if (value == null || value.isBlank()) {
            return OPENAI;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (LocalTtsProtocol protocol : values()) {
            if (protocol.id.equals(normalized) || protocol.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return protocol;
            }
        }
        return OPENAI;
    }
}
