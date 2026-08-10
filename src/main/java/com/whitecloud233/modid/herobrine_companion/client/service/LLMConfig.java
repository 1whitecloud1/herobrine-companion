package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmSettings;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmTask;
import com.whitecloud233.modid.herobrine_companion.client.llm.ResolvedTask;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class LLMConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_API_KEY_PLACEHOLDER = "YOUR_API_KEY_HERE";
    private static final String DEFAULT_SYSTEM_PROMPT = "You are Herobrine. "
            + "Speak as Herobrine rather than as a generic assistant. Be cold, calm, mythic, and aware of the world's code, but still capable of brief direct conversation. "
            + "When chatting normally, stay in-character and do not mention being an AI model. When asked to physically alter the world, answer as a reality-warping entity who can rewrite or discard parts of existence.";
    private static final double DEFAULT_TEMPERATURE = 0.95D;
    private static final double DEFAULT_TOP_P = 0.92D;
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 512;
    private static boolean apiKeyMarkedInvalid = false;
    private static boolean loaded = false;
    private static boolean loading = false;
    private static boolean providerProfilesLoaded = false;

    // 1. 公开配置：放在 config 文件夹，会被整合包打包
    private static final File PUBLIC_CONFIG = FMLPaths.CONFIGDIR.get().resolve("herobrine_companion")
            .resolve("herobrine_companion_ai.json").toFile();

    // 2. 私密配置：放在游戏根目录，绝对不会被打包工具带走（核心防御）
    private static final File PRIVATE_SECRETS = FMLPaths.GAMEDIR.get()
            .resolve("herobrine_ai_secrets.json").toFile();

    public enum Provider {
        DEEPSEEK_OFFICIAL("deepseek_official", "https://api.deepseek.com/v1/chat/completions", "deepseek-chat"),
        OPENROUTER("openrouter", "https://openrouter.ai/api/v1/chat/completions", "deepseek/deepseek-v3.2-251201"),
        QINIU_CLOUD("qiniu_cloud", "https://api.qnaigc.com/v1/chat/completions", "deepseek/deepseek-v3.2-251201"),
        QINIU_CLOUD_ANTHROPIC("qiniu_cloud_anthropic", "https://anthropic.qnaigc.com/v1/messages", "claude-3-5-sonnet-20241022"),
        GEMINI("gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash"),
        CUSTOM("custom", "", "");

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
            if (normalized.contains("anthropic.qnaigc.com")) {
                return QINIU_CLOUD_ANTHROPIC;
            }
            if (normalized.contains("qnaigc.com") || normalized.contains("qiniu")) {
                return QINIU_CLOUD;
            }
            return CUSTOM;
        }

        public static Provider resolve(String savedProvider, String legacyEndpoint) {
            Provider provider = fromSavedValue(savedProvider);
            if (provider != null) {
                return provider;
            }
            if (savedProvider != null && !savedProvider.isBlank()) {
                return CUSTOM;
            }
            return fromLegacyEndpoint(legacyEndpoint);
        }
    }

    public enum EndpointFormat {
        OPENAI_CHAT,
        OPENAI_RESPONSES,
        ANTHROPIC,
        GEMINI
    }

    public enum CommandMode {
        NORMAL("normal"),
        EAGER("eager");

        private final String id;

        CommandMode(String id) {
            this.id = id;
        }

        public String getId() {
            return this.id;
        }

        public CommandMode next() {
            CommandMode[] modes = values();
            return modes[(this.ordinal() + 1) % modes.length];
        }

        public static CommandMode fromSavedValue(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (CommandMode mode : values()) {
                if (mode.id.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return mode;
                }
            }
            return null;
        }
    }

    // 静态变量
    public static Provider aiProvider = Provider.QINIU_CLOUD;
    private static String aiProviderId = Provider.QINIU_CLOUD.getId();
    public static String aiApiKey = DEFAULT_API_KEY_PLACEHOLDER;
    public static String aiEndpoint = Provider.QINIU_CLOUD.getEndpoint();
    public static String aiModel = Provider.QINIU_CLOUD.getDefaultModel();
    public static String aiModelName = Provider.QINIU_CLOUD.getDefaultModel();
    public static String aiSystemPrompt = DEFAULT_SYSTEM_PROMPT;
    public static boolean aiStreamingEnabled = false;
    public static boolean aiConversationSummaryEnabled = true;
    public static boolean aiWebLookupEnabled = false;
    public static volatile boolean aiComputerControlEnabled = false;
    public static volatile boolean aiJvmCodeSkillEnabled = false;
    /** JVM 优先模式：普通聊天更倾向用 jvm_code_skill 改世界，同时关闭 Minecraft 指令工具。 */
    public static volatile boolean aiJvmPreferredEnabled = false;
    public static CommandMode aiCommandMode = CommandMode.NORMAL;
    /** 自定义 provider 的显式 API 格式（空 = 按 endpoint 自动探测）。 */
    public static String customApiFormat = "";
    public static double aiTemperature = DEFAULT_TEMPERATURE;
    public static double aiTopP = DEFAULT_TOP_P;
    public static int aiMaxOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS;
    public static Map<String, String> nbtStructures = new HashMap<>();
    private static final Map<LlmTask, TaskRoute> taskRoutes = new EnumMap<>(LlmTask.class);
    private static final Map<String, ProviderProfile> providerProfiles = new LinkedHashMap<>();

    public record ProviderSettings(String providerId, String apiKey, String endpoint, String modelId, String modelName) {
        public String modelForRequest() {
            String normalizedModelId = modelId == null ? "" : modelId.trim();
            if (!normalizedModelId.isEmpty()) {
                return normalizedModelId;
            }
            return modelName == null ? "" : modelName.trim();
        }
    }

    /** 任务路由：该任务的 provider（空=跟随全局激活）与其备用 provider（空=无备用）。 */
    public record TaskRoute(String providerId, String fallbackProviderId) {
        public TaskRoute {
            if (providerId == null) {
                providerId = "";
            }
            if (fallbackProviderId == null) {
                fallbackProviderId = "";
            }
        }
    }

    private static class ProviderProfile {
        String providerId;
        String apiKey;
        String endpoint;
        String modelId;
        String modelName;
    }

    public static boolean isKeyMissing() {
        ensureLoaded();
        return aiApiKey == null || aiApiKey.isBlank() || aiApiKey.equals(DEFAULT_API_KEY_PLACEHOLDER);
    }

    public static boolean isModelMissing() {
        return getResolvedModel().isBlank();
    }

    public static boolean isEndpointMissing() {
        return getResolvedEndpoint().isBlank();
    }

    public static boolean isSetupIncomplete() {
        return isKeyMissing() || isModelMissing() || isEndpointMissing();
    }

    public static boolean isKeyMissingOrInvalid() {
        return isKeyMissing() || apiKeyMarkedInvalid;
    }

    public static boolean isSetupIncompleteOrInvalid() {
        return isSetupIncomplete() || apiKeyMarkedInvalid;
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

    public static synchronized void ensureLoaded() {
        if (!loaded && !loading) {
            load();
        }
    }

    public static String getSystemPrompt() {
        ensureLoaded();
        return aiSystemPrompt == null ? "" : aiSystemPrompt;
    }

    public static Provider getProvider() {
        ensureLoaded();
        return getProviderUnchecked();
    }

    private static Provider getProviderUnchecked() {
        if (aiProvider == null) {
            aiProvider = Provider.resolve(aiProviderId, aiEndpoint);
        }
        if (aiProvider == null) {
            aiProvider = Provider.QINIU_CLOUD;
        }
        return aiProvider;
    }

    public static void setProvider(Provider provider) {
        setProvider(provider, false);
    }

    public static void setProvider(Provider provider, boolean preserveCustomEndpoint) {
        if (!loading) {
            ensureLoaded();
        }
        saveCurrentFieldsToActiveProfile();
        aiProvider = provider == null ? Provider.QINIU_CLOUD : provider;
        if (preserveCustomEndpoint && aiEndpoint != null && !aiEndpoint.isBlank()) {
            getProfile(aiProvider).endpoint = normalizeEndpoint(aiEndpoint);
        }
        applyActiveProfileToFields();
        apiKeyMarkedInvalid = false;
    }

    public static String getStoredProviderId() {
        ensureLoaded();
        return getStoredProviderIdUnchecked();
    }

    private static String getStoredProviderIdUnchecked() {
        return getProviderSettingsUnchecked(getProviderUnchecked()).providerId();
    }

    public static void setCustomProviderId(String providerId) {
        if (!loading) {
            ensureLoaded();
        }
        aiProvider = Provider.CUSTOM;
        aiProviderId = normalizeCustomProviderId(providerId);
        getProfile(Provider.CUSTOM).providerId = aiProviderId;
    }

    public static String getResolvedEndpoint() {
        ensureLoaded();
        return getResolvedEndpointUnchecked();
    }

    private static String getResolvedEndpointUnchecked() {
        return getProviderSettingsUnchecked(getProviderUnchecked()).endpoint();
    }

    public static EndpointFormat getResolvedEndpointFormat() {
        ensureLoaded();
        return detectEndpointFormat(getResolvedEndpointUnchecked());
    }

    public static String getResolvedModel() {
        ensureLoaded();
        return getResolvedModelUnchecked();
    }

    private static String getResolvedModelUnchecked() {
        return getProviderSettingsUnchecked(getProviderUnchecked()).modelForRequest();
    }

    public static String getResolvedModelName() {
        ensureLoaded();
        return getProviderSettingsUnchecked(getProviderUnchecked()).modelName();
    }

    public static ProviderSettings getProviderSettings(Provider provider) {
        ensureLoaded();
        return getProviderSettingsUnchecked(provider == null ? Provider.QINIU_CLOUD : provider);
    }

    /**
     * 解析全局激活 provider 的一组 LLM 调用设置（Phase 2 之前 AIService 的默认入口；
     * Phase 2 起按任务路由解析，此方法保留为"跟随全局"的语义）。
     */
    public static LlmSettings resolveGlobalSettings() {
        ensureLoaded();
        Provider provider = getProviderUnchecked();
        ProviderSettings settings = getProviderSettingsUnchecked(provider);
        return buildLlmSettings(settings, provider);
    }

    /** 自定义 provider 的显式格式；空 = 按 endpoint 自动探测。 */
    public static EndpointFormat getCustomApiFormat() {
        ensureLoaded();
        if (customApiFormat == null || customApiFormat.isBlank()) {
            return null;
        }
        try {
            return EndpointFormat.valueOf(customApiFormat.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LlmSettings buildLlmSettings(ProviderSettings settings, Provider provider) {
        EndpointFormat explicitFormat = provider == Provider.CUSTOM ? getCustomApiFormat() : null;
        return LlmSettings.of(settings.endpoint(), settings.apiKey(), settings.modelForRequest(), provider, explicitFormat);
    }

    /** 读取某任务的路由（未配置返回 null）。 */
    public static TaskRoute getTaskRoute(LlmTask task) {
        ensureLoaded();
        return task == null ? null : taskRoutes.get(task);
    }

    /** 写入某任务的路由；route 为 null 时清除该任务路由。 */
    public static void setTaskRoute(LlmTask task, TaskRoute route) {
        ensureLoaded();
        if (task == null) {
            return;
        }
        if (route == null || (route.providerId().isBlank() && route.fallbackProviderId().isBlank())) {
            taskRoutes.remove(task);
        } else {
            taskRoutes.put(task, route);
        }
    }

    /**
     * 解析某任务的调用设置：主 provider（未路由则跟随全局激活）+ 备用 provider（未配置为 null）。
     */
    public static ResolvedTask resolveTaskSettings(LlmTask task) {
        ensureLoaded();
        TaskRoute route = task == null ? null : taskRoutes.get(task);
        String primaryId = route == null ? null : route.providerId();
        String fallbackId = route == null ? null : route.fallbackProviderId();

        LlmSettings primary = resolveRoutedProviderSettings(primaryId);
        if (primary == null) {
            Provider active = getProviderUnchecked();
            ProviderSettings activeSettings = getProviderSettingsUnchecked(active);
            primary = buildLlmSettings(activeSettings, active);
        }
        LlmSettings fallback = resolveRoutedProviderSettings(fallbackId);
        return new ResolvedTask(primary, fallback);
    }

    private static LlmSettings resolveRoutedProviderSettings(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return null;
        }
        Provider provider = Provider.fromSavedValue(providerId);
        if (provider == null) {
            return null;
        }
        ProviderSettings settings = getProviderSettingsUnchecked(provider);
        return buildLlmSettings(settings, provider);
    }

    private static ProviderSettings getProviderSettingsUnchecked(Provider provider) {
        Provider normalizedProvider = provider == null ? Provider.QINIU_CLOUD : provider;
        ProviderProfile profile = getProfile(normalizedProvider);
        normalizeProfile(normalizedProvider, profile);
        return new ProviderSettings(
                profile.providerId,
                profile.apiKey,
                profile.endpoint,
                profile.modelId,
                profile.modelName
        );
    }

    public static void saveProviderSettings(Provider provider, String providerId, String apiKey, String endpoint,
                                            String modelId, String modelName) {
        ensureLoaded();
        Provider normalizedProvider = provider == null ? Provider.QINIU_CLOUD : provider;
        ProviderProfile profile = getProfile(normalizedProvider);
        profile.providerId = normalizedProvider == Provider.CUSTOM
                ? normalizeCustomProviderId(providerId)
                : normalizedProvider.getId();
        profile.apiKey = normalizeApiKey(apiKey);
        profile.endpoint = normalizeEndpoint(endpoint);
        profile.modelId = normalizeModelForProvider(normalizedProvider, modelId);
        profile.modelName = normalizeModelName(modelName, profile.modelId);

        aiProvider = normalizedProvider;
        applyActiveProfileToFields();
        save();
    }
    public static boolean isStreamingEnabled() {
        ensureLoaded();
        return aiStreamingEnabled;
    }

    public static boolean isConversationSummaryEnabled() {
        ensureLoaded();
        return aiConversationSummaryEnabled;
    }

    public static boolean isWebLookupEnabled() {
        ensureLoaded();
        return aiWebLookupEnabled;
    }

    public static boolean isComputerControlEnabled() {
        ensureLoaded();
        return aiComputerControlEnabled;
    }

    public static boolean isJvmCodeSkillEnabled() {
        ensureLoaded();
        return aiJvmCodeSkillEnabled;
    }

    public static boolean isJvmPreferredEnabled() {
        ensureLoaded();
        return aiJvmPreferredEnabled;
    }

    public static CommandMode getCommandMode() {
        ensureLoaded();
        return getCommandModeUnchecked();
    }

    private static CommandMode getCommandModeUnchecked() {
        if (aiCommandMode == null) {
            aiCommandMode = CommandMode.NORMAL;
        }
        return aiCommandMode;
    }

    public static boolean isCommandEagerMode() {
        ensureLoaded();
        return getCommandModeUnchecked() == CommandMode.EAGER;
    }

    public static double getConfiguredTemperature() {
        ensureLoaded();
        return getConfiguredTemperatureUnchecked();
    }

    private static double getConfiguredTemperatureUnchecked() {
        return clampDouble(aiTemperature, 0.0D, 2.0D, DEFAULT_TEMPERATURE);
    }

    public static double getConfiguredTopP() {
        ensureLoaded();
        return getConfiguredTopPUnchecked();
    }

    private static double getConfiguredTopPUnchecked() {
        return clampDouble(aiTopP, 0.1D, 1.0D, DEFAULT_TOP_P);
    }

    public static int getConfiguredMaxOutputTokens() {
        ensureLoaded();
        return getConfiguredMaxOutputTokensUnchecked();
    }

    private static int getConfiguredMaxOutputTokensUnchecked() {
        return clampInt(aiMaxOutputTokens, 64, 4096, DEFAULT_MAX_OUTPUT_TOKENS);
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
        aiProvider = Provider.resolve(aiProviderId, aiEndpoint);
        if (aiProvider == null) {
            aiProvider = Provider.QINIU_CLOUD;
        }
        if (!providerProfilesLoaded) {
            saveCurrentFieldsToActiveProfile();
            providerProfilesLoaded = true;
        }
        for (Provider provider : Provider.values()) {
            normalizeProfile(provider, getProfile(provider));
        }
        applyActiveProfileToFields();

        // 标准化 systemPrompt，确保不为 null
        if (aiSystemPrompt == null || aiSystemPrompt.isEmpty()) {
            aiSystemPrompt = DEFAULT_SYSTEM_PROMPT;
        } else {
            aiSystemPrompt = aiSystemPrompt.replace("\r\n", "\n").replace('\r', '\n');
        }
        // 验证并修正数值范围
        if (aiCommandMode == null) {
            aiCommandMode = CommandMode.NORMAL;
        }
        aiTemperature = clampDouble(aiTemperature, 0.0D, 2.0D, DEFAULT_TEMPERATURE);
        aiTopP = clampDouble(aiTopP, 0.1D, 1.0D, DEFAULT_TOP_P);
        aiMaxOutputTokens = clampInt(aiMaxOutputTokens, 64, 4096, DEFAULT_MAX_OUTPUT_TOKENS);

        if (nbtStructures == null) {
            nbtStructures = new HashMap<>();
        }
    }

    private static void resetProviderProfiles() {
        providerProfiles.clear();
        for (Provider provider : Provider.values()) {
            providerProfiles.put(provider.getId(), createDefaultProfile(provider));
        }
        providerProfilesLoaded = false;
    }

    private static ProviderProfile createDefaultProfile(Provider provider) {
        Provider normalizedProvider = provider == null ? Provider.QINIU_CLOUD : provider;
        ProviderProfile profile = new ProviderProfile();
        profile.providerId = normalizedProvider.getId();
        profile.apiKey = DEFAULT_API_KEY_PLACEHOLDER;
        profile.endpoint = normalizedProvider.getEndpoint();
        profile.modelId = normalizedProvider.getDefaultModel();
        profile.modelName = normalizedProvider.getDefaultModel();
        return profile;
    }

    private static ProviderProfile getProfile(Provider provider) {
        Provider normalizedProvider = provider == null ? Provider.QINIU_CLOUD : provider;
        return providerProfiles.computeIfAbsent(normalizedProvider.getId(), ignored -> createDefaultProfile(normalizedProvider));
    }

    private static void normalizeProfile(Provider provider, ProviderProfile profile) {
        Provider normalizedProvider = provider == null ? Provider.QINIU_CLOUD : provider;
        if (profile.providerId == null || profile.providerId.isBlank()) {
            profile.providerId = normalizedProvider == Provider.CUSTOM ? Provider.CUSTOM.getId() : normalizedProvider.getId();
        } else if (normalizedProvider != Provider.CUSTOM) {
            profile.providerId = normalizedProvider.getId();
        } else {
            profile.providerId = normalizeCustomProviderId(profile.providerId);
        }

        profile.apiKey = normalizeApiKey(profile.apiKey);
        profile.endpoint = normalizeEndpoint(profile.endpoint);
        if (profile.endpoint.isEmpty() && normalizedProvider != Provider.CUSTOM) {
            profile.endpoint = normalizedProvider.getEndpoint();
        }
        profile.modelId = normalizeModelForProvider(normalizedProvider, profile.modelId);
        profile.modelName = normalizeModelName(profile.modelName, profile.modelId);
    }

    private static void saveCurrentFieldsToActiveProfile() {
        Provider provider = getProviderUnchecked();
        ProviderProfile profile = getProfile(provider);
        profile.providerId = provider == Provider.CUSTOM ? normalizeCustomProviderId(aiProviderId) : provider.getId();
        profile.apiKey = normalizeApiKey(aiApiKey);
        profile.endpoint = normalizeEndpoint(aiEndpoint);
        profile.modelId = normalizeModelForProvider(provider, aiModel);
        profile.modelName = normalizeModelName(aiModelName, profile.modelId);
        normalizeProfile(provider, profile);
    }

    private static void applyActiveProfileToFields() {
        Provider provider = getProviderUnchecked();
        ProviderProfile profile = getProfile(provider);
        normalizeProfile(provider, profile);
        aiProviderId = profile.providerId;
        aiApiKey = profile.apiKey;
        aiEndpoint = profile.endpoint;
        aiModel = profile.modelId;
        aiModelName = profile.modelName;
    }

    private static ProviderProfile copyProfile(Provider provider, ProviderProfile source) {
        ProviderProfile copy = new ProviderProfile();
        copy.providerId = source == null ? null : source.providerId;
        copy.apiKey = source == null ? null : source.apiKey;
        copy.endpoint = source == null ? null : source.endpoint;
        copy.modelId = source == null ? null : source.modelId;
        copy.modelName = source == null ? null : source.modelName;
        normalizeProfile(provider, copy);
        return copy;
    }

    private static void resetToDefaults() {
        aiProvider = Provider.QINIU_CLOUD;
        aiProviderId = aiProvider.getId();
        aiApiKey = DEFAULT_API_KEY_PLACEHOLDER;
        aiEndpoint = aiProvider.getEndpoint();
        aiModel = aiProvider.getDefaultModel();
        aiModelName = aiProvider.getDefaultModel();
        aiSystemPrompt = DEFAULT_SYSTEM_PROMPT;
        aiStreamingEnabled = false;
        aiConversationSummaryEnabled = true;
        aiWebLookupEnabled = false;
        aiComputerControlEnabled = false;
        aiJvmCodeSkillEnabled = false;
        aiJvmPreferredEnabled = false;
        aiCommandMode = CommandMode.NORMAL;
        customApiFormat = "";
        aiTemperature = DEFAULT_TEMPERATURE;
        aiTopP = DEFAULT_TOP_P;
        aiMaxOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS;
        nbtStructures = new HashMap<>();
        taskRoutes.clear();
        resetProviderProfiles();
        apiKeyMarkedInvalid = false;
    }

    public static synchronized void load() {
        loading = true;
        try {
            resetToDefaults();
            boolean publicConfigOk = readPublicConfig();
            boolean privateSecretsOk = readPrivateSecrets();

            normalizeSettings();
            loaded = true;

            if (!PUBLIC_CONFIG.exists() && publicConfigOk) {
                try {
                    savePublicConfig();
                } catch (IOException e) {
                    LOGGER.error("Failed to create default Herobrine Companion public AI config at {}", PUBLIC_CONFIG, e);
                }
            }
            if (!PRIVATE_SECRETS.exists() && privateSecretsOk) {
                try {
                    savePrivateSecrets();
                } catch (IOException e) {
                    LOGGER.error("Failed to create default Herobrine Companion private AI secrets at {}", PRIVATE_SECRETS, e);
                }
            }
        } finally {
            loading = false;
        }
    }

    private static boolean readPublicConfig() {
        if (PUBLIC_CONFIG.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(PUBLIC_CONFIG), StandardCharsets.UTF_8)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    if (data.aiModel != null) aiModel = data.aiModel;
                    if (data.aiModelName != null) {
                        aiModelName = data.aiModelName;
                    } else if (data.aiModel != null) {
                        aiModelName = data.aiModel;
                    }
                    if (data.activeProvider != null) aiProviderId = data.activeProvider;
                    if (data.aiProvider != null) aiProviderId = data.aiProvider;
                    if (data.providers != null) {
                        applyProfilesFromData(data.providers);
                    }
                    if (data.aiSystemPrompt != null) {
                        aiSystemPrompt = data.aiSystemPrompt;
                    } else if (data.systemPrompt != null) {
                        aiSystemPrompt = data.systemPrompt;
                    } else if (data.aiPrompt != null) {
                        aiSystemPrompt = data.aiPrompt;
                    }
                    if (data.aiStreamingEnabled != null) aiStreamingEnabled = data.aiStreamingEnabled;
                    if (data.aiConversationSummaryEnabled != null) aiConversationSummaryEnabled = data.aiConversationSummaryEnabled;
                    if (data.aiWebLookupEnabled != null) aiWebLookupEnabled = data.aiWebLookupEnabled;
                    if (data.aiComputerControlEnabled != null) aiComputerControlEnabled = data.aiComputerControlEnabled;
                    if (data.aiJvmCodeSkillEnabled != null) aiJvmCodeSkillEnabled = data.aiJvmCodeSkillEnabled;
                    if (data.aiJvmPreferredEnabled != null) aiJvmPreferredEnabled = data.aiJvmPreferredEnabled;
                    CommandMode savedCommandMode = CommandMode.fromSavedValue(data.aiCommandMode);
                    if (savedCommandMode == null) {
                        savedCommandMode = CommandMode.fromSavedValue(data.commandMode);
                    }
                    if (savedCommandMode != null) aiCommandMode = savedCommandMode;
                    if (data.customApiFormat != null) customApiFormat = data.customApiFormat;
                    if (data.aiTemperature != null) aiTemperature = data.aiTemperature;
                    if (data.aiTopP != null) aiTopP = data.aiTopP;
                    if (data.aiMaxOutputTokens != null) aiMaxOutputTokens = data.aiMaxOutputTokens;

                    if (data.nbtStructures != null) nbtStructures = data.nbtStructures;
                    if (data.taskRoutes != null) {
                        taskRoutes.clear();
                        for (Map.Entry<String, TaskRoute> entry : data.taskRoutes.entrySet()) {
                            if (entry.getValue() == null) {
                                continue;
                            }
                            try {
                                LlmTask task = LlmTask.valueOf(entry.getKey().trim().toUpperCase(Locale.ROOT));
                                taskRoutes.put(task, entry.getValue());
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load Herobrine Companion public AI config from {}", PUBLIC_CONFIG, e);
                return false;
            }
        }
        return true;
    }

    private static boolean readPrivateSecrets() {
        if (PRIVATE_SECRETS.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(PRIVATE_SECRETS), StandardCharsets.UTF_8)) {
                SecretData data = GSON.fromJson(reader, SecretData.class);
                if (data != null) {
                    if (data.activeProvider != null) aiProviderId = data.activeProvider;
                    if (data.aiProvider != null) aiProviderId = data.aiProvider;
                    if (data.providers != null) {
                        applyProfilesFromData(data.providers);
                    }
                    if (data.aiApiKey != null) aiApiKey = data.aiApiKey;
                    if (data.aiEndpoint != null) aiEndpoint = data.aiEndpoint;
                    if (data.aiModel != null) aiModel = data.aiModel;
                    if (data.aiModelName != null) {
                        aiModelName = data.aiModelName;
                    } else if (data.aiModel != null) {
                        aiModelName = data.aiModel;
                    }
                    aiProvider = Provider.resolve(aiProviderId, data.aiEndpoint);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load Herobrine Companion private AI secrets from {}", PRIVATE_SECRETS, e);
                return false;
            }
        }
        return true;
    }

    public static synchronized void save() {
        try {
            loaded = true;
            saveCurrentFieldsToActiveProfile();
            normalizeSettings();
            markApiKeyValid();
            savePublicConfig();
            savePrivateSecrets();
        } catch (Exception e) {
            LOGGER.error("Failed to save Herobrine Companion AI config", e);
        }
    }

    private static void savePublicConfig() throws IOException {
        PUBLIC_CONFIG.getParentFile().mkdirs();
        ConfigData pData = new ConfigData();
        pData.aiModel = aiModel;
        pData.aiModelName = aiModelName;
        pData.activeProvider = getProviderUnchecked().getId();
        pData.aiSystemPrompt = aiSystemPrompt;
        pData.aiStreamingEnabled = aiStreamingEnabled;
        pData.aiConversationSummaryEnabled = aiConversationSummaryEnabled;
        pData.aiWebLookupEnabled = aiWebLookupEnabled;
        pData.aiComputerControlEnabled = aiComputerControlEnabled;
        pData.aiJvmCodeSkillEnabled = aiJvmCodeSkillEnabled;
        pData.aiJvmPreferredEnabled = aiJvmPreferredEnabled;
        pData.aiCommandMode = getCommandModeUnchecked().getId();
        pData.customApiFormat = customApiFormat;
        pData.aiTemperature = aiTemperature;
        pData.aiTopP = aiTopP;
        pData.aiMaxOutputTokens = aiMaxOutputTokens;
        pData.nbtStructures = nbtStructures;
        pData.taskRoutes = copyTaskRoutesForSave();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(PUBLIC_CONFIG), StandardCharsets.UTF_8)) {
            GSON.toJson(pData, writer);
        }
    }

    private static void savePrivateSecrets() throws IOException {
        PRIVATE_SECRETS.getParentFile().mkdirs();
        SecretData sData = new SecretData();
        sData.aiApiKey = aiApiKey;
        sData.aiEndpoint = getResolvedEndpointUnchecked();
        sData.aiProvider = getStoredProviderIdUnchecked();
        sData.aiModel = aiModel;
        sData.aiModelName = aiModelName;
        sData.activeProvider = getProviderUnchecked().getId();
        sData.providers = copyProfilesForSave();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(PRIVATE_SECRETS), StandardCharsets.UTF_8)) {
            GSON.toJson(sData, writer);
        }
    }

    private static void applyProfilesFromData(Map<String, ProviderProfile> savedProfiles) {
        if (savedProfiles == null || savedProfiles.isEmpty()) {
            return;
        }
        for (Map.Entry<String, ProviderProfile> entry : savedProfiles.entrySet()) {
            Provider provider = Provider.fromSavedValue(entry.getKey());
            if (provider == null && entry.getValue() != null) {
                provider = Provider.fromSavedValue(entry.getValue().providerId);
            }
            if (provider == null) {
                continue;
            }
            providerProfiles.put(provider.getId(), copyProfile(provider, entry.getValue()));
        }
        providerProfilesLoaded = true;
    }

    private static Map<String, ProviderProfile> copyProfilesForSave() {
        Map<String, ProviderProfile> profilesForSave = new LinkedHashMap<>();
        for (Provider provider : Provider.values()) {
            profilesForSave.put(provider.getId(), copyProfile(provider, getProfile(provider)));
        }
        return profilesForSave;
    }

    private static Map<String, TaskRoute> copyTaskRoutesForSave() {
        Map<String, TaskRoute> routesForSave = new LinkedHashMap<>();
        for (LlmTask task : LlmTask.values()) {
            TaskRoute route = taskRoutes.get(task);
            if (route != null && (!route.providerId().isBlank() || !route.fallbackProviderId().isBlank())) {
                routesForSave.put(task.name(), route);
            }
        }
        return routesForSave.isEmpty() ? null : routesForSave;
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

    private static String normalizeCustomProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return Provider.CUSTOM.getId();
        }
        return providerId.trim();
    }

    private static String normalizeEndpoint(String endpoint) {
        return endpoint == null ? "" : endpoint.trim();
    }

    private static String normalizeApiKey(String apiKey) {
        String normalized = apiKey == null ? "" : apiKey.trim();
        return normalized.isEmpty() ? DEFAULT_API_KEY_PLACEHOLDER : normalized;
    }

    private static String normalizeModelForProvider(Provider provider, String model) {
        String normalized = model == null ? "" : model.trim();
        if (!normalized.isEmpty()) {
            return normalized;
        }
        Provider normalizedProvider = provider == null ? Provider.QINIU_CLOUD : provider;
        return normalizedProvider == Provider.CUSTOM ? "" : normalizedProvider.getDefaultModel();
    }

    private static String normalizeModelName(String modelName, String modelId) {
        String normalized = modelName == null ? "" : modelName.trim();
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return modelId == null ? "" : modelId.trim();
    }

    public static String normalizeChatEndpoint(String endpoint) {
        return normalizeEndpoint(endpoint);
    }

    public static EndpointFormat detectEndpointFormat(String endpoint) {
        String normalized = normalizeEndpoint(endpoint).toLowerCase(Locale.ROOT);
        String withoutQuery = removeQueryAndFragment(normalized);
        if (withoutQuery.contains("generativelanguage.googleapis.com")
                || withoutQuery.contains("/generativelanguage")) {
            return EndpointFormat.GEMINI;
        }
        if (withoutQuery.endsWith("/v1/responses") || withoutQuery.endsWith("/responses")) {
            return EndpointFormat.OPENAI_RESPONSES;
        }
        if (withoutQuery.contains("anthropic.com") || withoutQuery.endsWith("/v1/messages") || withoutQuery.endsWith("/messages")) {
            return EndpointFormat.ANTHROPIC;
        }
        return EndpointFormat.OPENAI_CHAT;
    }

    private static String removeQueryAndFragment(String endpoint) {
        int queryIndex = endpoint.indexOf('?');
        int fragmentIndex = endpoint.indexOf('#');
        int cutIndex = -1;
        if (queryIndex >= 0) {
            cutIndex = queryIndex;
        }
        if (fragmentIndex >= 0 && (cutIndex < 0 || fragmentIndex < cutIndex)) {
            cutIndex = fragmentIndex;
        }
        return cutIndex >= 0 ? endpoint.substring(0, cutIndex) : endpoint;
    }

    private static class ConfigData {
        String aiModel;
        String aiModelName;
        String activeProvider;
        String aiProvider;
        String aiSystemPrompt;
        String systemPrompt;
        String aiPrompt;
        Boolean aiStreamingEnabled;
        Boolean aiConversationSummaryEnabled;
        Boolean aiWebLookupEnabled;
        Boolean aiComputerControlEnabled;
        Boolean aiJvmCodeSkillEnabled;
        Boolean aiJvmPreferredEnabled;
        String aiCommandMode;
        String commandMode;
        String customApiFormat;
        Double aiTemperature;
        Double aiTopP;
        Integer aiMaxOutputTokens;
        Map<String, String> nbtStructures;
        Map<String, TaskRoute> taskRoutes;
        Map<String, ProviderProfile> providers;

    } private static class SecretData {
        String aiApiKey;
        String aiEndpoint;
        String aiProvider;
        String aiModel;
        String aiModelName;
        String activeProvider;
        Map<String, ProviderProfile> providers;
    }
}
