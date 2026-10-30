package com.whitecloud233.modid.herobrine_companion.client.tts;

/**
 * 一条可供选择的 TTS 音色（系统语音与云端音色共用）。
 *
 * @param name     引擎侧的真实音色标识（Windows 为 SAPI 音色全名，如 {@code Microsoft Huihui Desktop}）
 * @param label    界面展示名（已本地化友好，通常是去掉 {@code Microsoft ... Desktop} 外壳后的短名）
 * @param gender   性别推断结果，仅用于界面标注与筛选，不影响合成
 */
public record VoiceOption(String name, String label, VoiceGender gender) {

    /** 「自动」占位项：音色名为空，表示交给引擎自己挑默认音色。 */
    public static final VoiceOption AUTO = new VoiceOption("", "", VoiceGender.UNKNOWN);

    public VoiceOption {
        name = name == null ? "" : name.trim();
        label = label == null || label.isBlank() ? name : label.trim();
        gender = gender == null ? VoiceGender.UNKNOWN : gender;
    }

    /** 是否可作为"自动"之外的显式选择。 */
    public boolean isSelectable() {
        return !this.name.isBlank();
    }

    /** 界面按钮上的文字：短名 + 性别标记。 */
    public String displayText() {
        return this.gender.marker().isEmpty() ? this.label : this.label + " " + this.gender.marker();
    }
}
