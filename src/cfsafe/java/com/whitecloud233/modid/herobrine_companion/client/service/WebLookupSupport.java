package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.JsonObject;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmToolSpec;

import java.util.concurrent.CompletableFuture;

/**
 * 安全版惰性 stub（仅安全构建编译）。
 *
 * <p>联网查找（web_lookup）在安全版构建中被排除（与电脑 CMD、JVM 代码注入同组），
 * 本类与完整版同 FQCN、同包可见性，保证 {@code AIService} / {@code AIPromptAssembler}
 * 等引用方可编译，工具识别恒为 false，LLM 永远不会看到该工具。
 * 不包含任何 HTTP 请求 / 网络访问。</p>
 */
final class WebLookupSupport {
    static final String TOOL_WEB_LOOKUP = "web_lookup";

    private WebLookupSupport() {
    }

    static boolean isSupportedToolName(String toolName) {
        return false;
    }

    static LlmToolSpec toolSpec() {
        return new LlmToolSpec(TOOL_WEB_LOOKUP,
                "Unavailable in this build; web lookup is not included.",
                new JsonObject());
    }

    static ParseResult parseAction(JsonObject args) {
        return ParseResult.error("Web lookup is not available in this build.");
    }

    static CompletableFuture<String> execute(WebLookupAction request) {
        return CompletableFuture.completedFuture("(Web lookup is not available in this build.)");
    }

    record WebLookupAction(String action, String site, String query, String url) {
    }

    record ParseResult(WebLookupAction request, String error) {
        static ParseResult error(String error) {
            return new ParseResult(null, error);
        }

        boolean isValid() {
            return this.request != null;
        }
    }
}