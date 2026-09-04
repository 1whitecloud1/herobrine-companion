package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonObject;
import com.whitecloud233.herobrine_companion.client.llm.LlmToolSpec;

/**
 * 安全版惰性 stub（普通构建默认即安全版，编译进安全版 jar）。
 *
 * <p>与 {@code SafeComputerControlService} 配套：工具识别恒为 false、
 * schema 恒不可用，LLM 在安全版中永远不会看到 computer_control_skill 工具。</p>
 */
final class AIComputerControlSupport {
    static final String TOOL_COMPUTER_CONTROL = "computer_control_skill";

    private AIComputerControlSupport() {}

    static boolean isSupportedToolName(String toolName) {
        return false;
    }

    static LlmToolSpec toolSpec() {
        return new LlmToolSpec(TOOL_COMPUTER_CONTROL,
                "Unavailable in this build; local computer control is not included.",
                new JsonObject());
    }

    static ParseResult parseAction(JsonObject args) {
        return ParseResult.error("Local computer control is not available in this build.");
    }

    static boolean isLikelyComputerControlRequest(String text) {
        return false;
    }

    record ComputerAction(Action action, String name, String content) {}

    enum Action {}

    record ParseResult(ComputerAction action, String error) {
        static ParseResult success(ComputerAction action) {
            return new ParseResult(action, "");
        }

        static ParseResult error(String error) {
            return new ParseResult(null, error);
        }

        boolean isValid() {
            return this.action != null;
        }
    }
}
