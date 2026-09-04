package com.whitecloud233.herobrine_companion.client.llm;

/**
 * 流式响应产出（从原 AIStreamingSupport.StreamingResponse 迁来）。
 * finishReason 可为 null（正常结束或无该信息）；OpenAI 系为 "stop"/"length"，
 * Anthropic 系为 "end_turn"/"max_tokens"。
 */
public class LlmStreamingResponse {
    public final int statusCode;
    public final String errorBody;
    public final String reply;
    public final String reasoning;
    public final String toolName;
    public final String toolArguments;
    public final String finishReason;

    private LlmStreamingResponse(int statusCode, String errorBody, String reply, String reasoning,
                                 String toolName, String toolArguments, String finishReason) {
        this.statusCode = statusCode;
        this.errorBody = errorBody;
        this.reply = reply;
        this.reasoning = reasoning;
        this.toolName = toolName;
        this.toolArguments = toolArguments;
        this.finishReason = finishReason;
    }

    public static LlmStreamingResponse success(String reply, String toolName, String toolArguments) {
        return new LlmStreamingResponse(200, null, reply, null, toolName, toolArguments, null);
    }

    /** 含思考过程（reasoning_content / thinking 增量）的流式成功响应。 */
    public static LlmStreamingResponse success(String reply, String reasoning, String toolName, String toolArguments) {
        return new LlmStreamingResponse(200, null, reply, reasoning, toolName, toolArguments, null);
    }

    /** 含思考过程与结束原因（截断检测用）的流式成功响应。 */
    public static LlmStreamingResponse success(String reply, String reasoning, String toolName,
                                               String toolArguments, String finishReason) {
        return new LlmStreamingResponse(200, null, reply, reasoning, toolName, toolArguments, finishReason);
    }

    public static LlmStreamingResponse error(int statusCode, String errorBody) {
        return new LlmStreamingResponse(statusCode, errorBody, null, null, null, null, null);
    }
}
