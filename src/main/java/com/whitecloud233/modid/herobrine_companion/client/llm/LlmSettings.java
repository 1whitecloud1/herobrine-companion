package com.whitecloud233.modid.herobrine_companion.client.llm;

import com.whitecloud233.modid.herobrine_companion.client.service.LLMConfig;

import java.net.URI;

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
        String normalizedEndpoint = LLMConfig.normalizeChatEndpoint(endpoint);
        LLMConfig.EndpointFormat format = LLMConfig.detectEndpointFormat(normalizedEndpoint);
        return new LlmSettings(normalizedEndpoint, apiKey, model, provider, format);
    }

    /** 显式指定格式（如自定义 provider 手动选了供应商格式）；null 时按 endpoint 自动探测。 */
    public static LlmSettings of(String endpoint, String apiKey, String model, LLMConfig.Provider provider,
                                 LLMConfig.EndpointFormat explicitFormat) {
        String normalizedEndpoint = LLMConfig.normalizeChatEndpoint(endpoint);
        LLMConfig.EndpointFormat format = explicitFormat != null ? explicitFormat : LLMConfig.detectEndpointFormat(normalizedEndpoint);
        return new LlmSettings(normalizedEndpoint, apiKey, model, provider, format);
    }

    public boolean isUsable() {
        if (endpoint.isBlank() || apiKey.isBlank() || model.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String scheme = uri.getScheme();
            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        } catch (Exception e) {
            return false;
        }
    }
}
