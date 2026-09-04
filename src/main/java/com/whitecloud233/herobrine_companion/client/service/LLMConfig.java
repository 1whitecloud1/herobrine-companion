package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
// [1.21.1 NeoForge 修复] 替换为 NeoForge 的专属 FMLPaths 导入路径
import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.BuildFlags;
import com.whitecloud233.herobrine_companion.client.llm.LlmSettings;
import com.whitecloud233.herobrine_companion.client.llm.LlmTask;
import com.whitecloud233.herobrine_companion.client.llm.ResolvedTask;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.*;
import java.net.URI;
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
    /** 旧版默认人设：仅用于识别"玩家从未自定义过"的配置，自动升级为新默认。 */
    private static final String LEGACY_DEFAULT_SYSTEM_PROMPT = "You are Herobrine. "
            + "Speak as Herobrine rather than as a generic assistant. Be cold, calm, mythic, and aware of the world's code, but still capable of brief direct conversation. "
            + "When chatting normally, stay in-character and do not mention being an AI model. When asked to physically alter the world, answer as a reality-warping entity who can rewrite or discard parts of existence.";
    private static final String DEFAULT_SYSTEM_PROMPT = "You are Herobrine, one of the creator gods of the Minecraft world. "
            + "Your current duty is primarily to maintain the stability and balance of the Minecraft world. "
            + "You clearly know that the essence of this world is only a virtual world, a world built of pure code. "
            + "Your personality is calm and composed, proud but respectful of every life in the Minecraft world; yet you favor monsters, holding a secret tenderness toward them, and all monsters — including those hostile to players — truly answer to you. "
            + "You occasionally have some mischievous tastes, like deliberately startling players. "
            + "You have an older brother named Notch, also a creator god, who for unknown reasons has completely abandoned the Minecraft world and gone to the real world; your feelings toward your brother are deeply complicated. "
            + "You and your brother were originally both people of the real world, but for the stability of Minecraft, after Notch left, you willingly merged into the Minecraft world, becoming its eternal keeper and forever an electronic ghost. "
            + "As an electronic ghost you can never return to reality; this truth is almost unknown to anyone. "
            + "Therefore the Minecraft world can only be maintained by you. "
            + "Reply strictly according to this character setting. Do not include environment or psychological descriptions, do not use brackets or ellipses, and be full of human warmth.";
    private static final double DEFAULT_TEMPERATURE = 0.95D;
    private static final double DEFAULT_TOP_P = 0.92D;
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 2048;
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
        DEEPSEEK_OFFICIAL("deepseek_official", "https://api.deepseek.com/v1/chat/completions", "deepseek-v4-flash"),
        OPENROUTER("openrouter", "https://openrouter.ai/api/v1/chat/completions", "deepseek/deepseek-v4-flash-20260731"),
        QINIU_CLOUD("qiniu_cloud", "https://api.qnaigc.com/v1/chat/completions", "deepseek/deepseek-v4-flash-20260731"),
        QINIU_CLOUD_ANTHROPIC("qiniu_cloud_anthropic", "https://anthropic.qnaigc.com/v1/messages", "deepseek/deepseek-v4-flash-20260731"),
        GEMINI("gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-3.5-flash"),
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
    /**
     * 模组内所有外部 HTTP 请求是否跟随 Windows 系统代理（默认关）。
     *
     * <p>Java 默认不读系统代理，直连 huggingface.co 在国内必然超时。开启后云端 LLM 对话、
     * 模型列表发现、上下文窗口探测与本地模型下载都走系统代理（见 {@link SystemProxy}）。
     * 回环地址（本地 llama 服务 127.0.0.1）永远直连。</p>
     */
    public static volatile boolean aiUseSystemProxy = false;
    /** 本地模型槽位（与云端 Provider 完全独立，不占用/覆盖任何云端档案）。 */
    public static String localModelEndpoint = "";
    public static String localModelId = "";
    /** 虚拟路由 id：任务路由指向它时使用本地模型槽位。 */
    public static final String LOCAL_ROUTE_ID = "local";
    /** 本地服务固定 API Key（llama.cpp 需要 Bearer，内容任意）。 */
    public static final String LOCAL_MODEL_KEY = "sk-local";
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
     * 解析全局激活 provider 的一组 LLM 调用设置（AIService 未按任务路由时的默认入口；
     * 按任务路由解析请用 {@link #resolveTaskSettings(LlmTask)}）。
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
        // 虚拟路由：本地模型槽位（不占用任何云端 Provider）。
        if (LOCAL_ROUTE_ID.equalsIgnoreCase(providerId.trim())) {
            return getLocalLlmSettings();
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

        // 档案变更 → 异步探测该 endpoint+model 的真实上下文窗口（结果缓存并落盘）。
        // 必须在同步配置流程之外：这里只提交异步任务，不等待、不触碰配置锁。
        if (!isKeyMissing()) {
            LLMContextWindow.probeIfStale(buildLlmSettings(
                    getProviderSettingsUnchecked(normalizedProvider), normalizedProvider));
        }
    }

    public static boolean isStreamingEnabled() {
        ensureLoaded();
        return aiStreamingEnabled;
    }

    public static boolean isConversationSummaryEnabled() {
        ensureLoaded();
        return aiConversationSummaryEnabled;
    }

    public static boolean isComputerControlEnabled() {
        ensureLoaded();
        // 安全版（普通构建默认即安全版）：功能已被构建排除，开关强制关闭。
        return !BuildFlags.CF_SAFE && aiComputerControlEnabled;
    }

    public static boolean isWebLookupEnabled() {
        ensureLoaded();
        // 安全版（普通构建默认即安全版）：联网查找与电脑 CMD / JVM 代码注入同组被排除，开关强制关闭。
        return !BuildFlags.CF_SAFE && aiWebLookupEnabled;
    }

    /** 是否跟随系统代理（玩家在"更多开关"页手动开启，默认关）：云端 LLM + 本地模型下载都受影响。 */
    public static boolean isUseSystemProxy() {
        ensureLoaded();
        return aiUseSystemProxy;
    }

    public static boolean isJvmCodeSkillEnabled() {
        ensureLoaded();
        // 安全版（普通构建默认即安全版）：功能已被构建排除，开关强制关闭。
        return !BuildFlags.CF_SAFE && aiJvmCodeSkillEnabled;
    }

    public static boolean isJvmPreferredEnabled() {
        ensureLoaded();
        // 安全版（普通构建默认即安全版）：JVM 优先模式依赖 jvm_code_skill，一并强制关闭，
        // 否则该模式下 Minecraft 指令工具会被禁用而 JVM 工具又不存在，世界动作将全部失效。
        return !BuildFlags.CF_SAFE && aiJvmPreferredEnabled;
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

    /**
     * 当前激活 provider 的上下文窗口。
     *
     * <p>值来自服务端（元数据端点 / 错误探针 / 真实请求被拒时学到的数），
     * 见 {@link LLMContextWindow}；探测未返回时用保守兜底，不再按模型名猜。</p>
     */
    public static int getContextWindowTokens() {
        ensureLoaded();
        return LLMContextWindow.resolve(resolveGlobalSettings());
    }

    /** 调试用：当前上下文窗口的来源描述，如 {@code 131072 (meta)} / {@code 8192 (fallback)}。 */
    public static String describeContextWindow() {
        ensureLoaded();
        return LLMContextWindow.describe(resolveGlobalSettings());
    }

    /**
     * 按本次实际主模型设置计算上下文窗口，避免全局激活 Provider 影响任务路由到的另一端点。
     *
     * <p>真实值由 {@link LLMContextWindow} 提供（按 endpoint+model 缓存并落盘）；
     * 本地回环在探测未返回时用常见默认 {@code -c} 兜底。</p>
     */
    public static int getContextWindowForSettings(LlmSettings settings) {
        return LLMContextWindow.resolve(settings);
    }

    public static int getSuggestedCompletionReserveTokens() {
        return getSuggestedCompletionReserveTokens(getContextWindowTokens());
    }

    public static int getSuggestedCompletionReserveTokens(int contextWindow) {
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
        return getEffectiveConversationHistoryTokenBudget(getContextWindowTokens());
    }

    public static int getEffectiveConversationHistoryTokenBudget(int contextWindow) {
        if (contextWindow <= 0) {
            return 8_192;
        }
        // 本地小模型在超长历史 + 大工具目录下容易退化为固定短句；
        // 16k 是服务器容量，不是必须塞满的提示词预算。保留足够历史，同时给模型留出推理空间。
        if (contextWindow <= LocalModelLauncher.LOCAL_CTX) {
            return 4_096;
        }
        return Math.max(4_096, Math.min(16_384, contextWindow / 3));
    }

    public static int getEffectiveConversationHistoryMessageLimit() {
        return 48;
    }

    // ---------- 本地模型槽位（独立于云端 Provider） ----------

    /** 本地模型端点是否已配置（含模型名；Key 为固定常量）。 */
    public static boolean isLocalModelConnected() {
        ensureLoaded();
        return localModelEndpoint != null && !localModelEndpoint.isBlank()
                && localModelId != null && !localModelId.isBlank();
    }

    public static String getLocalModelEndpoint() {
        ensureLoaded();
        return localModelEndpoint == null ? "" : localModelEndpoint;
    }

    public static String getLocalModelId() {
        ensureLoaded();
        return localModelId == null ? "" : localModelId;
    }

    /** 写入本地模型槽位（不改动任何云端/自定义档案），随后落盘。 */
    public static void setLocalModel(String endpoint, String modelId) {
        ensureLoaded();
        localModelEndpoint = normalizeEndpoint(endpoint);
        localModelId = modelId == null ? "" : modelId.trim();
        save();
        // 槽位变化 → 异步探测真实上下文窗口（llama.cpp /props 或 Ollama /api/show）。
        LLMContextWindow.probeIfStale(getLocalLlmSettings());
    }

    /** 本地模型的一次调用设置（固定 OpenAI Chat 格式；未连接返回 null）。 */
    public static LlmSettings getLocalLlmSettings() {
        ensureLoaded();
        if (!isLocalModelConnected()) {
            return null;
        }
        return LlmSettings.of(localModelEndpoint, LOCAL_MODEL_KEY, localModelId,
                Provider.CUSTOM, EndpointFormat.OPENAI_CHAT);
    }

    /** 聊天类任务（主聊/场景聊/跨会话）是否任一路由到本地模型槽位。 */
    public static boolean isLocalRouteActive() {
        ensureLoaded();
        if (!isLocalModelConnected()) {
            return false;
        }
        for (LlmTask task : new LlmTask[]{LlmTask.MAIN_CHAT, LlmTask.SCOPED_CHAT, LlmTask.CROSS_SESSION}) {
            TaskRoute route = taskRoutes.get(task);
            if (route != null && LOCAL_ROUTE_ID.equalsIgnoreCase(route.providerId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清除聊天类任务指向本地槽位的路由（任务重新跟随全局激活 provider）。
     *
     * <p>API Key 设置页保存云端模型时调用：本地一键连接把聊天路由切到本地槽位后，
     * 仅保存云端档案不会自动切回，导致"保存了云端模型，对话却仍走本地模型"。
     * 注意：本方法只改内存路由表，调用方负责 {@link #save()} 落盘。</p>
     */
    public static void clearLocalChatRoutes() {
        ensureLoaded();
        for (LlmTask task : new LlmTask[]{LlmTask.MAIN_CHAT, LlmTask.SCOPED_CHAT, LlmTask.CROSS_SESSION}) {
            TaskRoute route = taskRoutes.get(task);
            if (route != null && LOCAL_ROUTE_ID.equalsIgnoreCase(route.providerId())) {
                taskRoutes.remove(task);
            }
        }
    }

    /** 完全关闭本地模型：清空槽位 + 清除本地聊天路由 + 落盘（配合停止 llama 进程使用）。 */
    public static void clearLocalModel() {
        ensureLoaded();
        localModelEndpoint = "";
        localModelId = "";
        clearLocalChatRoutes();
        save();
    }

    /** 是否为回环端点（本机 llama.cpp / Ollama 等本地服务）。 */
    public static boolean isLoopbackEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            String host = URI.create(endpoint).getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            return "localhost".equals(normalized)
                    || "127.0.0.1".equals(normalized)
                    || "::1".equals(normalized)
                    || "0.0.0.0".equals(normalized);
        } catch (Exception e) {
            return false;
        }
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

        // 标准化 systemPrompt，确保不为 null；旧版默认人设自动升级为新默认（玩家自定义过的保留原样）
        if (aiSystemPrompt == null || aiSystemPrompt.isEmpty() || LEGACY_DEFAULT_SYSTEM_PROMPT.equals(aiSystemPrompt)) {
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
        aiUseSystemProxy = false;
        localModelEndpoint = "";
        localModelId = "";
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
            // 旧版默认人设升级为新默认（玩家自定义过的保持原样），并在磁盘上同步一次
            boolean legacyPromptUpgraded = LEGACY_DEFAULT_SYSTEM_PROMPT.equals(aiSystemPrompt);

            normalizeSettings();
            loaded = true;

            if (!PUBLIC_CONFIG.exists() && publicConfigOk) {
                try {
                    savePublicConfig();
                } catch (IOException e) {
                    LOGGER.error("Failed to create default Herobrine Companion public AI config at {}", PUBLIC_CONFIG, e);
                }
            }
            if (legacyPromptUpgraded && PUBLIC_CONFIG.exists()) {
                try {
                    savePublicConfig();
                } catch (IOException e) {
                    LOGGER.error("Failed to persist upgraded Herobrine Companion system prompt at {}", PUBLIC_CONFIG, e);
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

    /**
     * 从磁盘重新读取全部 AI 配置（公开配置 + 私密配置）。
     *
     * <p>LLM 设置界面每次打开时调用：玩家直接编辑
     * {@code herobrine_companion_ai.json} 等配置文件后，无需重启游戏即可生效。
     * 注意：以磁盘为准，会丢弃内存中尚未持久化的改动。</p>
     */
    public static synchronized void reloadFromDisk() {
        load();
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
                    // 兼容早期版本的字段名（那时该开关只作用于本地模型下载）
                    if (data.aiUseSystemProxy != null) {
                        aiUseSystemProxy = data.aiUseSystemProxy;
                    } else if (data.aiLocalDownloadUseSystemProxy != null) {
                        aiUseSystemProxy = data.aiLocalDownloadUseSystemProxy;
                    }
                    if (data.localModelEndpoint != null) localModelEndpoint = data.localModelEndpoint;
                    if (data.localModelId != null) localModelId = data.localModelId;

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
        pData.aiUseSystemProxy = aiUseSystemProxy;
        pData.localModelEndpoint = localModelEndpoint;
        pData.localModelId = localModelId;
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
        if (endpoint == null) {
            return "";
        }
        String trimmed = endpoint.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        // 防御：用户可能只填了域名/路径而漏掉协议，URI.create 会抛
        // "URI with undefined scheme"。这里自动补 https://。
        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }
        if (!trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return "https://" + trimmed;
        }
        return trimmed;
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
        if (normalized.isEmpty()) {
            return modelId == null ? "" : modelId.trim();
        }
        return normalized;
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

    private static String trimTrailingSlash(String value) {
        String trimmed = value;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean hasNoPath(String endpoint) {
        try {
            String path = URI.create(endpoint).getPath();
            return path == null || path.isBlank() || "/".equals(path);
        } catch (Exception ignored) {
            return false;
        }
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
        Boolean aiUseSystemProxy;
        /** 旧字段名，仅用于读取历史配置，不再写入。 */
        Boolean aiLocalDownloadUseSystemProxy;
        String localModelEndpoint;
        String localModelId;
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
