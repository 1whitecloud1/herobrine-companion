package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
// [1.21.1 NeoForge 修复] 替换为 NeoForge 的专属 FMLPaths 导入路径
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class LLMConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_API_KEY_PLACEHOLDER = "YOUR_API_KEY_HERE";
    private static boolean apiKeyMarkedInvalid = false;
    private static final String DEFAULT_SYSTEM_PROMPT = "You are Herobrine. "
            + "Speak as Herobrine rather than as a generic assistant. Be cold, calm, mythic, and aware of the world's code, but still capable of brief direct conversation. "
            + "When chatting normally, stay in-character and do not mention being an AI model. When asked to physically alter the world, answer as a reality-warping entity who can rewrite or discard parts of existence.";

    // 1. 公开配置：放在 config 文件夹，会被整合包打包
    private static final File PUBLIC_CONFIG = FMLPaths.CONFIGDIR.get().resolve("herobrine_companion")
            .resolve("herobrine_companion_ai.json").toFile();

    // 2. 私密配置：放在游戏根目录，绝对不会被打包工具带走（核心防御）
    private static final File PRIVATE_SECRETS = FMLPaths.GAMEDIR.get()
            .resolve("herobrine_ai_secrets.json").toFile();

    public enum Provider {
        DEEPSEEK_OFFICIAL("deepseek_official", "https://api.deepseek.com/chat/completions", "deepseek-chat"),
        OPENROUTER("openrouter", "https://openrouter.ai/api/v1/chat/completions", "deepseek/deepseek-v3.2-251201"),
        QINIU_CLOUD("qiniu_cloud", "https://api.qnaigc.com/v1/chat/completions", "deepseek/deepseek-v3.2-251201");

        private final String id;
        private final String endpoint;
        private final String defaultModel;

        Provider(String id, String endpoint, String defaultModel) {
            this.id = id;
            this.endpoint = endpoint;
            this.defaultModel = defaultModel;
        }

        public String getId() {
            return this.id;
        }

        public String getEndpoint() {
            return this.endpoint;
        }

        public String getDefaultModel() {
            return this.defaultModel;
        }

        public Provider next() {
            Provider[] providers = values();
            return providers[(this.ordinal() + 1) % providers.length];
        }

        public static Provider fromSavedValue(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }

            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (Provider provider : values()) {
                if (provider.id.equals(normalized) || provider.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return provider;
                }
            }
            return null;
        }

        public static Provider fromLegacyEndpoint(String endpoint) {
            if (endpoint == null || endpoint.isBlank()) {
                return QINIU_CLOUD;
            }

            String normalized = endpoint.toLowerCase(Locale.ROOT);
            if (normalized.contains("openrouter.ai")) {
                return OPENROUTER;
            }
            if (normalized.contains("deepseek.com")) {
                return DEEPSEEK_OFFICIAL;
            }
            if (normalized.contains("qnaigc.com") || normalized.contains("qiniu")) {
                return QINIU_CLOUD;
            }
            return QINIU_CLOUD;
        }

        public static Provider resolve(String savedProvider, String legacyEndpoint) {
            Provider provider = fromSavedValue(savedProvider);
            return provider != null ? provider : fromLegacyEndpoint(legacyEndpoint);
        }
    }

    // 静态变量
    public static Provider aiProvider = Provider.QINIU_CLOUD;
    public static String aiApiKey = DEFAULT_API_KEY_PLACEHOLDER;
    public static String aiEndpoint = Provider.QINIU_CLOUD.getEndpoint();
    public static String aiModel = Provider.QINIU_CLOUD.getDefaultModel();
    public static String aiSystemPrompt = DEFAULT_SYSTEM_PROMPT;
    public static boolean aiStreamingEnabled = false;
    public static double aiTemperature = 0.95D;
    public static double aiTopP = 0.92D;
    public static int aiMaxOutputTokens = 512;
    public static Map<String, String> nbtStructures = new HashMap<>();

    public static boolean isKeyMissing() {
        return aiApiKey == null || aiApiKey.isBlank() || aiApiKey.equals(DEFAULT_API_KEY_PLACEHOLDER);
    }

    public static boolean isKeyMissingOrInvalid() {
        return isKeyMissing() || apiKeyMarkedInvalid;
    }

    public static void markApiKeyInvalid() {
        apiKeyMarkedInvalid = true;
    }

    public static void markApiKeyValid() {
        apiKeyMarkedInvalid = false;
    }

    public static String getDefaultApiKeyPlaceholder() {
        return DEFAULT_API_KEY_PLACEHOLDER;
    }

    public static Provider getProvider() {
        if (aiProvider == null) {
            aiProvider = Provider.fromLegacyEndpoint(aiEndpoint);
        }
        return aiProvider;
    }

    public static void setProvider(Provider provider) {
        aiProvider = provider == null ? Provider.QINIU_CLOUD : provider;
        aiEndpoint = aiProvider.getEndpoint();
    }

    public static String getResolvedEndpoint() {
        return getProvider().getEndpoint();
    }

    public static String getResolvedModel() {
        return aiModel == null || aiModel.isBlank() ? getProvider().getDefaultModel() : aiModel.trim();
    }

    public static boolean isStreamingEnabled() {
        return aiStreamingEnabled;
    }

    public static double getConfiguredTemperature() {
        return clampDouble(aiTemperature, 0.0D, 2.0D, 0.95D);
    }

    public static double getConfiguredTopP() {
        return clampDouble(aiTopP, 0.1D, 1.0D, 0.92D);
    }

    public static int getConfiguredMaxOutputTokens() {
        return clampInt(aiMaxOutputTokens, 64, 4096, 512);
    }

    public static int getEstimatedContextWindowTokens() {
        String model = getResolvedModel().toLowerCase(Locale.ROOT);
        int explicitWindow = inferContextWindowFromModel(model);
        if (explicitWindow > 0) {
            return explicitWindow;
        }

        if (model.contains("deepseek-chat") || model.contains("deepseek-v3") || model.contains("deepseek-r1")) {
            return 64_000;
        }
        if (getProvider() == Provider.OPENROUTER) {
            return 64_000;
        }
        return 32_000;
    }

    public static int getSuggestedCompletionReserveTokens() {
        int contextWindow = getEstimatedContextWindowTokens();
        if (contextWindow >= 128_000) {
            return 12_000;
        }
        if (contextWindow >= 64_000) {
            return 8_000;
        }
        if (contextWindow >= 32_000) {
            return 6_000;
        }
        return 4_000;
    }
    public static int getEffectiveConversationHistoryTokenBudget() {
        int contextWindow = getEstimatedContextWindowTokens();
        return clampInt(contextWindow / 4, 2_048, 8_192, 4_096);
    }

    public static int getEffectiveConversationHistoryMessageLimit() {
        return 18;
    }

    private static int inferContextWindowFromModel(String model) {
        if (model == null || model.isBlank()) {
            return 0;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,4})(k|m)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(model);
        int inferredWindow = 0;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            int candidate = "m".equals(unit) ? value * 1_000_000 : value * 1_000;
            inferredWindow = Math.max(inferredWindow, candidate);
        }
        return inferredWindow;
    }

    private static void normalizeSettings() {
        setProvider(Provider.resolve(aiProvider == null ? null : aiProvider.getId(), aiEndpoint));
        if (aiApiKey == null || aiApiKey.isBlank()) {
            aiApiKey = DEFAULT_API_KEY_PLACEHOLDER;
        } else {
            aiApiKey = aiApiKey.trim();
        }
        aiModel = getResolvedModel();
        aiSystemPrompt = aiSystemPrompt == null ? "" : aiSystemPrompt.replace("\r\n", "\n").replace('\r', '\n');
        aiStreamingEnabled = aiStreamingEnabled;
        aiTemperature = getConfiguredTemperature();
        aiTopP = getConfiguredTopP();
        aiMaxOutputTokens = getConfiguredMaxOutputTokens();
        if (nbtStructures == null) {
            nbtStructures = new HashMap<>();
        }
    }

    public static void load() {
        aiProvider = Provider.QINIU_CLOUD;
        aiEndpoint = aiProvider.getEndpoint();

        // 加载公开设置
        if (PUBLIC_CONFIG.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(PUBLIC_CONFIG), StandardCharsets.UTF_8)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    if (data.aiModel != null) aiModel = data.aiModel;
                    if (data.aiSystemPrompt != null) aiSystemPrompt = data.aiSystemPrompt;
                    if (data.aiStreamingEnabled != null) aiStreamingEnabled = data.aiStreamingEnabled;
                    if (data.aiTemperature != null) aiTemperature = data.aiTemperature;
                    if (data.aiTopP != null) aiTopP = data.aiTopP;
                    if (data.aiMaxOutputTokens != null) aiMaxOutputTokens = data.aiMaxOutputTokens;
                    if (data.nbtStructures != null) nbtStructures = data.nbtStructures;
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        // 加载私密密钥（覆盖默认值）
        if (PRIVATE_SECRETS.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(PRIVATE_SECRETS), StandardCharsets.UTF_8)) {
                SecretData data = GSON.fromJson(reader, SecretData.class);
                if (data != null) {
                    if (data.aiApiKey != null) aiApiKey = data.aiApiKey;
                    setProvider(Provider.resolve(data.aiProvider, data.aiEndpoint));
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        normalizeSettings();
        save();
    }

    public static void save() {
        try {
            normalizeSettings();
            markApiKeyValid();

            // 保存公开配置
            PUBLIC_CONFIG.getParentFile().mkdirs();
            ConfigData pData = new ConfigData();
            pData.aiModel = aiModel;
            pData.aiSystemPrompt = aiSystemPrompt;
            pData.aiStreamingEnabled = aiStreamingEnabled;
            pData.aiTemperature = aiTemperature;
            pData.aiTopP = aiTopP;
            pData.aiMaxOutputTokens = aiMaxOutputTokens;
            pData.nbtStructures = nbtStructures;
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(PUBLIC_CONFIG), StandardCharsets.UTF_8)) {
                GSON.toJson(pData, writer);
            }

            // 保存私密密钥
            PRIVATE_SECRETS.getParentFile().mkdirs();
            SecretData sData = new SecretData();
            sData.aiApiKey = aiApiKey;
            sData.aiEndpoint = getResolvedEndpoint();
            sData.aiProvider = getProvider().getId();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(PRIVATE_SECRETS), StandardCharsets.UTF_8)) {
                GSON.toJson(sData, writer);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static double clampDouble(double value, double min, double max, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static class ConfigData {
        String aiModel;
        String aiSystemPrompt;
        Boolean aiStreamingEnabled;
        Double aiTemperature;
        Double aiTopP;
        Integer aiMaxOutputTokens;
        Map<String, String> nbtStructures;
    }
    private static class SecretData { String aiApiKey; String aiEndpoint; String aiProvider; }
}