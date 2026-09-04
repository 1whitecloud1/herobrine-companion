package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.JsonObject;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmToolSpec;

/**
 * 安全版惰性 stub（普通构建默认即安全版，编译进安全版 jar）。
 *
 * <p>jvm_code_skill（JVM 代码注入）在安全版构建中被排除，本类保证
 * {@code AIService} / {@code AIPromptAssembler} / {@code AIActionIntentInference}
 * 等引用方可编译，工具识别恒为 false，LLM 永远不会看到该工具。</p>
 */
final class AIJvmCodeSkillSupport {
    static final String TOOL_JVM_CODE = "jvm_code_skill";

    private AIJvmCodeSkillSupport() {}

    static boolean isSupportedToolName(String toolName) {
        return false;
    }

    static LlmToolSpec toolSpec() {
        return new LlmToolSpec(TOOL_JVM_CODE,
                "Unavailable in this build; JVM code execution is not included.",
                new JsonObject());
    }

    static ParseResult parseCode(JsonObject args) {
        return ParseResult.error("JVM code execution is not available in this build.");
    }

    static boolean isLikelyJvmCodeSkillRequest(String text) {
        return false;
    }

    record ParseResult(String code, String error) {
        static ParseResult success(String code) {
            return new ParseResult(code, "");
        }

        static ParseResult error(String error) {
            return new ParseResult(null, error);
        }

        boolean isValid() {
            return this.code != null;
        }
    }
}
