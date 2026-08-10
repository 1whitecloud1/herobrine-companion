package com.whitecloud233.modid.herobrine_companion.client.jvm;

/**
 * jvm_code_skill 执行的产出值类型，由编排层返回给 AIService。
 */
public record JvmCodeResult(JvmCodeStatus status, String detail) {
    public JvmCodeResult {
        if (detail == null) {
            detail = "";
        }
    }

    public static JvmCodeResult of(JvmCodeStatus status, String detail) {
        return new JvmCodeResult(status, detail);
    }
}
