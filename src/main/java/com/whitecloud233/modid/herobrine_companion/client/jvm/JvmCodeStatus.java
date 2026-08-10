package com.whitecloud233.modid.herobrine_companion.client.jvm;

/**
 * jvm_code_skill 执行的产出值类型，由编排层返回给 AIService。
 */
public enum JvmCodeStatus {
    SUCCESS,
    CANCELLED,
    FAILED,
    DISABLED,
    UNSUPPORTED,
    BUSY
}
