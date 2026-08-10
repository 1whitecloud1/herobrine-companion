package com.whitecloud233.modid.herobrine_companion.client.llm;

import com.google.gson.JsonObject;
import org.slf4j.Logger;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.function.Consumer;

/**
 * 单种 LLM 线上格式的适配器（DIP 接缝）。
 * 每种格式的请求构建、鉴权、响应解析、流式读取、工具包装全部收敛在实现内；
 * 编排层（AIService / ConversationSummaryService）只依赖本接口。
 */
public interface LlmFormatAdapter {

    /** 构建一次聊天请求（含 URL、鉴权头、请求体）。 */
    HttpRequest buildChatRequest(LlmSettings settings, LlmChatPayload payload);

    /** 构建一次无历史、无工具的简单请求（本地化 / actor 台词 / 摘要）。 */
    HttpRequest buildSimpleRequest(LlmSettings settings, String systemPrompt, String userPrompt,
                                   double temperature, double topP, int maxTokens);

    /** 从非流式响应提取文本；失败返回 fallback。 */
    String extractText(JsonObject json, String fallback);

    /** 从非流式响应提取"思考过程"（reasoning/thinking）；该格式无此字段时返回空串。 */
    default String extractReasoning(JsonObject json) {
        return "";
    }

    /** 从非流式响应提取工具调用；无则返回 null。 */
    LlmToolInvocation extractToolInvocation(JsonObject json);

    /** 读取流式响应（阻塞直到流结束，返回产出）。 */
    LlmStreamingResponse readStreaming(HttpClient client, HttpRequest request,
                                       Consumer<String> partialConsumer, Logger logger);

    /** 把中性工具描述包装成该格式的工具 JSON。 */
    JsonObject wrapTool(LlmToolSpec spec);
}
