package com.whitecloud233.modid.herobrine_companion.client.llm;

/**
 * 流式响应产出（从原 AIStreamingSupport.StreamingResponse 迁来）。
 */
public class LlmStreamingResponse {
    public final int statusCode;
    public final String errorBody;
    public final String reply;
    public final String reasoning;
    public final String toolName;
    public final String toolArguments;

    private LlmStreamingResponse(int statusCode, String errorBody, String reply, String reasoning,
                                 String toolName, String toolArguments) {
        this.statusCode = statusCode;
        this.errorBody = errorBody;
        this.reply = reply;
        this.reasoning = reasoning;
        this.toolName = toolName;
        this.toolArguments = toolArguments;
    }

    public static LlmStreamingResponse success(String reply, String toolName, String toolArguments) {
        return new LlmStreamingResponse(200, null, reply, null, toolName, toolArguments);
    }

    /** 含思考过程（reasoning_content / thinking 增量）的流式成功响应。 */
    public static LlmStreamingResponse success(String reply, String reasoning, String toolName, String toolArguments) {
        return new LlmStreamingResponse(200, null, reply, reasoning, toolName, toolArguments);
    }

    public static LlmStreamingResponse error(int statusCode, String errorBody) {
        return new LlmStreamingResponse(statusCode, errorBody, null, null, null, null);
    }
}
