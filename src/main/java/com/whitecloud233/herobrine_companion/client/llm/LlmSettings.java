package com.whitecloud233.herobrine_companion.client.llm;

import com.whitecloud233.herobrine_companion.client.service.LLMConfig;

/**
 * 一次 LLM 调用的已解析设置（endpoint / key / model / provider / 格式）。
 * 由 {@code LLMConfig.resolveTaskSettings} 生成；格式由 endpoint 自动探测。
 */
public record LlmSettings(
        String endpoint,
        String apiKey,
        String model,
        LLMConfig.Provider provider,
        LLMConfig.EndpointFormat format) {

    public LlmSettings {
        if (endpoint == null) {
            endpoint = "";
        }
        if (apiKey == null) {
            apiKey = "";
        }
        if (model == null) {
            model = "";
        }
    }

    public static LlmSettings of(String endpoint, String apiKey, String model, LLMConfig.Provider provider) {
        LLMConfig.EndpointFormat format = LLMConfig.detectEndpointFormat(endpoint);
        return new LlmSettings(endpoint, apiKey, model, provider, format);
    }

    /** 显式指定格式（如自定义 provider 手动选了供应商格式）；null 时按 endpoint 自动探测。 */
    public static LlmSettings of(String endpoint, String apiKey, String model, LLMConfig.Provider provider,
                                 LLMConfig.EndpointFormat explicitFormat) {
        LLMConfig.EndpointFormat format = explicitFormat != null ? explicitFormat : LLMConfig.detectEndpointFormat(endpoint);
        return new LlmSettings(endpoint, apiKey, model, provider, format);
    }

    public boolean isUsable() {
        return !endpoint.isBlank() && !apiKey.isBlank() && !model.isBlank();
    }
}
