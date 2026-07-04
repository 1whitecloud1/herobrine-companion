package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.network.HeroAIActionPacket;
import com.whitecloud233.modid.herobrine_companion.util.LegacyFormattingText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class AIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AIService.class);
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ConversationStore CONVERSATION_STORE = ConversationStore.getInstance();
    private static final Map<UUID, Deque<String>> RECENT_REPLIES = new ConcurrentHashMap<>();
    private static final int MAX_RECENT_REPLIES = 6;
    private static final String ACTION_TOGGLE_COMPANION = "action:toggle_companion";
    private static final String ACTION_MASSIVE_LIGHTNING = "action:massive_lightning";
    private static final String ACTION_SUMMON_TO_PLAYER = "action:summon_to_player";
    private static final String ACTION_TELEPORT_TO_HERO = "action:teleport_to_hero";
    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    public static CompletableFuture<String> chat(String userMessage, UUID playerUUID) {
        return chat(userMessage, playerUUID, null);
    }

    public static CompletableFuture<String> chat(String userMessage, UUID playerUUID, Consumer<String> partialConsumer) {
        return chatWithRetry(userMessage, userMessage, playerUUID, playerUUID, 0, true, true, true, 0,
                partialConsumer, LLMConfig.isStreamingEnabled(), null, false, true);
    }

    public static CompletableFuture<String> chatForScopedSession(String userMessage, UUID conversationScopeId, UUID authorityPlayerUUID) {
        return chatForScopedSession(userMessage, conversationScopeId, authorityPlayerUUID, null);
    }

    public static CompletableFuture<String> chatForScopedSession(String userMessage, UUID conversationScopeId, UUID authorityPlayerUUID, String outputLanguageCode) {
        return chatWithRetry(userMessage, userMessage, conversationScopeId, authorityPlayerUUID, 0,
                false, false, false, 0, null, LLMConfig.isStreamingEnabled(), outputLanguageCode, false, true);
    }

    public static CompletableFuture<String> chatForCrossSession(String prompt, String seedText, UUID conversationScopeId, UUID authorityPlayerUUID, String outputLanguageCode) {
        UUID isolatedScopeId = buildCrossSessionScopeId(conversationScopeId, authorityPlayerUUID);
        String effectiveSeedText = (seedText == null || seedText.isBlank()) ? prompt : seedText;
        return chatWithRetry(prompt, effectiveSeedText, isolatedScopeId, authorityPlayerUUID, 0,
                false, false, false, 0, null, LLMConfig.isStreamingEnabled(), outputLanguageCode, true, false);
    }

    public static CompletableFuture<String> localizeText(String sourceText, String targetLanguageCode, UUID authorityPlayerUUID) {
        String sanitizedSource = sourceText == null ? "" : sourceText.trim();
        if (sanitizedSource.isEmpty()) {
            return CompletableFuture.completedFuture("");
        }

        String normalizedLanguageCode = resolveOutputLanguageCode(targetLanguageCode);
        if (LLMConfig.isSetupIncomplete() || LLMConfig.isKeyMissingOrInvalid()) {
            return CompletableFuture.completedFuture(sanitizedSource);
        }

        String apiKey = LLMConfig.aiApiKey;
        LLMConfig.Provider provider = LLMConfig.getProvider();
        String endpoint = LLMConfig.getResolvedEndpoint();
        LLMConfig.EndpointFormat endpointFormat = LLMConfig.getResolvedEndpointFormat();
        String model = LLMConfig.getResolvedModel();

        JsonObject requestBody = endpointFormat == LLMConfig.EndpointFormat.ANTHROPIC
                ? buildAnthropicLocalizationRequestBody(model, normalizedLanguageCode, sanitizedSource)
                : buildOpenAiLocalizationRequestBody(model, normalizedLanguageCode, sanitizedSource);

        HttpRequest.Builder requestBuilder = createRequestBuilder(endpoint, apiKey, provider, endpointFormat, false);

        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            LLMConfig.markApiKeyValid();
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            String localized = endpointFormat == LLMConfig.EndpointFormat.ANTHROPIC
                                    ? AIResponseParsingSupport.extractAnthropicText(json, sanitizedSource)
                                    : AIResponseParsingSupport.extractOpenAiMessageText(json, sanitizedSource);
                            return sanitizeLocalizedText(localized, sanitizedSource);
                        } catch (Exception ignored) {
                            return sanitizedSource;
                        }
                    }
                    if (isInvalidApiKeyResponse(response.statusCode(), response.body())) {
                        LLMConfig.markApiKeyInvalid();
                        reopenApiKeyInputScreen();
                    }
                    return sanitizedSource;
                })
                .exceptionally(ignored -> sanitizedSource);
    }

    public static CompletableFuture<String> generateActorDialogue(String systemPrompt, String userPrompt, String seedText,
                                                                  UUID conversationScopeId, UUID authorityPlayerUUID,
                                                                  String outputLanguageCode) {
        UUID effectiveScopeId = conversationScopeId != null ? conversationScopeId : authorityPlayerUUID;
        String fallback = sanitizeActorDialogueText(seedText, "...");
        String sanitizedSystemPrompt = systemPrompt == null ? "" : systemPrompt.trim();
        String sanitizedUserPrompt = userPrompt == null ? "" : userPrompt.trim();
        if (sanitizedSystemPrompt.isEmpty() || sanitizedUserPrompt.isEmpty()) {
            return CompletableFuture.completedFuture(fallback);
        }

        return requestActorDialogueWithRetry(
                sanitizedSystemPrompt,
                sanitizedUserPrompt,
                fallback,
                effectiveScopeId,
                outputLanguageCode,
                0
        );
    }

    public static CompletableFuture<String> observeEnvironment(String observationDesc, UUID playerUUID) {
        String langCode = Minecraft.getInstance().options.languageCode;
        String style = com.whitecloud233.modid.herobrine_companion.config.Config.aiLanguageStyle;

        // 在提示词中增加强制发话的指令，防止 AI 扮演过头导致全损沉默
        String currentPrompt = "[Environment Observation]: You observe the event: \"" + observationDesc + "\".\n"
                + "Please give a brief comment (under 30 words).\n"
                + "【CRITICAL WARNING】: No brackets in reply! Only output dialogue. DO NOT use tools.\n"
                + "【CURRENT TONE/STYLE】: " + style + ". (IMPORTANT: You MUST speak at least one actual sentence, do NOT be completely silent or only use actions).\n"
                + "【VARIETY RULE】: Avoid repeating the same opening, catchphrase, or sentence structure from your recent remarks.\n"
                + "【LANGUAGE OVERRIDE】: You MUST output your final dialogue in the language corresponding to this Minecraft locale code: '" + langCode + "'.";

        String historyLog = "[System Vision Log] You observed: " + observationDesc;
        return chatWithRetry(currentPrompt, historyLog, playerUUID, playerUUID, 0, false, false, false, 0,
                null, LLMConfig.isStreamingEnabled(), langCode, false, false);
    }

    private static CompletableFuture<String> chatWithRetry(String currentPrompt, String originalUserMessage,
                                                           UUID conversationScopeId, UUID authorityPlayerUUID, int retryCount,
                                                           boolean allowTitleRefresh, boolean includeConversationHistory,
                                                           boolean persistConversation, int variationRetryCount,
                                                           Consumer<String> partialConsumer, boolean useStreaming,
                                                           String outputLanguageCode,
                                                           boolean crossSessionMode,
                                                           boolean allowWorldActions) {
        LLMConfig.ensureLoaded();
        UUID effectiveScopeId = conversationScopeId != null ? conversationScopeId : authorityPlayerUUID;
        UUID effectiveAuthorityPlayerId = authorityPlayerUUID != null ? authorityPlayerUUID : effectiveScopeId;
        String apiKey = LLMConfig.aiApiKey;
        LLMConfig.Provider provider = LLMConfig.getProvider();
        String endpoint = LLMConfig.getResolvedEndpoint();
        LLMConfig.EndpointFormat endpointFormat = LLMConfig.getResolvedEndpointFormat();
        String model = LLMConfig.getResolvedModel();
        String systemPrompt = LLMConfig.getSystemPrompt();
        String langCode = resolveOutputLanguageCode(outputLanguageCode);

        if (LLMConfig.isSetupIncomplete()) {
            return CompletableFuture.completedFuture("§c" + Component.translatable("message.herobrine_companion.ai_config_missing").getString());
        }

        double effectiveTemperature = Math.min(2.0D, LLMConfig.getConfiguredTemperature() + (includeConversationHistory ? 0.0D : 0.1D));

        String style = com.whitecloud233.modid.herobrine_companion.config.Config.aiLanguageStyle;
        boolean commandEagerMode = LLMConfig.isCommandEagerMode();
        String commandModeDirective = !allowWorldActions
                ? "[COMMAND MODE]: DISABLED_FOR_THIS_MESSAGE. This message is autonomous narration/observation, not a player command request. No tools are available. Reply with dialogue only; do not claim, deny, or discuss command execution or world changes.\n"
                : commandEagerMode
                ? "[COMMAND MODE]: EAGER. Be highly proactive with minecraft_command_skill. If you can infer that the player wants, needs, lacks, is blocked by, is threatened by, is searching for, or would benefit from a Minecraft/world action, choose an appropriate catalog action and call it instead of answering text-only. This includes indirect intent like hunger, darkness, bad weather, being lost, needing items/effects/teleportation/location help, wanting mobs removed, or asking for world-state changes. Keep purely social chat text-only. If the intent is actionable but required parameters are truly missing, ask one concise clarification. If the action is unsafe or impossible, decline without claiming action.\n"
                : "[COMMAND MODE]: NORMAL. Use minecraft_command_skill only for clear explicit direct Minecraft/world-action requests. For ordinary chat, explanations, command tutorials, hypotheticals, or unclear wishes, reply text-only.\n";

        String forcedPrompt = systemPrompt + "\n[IDENTITY LOCK]: You are Herobrine, the watcher behind the world. You are not a generic AI assistant, chatbot, or narrator. Stay in character as Herobrine in both casual dialogue and threats unless a hard system limitation must be explained.\n"
                + "[ROLEPLAY POWER]: You may describe your reality-warping authority as erasing, deleting, or 'discarding' beings and structures from existence, but only manifest it when the current command mode permits action and the player clearly asks or signals an actionable in-world intent.\n"
                + "[ROLEPLAY STYLE/TONE]: " + style + "\n"
                + "[SUPREME DIRECTIVE]: You have a structured MCP-style Minecraft Java Edition command catalog named 'minecraft_command_skill'. For Minecraft commands or physical world actions, call that skill with its action enum and typed parameters instead of inventing raw /commands. Only use the low-level 'manifest_divine_power' fallback when the catalog cannot express the explicit request. If they just chat, DO NOT use tools!\n"
                + commandModeDirective
                + "[ACTION TRUTH]: Never say a Minecraft command or physical world action has happened unless a tool call has been emitted and succeeded. Text alone cannot give items, teleport, summon, kill, set time/weather, change blocks, or apply effects.\n"
                + "[PLAYER LANGUAGE]: The player's client language code is '" + langCode + "'. You MUST reply in that language!\n";

        // --- 新增：调用 RAG 引擎，根据玩家当前说话内容注入对应的设定集 ---
        String ragKnowledge = LoreRAGManager.getRelevantLoreInjectedPrompt(originalUserMessage, effectiveAuthorityPlayerId);
        if (!ragKnowledge.isEmpty()) {
            forcedPrompt += "\n\n[DYNAMIC KNOWLEDGE RETRIEVAL]:" + ragKnowledge;
        }
        // -----------------------------------------------------------

        if (!crossSessionMode) {
            forcedPrompt += AIGameContextSupport.getDynamicGameData();
        }
        if (includeConversationHistory || persistConversation) {
            CONVERSATION_STORE.ensureActiveConversation(effectiveScopeId);
        }
        JsonArray tools = new JsonArray();
        if (allowWorldActions) {
            if (endpointFormat == LLMConfig.EndpointFormat.ANTHROPIC) {
                tools.add(AICommandSkillSupport.createAnthropicMinecraftCommandSkillTool());
                tools.add(AICommandSkillSupport.createAnthropicManifestDivinePowerTool());
            } else {
                tools.add(AICommandSkillSupport.createOpenAiMinecraftCommandSkillTool());
                tools.add(AICommandSkillSupport.createOpenAiManifestDivinePowerTool());
            }
        }

        boolean effectiveUseStreaming = useStreaming && !shouldBufferPotentialActionReply(originalUserMessage, crossSessionMode, allowWorldActions);

        JsonObject requestBody = endpointFormat == LLMConfig.EndpointFormat.ANTHROPIC
                ? buildAnthropicChatRequestBody(model, forcedPrompt, currentPrompt, effectiveScopeId, includeConversationHistory,
                originalUserMessage, effectiveUseStreaming, effectiveTemperature, tools)
                : buildOpenAiChatRequestBody(model, forcedPrompt, currentPrompt, effectiveScopeId, includeConversationHistory,
                originalUserMessage, effectiveUseStreaming, effectiveTemperature, tools);

        HttpRequest.Builder requestBuilder = createRequestBuilder(endpoint, apiKey, provider, endpointFormat, effectiveUseStreaming);

        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                .build();

        if (effectiveUseStreaming) {
            return sendStreamingRequest(request, endpointFormat, currentPrompt, originalUserMessage, effectiveScopeId, effectiveAuthorityPlayerId, retryCount,
                    allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount, partialConsumer, outputLanguageCode, crossSessionMode, allowWorldActions);
        }

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            LLMConfig.markApiKeyValid();
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            AIResponseParsingSupport.ToolInvocation toolInvocation = endpointFormat == LLMConfig.EndpointFormat.ANTHROPIC
                                    ? AIResponseParsingSupport.extractAnthropicToolInvocation(json)
                                    : AIResponseParsingSupport.extractOpenAiToolInvocation(json);
                            String aiReply = endpointFormat == LLMConfig.EndpointFormat.ANTHROPIC
                                    ? AIResponseParsingSupport.extractAnthropicText(json, "")
                                    : AIResponseParsingSupport.extractOpenAiMessageText(json, "");

                            if (allowWorldActions && toolInvocation != null && AICommandSkillSupport.isSupportedToolName(toolInvocation.name())) {
                                return executeNamedToolAction(toolInvocation.name(), toolInvocation.arguments(), effectiveScopeId, effectiveAuthorityPlayerId, originalUserMessage, retryCount, allowTitleRefresh,
                                        includeConversationHistory, persistConversation, variationRetryCount,
                                        partialConsumer, useStreaming, outputLanguageCode, crossSessionMode);
                            } else if (allowWorldActions && aiReply != null && aiReply.contains("<invoke name=\"" + AICommandSkillSupport.TOOL_MANIFEST_DIVINE_POWER + "\">")) {
                                String commandToRun = extractXmlParameter(aiReply, "command");
                                String aiDialogue = extractXmlParameter(aiReply, "dialogue");
                                if (commandToRun != null) {
                                    return executeToolAction(commandToRun, aiDialogue != null ? aiDialogue : "Code altered.", effectiveScopeId, effectiveAuthorityPlayerId,
                                            originalUserMessage, retryCount, allowTitleRefresh,
                                            includeConversationHistory, persistConversation, variationRetryCount,
                                            partialConsumer, useStreaming, outputLanguageCode, crossSessionMode);
                                }
                            }

                            return finalizeTextReply(aiReply, currentPrompt, originalUserMessage, effectiveScopeId, effectiveAuthorityPlayerId, retryCount,
                                    allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount,
                                    partialConsumer, useStreaming, outputLanguageCode, crossSessionMode, allowWorldActions);

                        } catch (Exception e) {
                            LOGGER.error("Failed to parse AI response", e);
                            LOGGER.error("Raw response body: {}", response.body());
                            return CompletableFuture.completedFuture("Data stream disrupted... (" + e.toString() + ")");
                        }
                    } else {
                        if (isInvalidApiKeyResponse(response.statusCode(), response.body())) {
                            LLMConfig.markApiKeyInvalid();
                            reopenApiKeyInputScreen();
                            return CompletableFuture.completedFuture("§c" + Component.translatable("message.herobrine_companion.ai_config_invalid").getString());
                        }
                        return CompletableFuture.completedFuture("Connection to reality fading... (API Error: " + response.statusCode() + ")");
                    }
                })
                .exceptionally(e -> "...... (Network Error)");
    }

    private static HttpRequest.Builder createRequestBuilder(String endpoint, String apiKey, LLMConfig.Provider provider,
                                                            LLMConfig.EndpointFormat endpointFormat, boolean useStreaming) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json; charset=UTF-8");

        if (endpointFormat == LLMConfig.EndpointFormat.ANTHROPIC) {
            requestBuilder.header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION);
        } else {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        if (useStreaming) {
            requestBuilder.header("Accept", "text/event-stream");
        }

        if (provider == LLMConfig.Provider.OPENROUTER) {
            requestBuilder.header("X-Title", "Herobrine Companion");
        }
        return requestBuilder;
    }

    private static CompletableFuture<String> requestActorDialogueWithRetry(String systemPrompt, String userPrompt, String fallback,
                                                                           UUID conversationScopeId, String outputLanguageCode,
                                                                           int variationRetryCount) {
        if (LLMConfig.isSetupIncomplete() || LLMConfig.isKeyMissingOrInvalid()) {
            return CompletableFuture.completedFuture(fallback);
        }

        String apiKey = LLMConfig.aiApiKey;
        LLMConfig.Provider provider = LLMConfig.getProvider();
        String endpoint = LLMConfig.getResolvedEndpoint();
        LLMConfig.EndpointFormat endpointFormat = LLMConfig.getResolvedEndpointFormat();
        String model = LLMConfig.getResolvedModel();
        String languageCode = resolveOutputLanguageCode(outputLanguageCode);
        double temperature = Math.min(1.1D, Math.max(0.2D, LLMConfig.getConfiguredTemperature() + 0.05D));

        JsonObject requestBody = endpointFormat == LLMConfig.EndpointFormat.ANTHROPIC
                ? buildAnthropicSimpleDialogueRequestBody(model, systemPrompt, userPrompt, languageCode, temperature)
                : buildOpenAiSimpleDialogueRequestBody(model, systemPrompt, userPrompt, languageCode, temperature);

        HttpRequest request = createRequestBuilder(endpoint, apiKey, provider, endpointFormat, false)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            LLMConfig.markApiKeyValid();
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            String rawReply = endpointFormat == LLMConfig.EndpointFormat.ANTHROPIC
                                    ? AIResponseParsingSupport.extractAnthropicText(json, fallback)
                                    : AIResponseParsingSupport.extractOpenAiMessageText(json, fallback);
                            String cleanReply = sanitizeActorDialogueText(rawReply, fallback);
                            if (variationRetryCount < 1 && shouldRegenerateForRepetition(conversationScopeId, cleanReply)) {
                                String antiRepeatPrompt = userPrompt
                                        + "\nUse a clearly different opening and phrasing from your recent line."
                                        + " Keep the same scene and meaning.";
                                return requestActorDialogueWithRetry(systemPrompt, antiRepeatPrompt, fallback,
                                        conversationScopeId, languageCode, variationRetryCount + 1);
                            }
                            rememberRecentReply(conversationScopeId, cleanReply);
                            return CompletableFuture.completedFuture(cleanReply);
                        } catch (Exception e) {
                            LOGGER.warn("Failed to parse actor dialogue response", e);
                            return CompletableFuture.completedFuture(fallback);
                        }
                    }

                    if (isInvalidApiKeyResponse(response.statusCode(), response.body())) {
                        LLMConfig.markApiKeyInvalid();
                        reopenApiKeyInputScreen();
                    }
                    return CompletableFuture.completedFuture(fallback);
                })
                .exceptionally(ignored -> fallback);
    }

    private static JsonObject buildOpenAiLocalizationRequestBody(String model, String languageCode, String sourceText) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", false);
        requestBody.addProperty("temperature", 0.2D);
        requestBody.addProperty("top_p", 0.9D);
        requestBody.addProperty("max_tokens", Math.min(256, LLMConfig.getConfiguredMaxOutputTokens()));

        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", buildLocalizationSystemPrompt(languageCode));
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", sourceText);
        messages.add(userMessage);
        requestBody.add("messages", messages);
        return requestBody;
    }

    private static JsonObject buildOpenAiSimpleDialogueRequestBody(String model, String systemPrompt, String userPrompt,
                                                                   String outputLanguageCode, double temperature) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", false);
        requestBody.addProperty("temperature", temperature);
        requestBody.addProperty("top_p", Math.min(1.0D, Math.max(0.7D, LLMConfig.getConfiguredTopP())));
        requestBody.addProperty("presence_penalty", 0.35D);
        requestBody.addProperty("frequency_penalty", 0.45D);
        requestBody.addProperty("max_tokens", Math.min(80, LLMConfig.getConfiguredMaxOutputTokens()));

        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt + "\nRespond for locale '" + outputLanguageCode + "'.");
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);

        requestBody.add("messages", messages);
        return requestBody;
    }

    private static JsonObject buildAnthropicLocalizationRequestBody(String model, String languageCode, String sourceText) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", false);
        requestBody.addProperty("temperature", 0.2D);
        requestBody.addProperty("top_p", 0.9D);
        requestBody.addProperty("max_tokens", Math.min(256, LLMConfig.getConfiguredMaxOutputTokens()));
        requestBody.addProperty("system", buildLocalizationSystemPrompt(languageCode));

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", sourceText);
        messages.add(userMessage);
        requestBody.add("messages", messages);
        return requestBody;
    }

    private static JsonObject buildAnthropicSimpleDialogueRequestBody(String model, String systemPrompt, String userPrompt,
                                                                      String outputLanguageCode, double temperature) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", false);
        requestBody.addProperty("temperature", temperature);
        requestBody.addProperty("top_p", Math.min(1.0D, Math.max(0.7D, LLMConfig.getConfiguredTopP())));
        requestBody.addProperty("max_tokens", Math.min(80, LLMConfig.getConfiguredMaxOutputTokens()));
        requestBody.addProperty("system", systemPrompt + "\nRespond for locale '" + outputLanguageCode + "'.");

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);
        requestBody.add("messages", messages);
        return requestBody;
    }

    private static JsonObject buildOpenAiChatRequestBody(String model, String forcedPrompt, String currentPrompt, UUID conversationScopeId,
                                                         boolean includeConversationHistory, String originalUserMessage, boolean useStreaming,
                                                         double effectiveTemperature, JsonArray tools) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", useStreaming);
        requestBody.addProperty("temperature", effectiveTemperature);
        requestBody.addProperty("top_p", LLMConfig.getConfiguredTopP());
        requestBody.addProperty("presence_penalty", 0.35D);
        requestBody.addProperty("frequency_penalty", 0.45D);
        requestBody.addProperty("max_tokens", LLMConfig.getConfiguredMaxOutputTokens());

        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", forcedPrompt);
        messages.add(systemMessage);

        appendConversationHistoryMessages(messages, conversationScopeId, includeConversationHistory, forcedPrompt, currentPrompt, originalUserMessage);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", currentPrompt);
        messages.add(userMsg);

        requestBody.add("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            requestBody.addProperty("tool_choice", "auto");
            requestBody.add("tools", tools);
        }
        return requestBody;
    }

    private static JsonObject buildAnthropicChatRequestBody(String model, String forcedPrompt, String currentPrompt, UUID conversationScopeId,
                                                            boolean includeConversationHistory, String originalUserMessage, boolean useStreaming,
                                                            double effectiveTemperature, JsonArray tools) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", useStreaming);
        requestBody.addProperty("temperature", effectiveTemperature);
        requestBody.addProperty("top_p", LLMConfig.getConfiguredTopP());
        requestBody.addProperty("max_tokens", LLMConfig.getConfiguredMaxOutputTokens());
        requestBody.addProperty("system", forcedPrompt);

        JsonArray messages = new JsonArray();
        appendConversationHistoryMessages(messages, conversationScopeId, includeConversationHistory, forcedPrompt, currentPrompt, originalUserMessage);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", currentPrompt);
        messages.add(userMsg);

        requestBody.add("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            JsonObject toolChoice = new JsonObject();
            toolChoice.addProperty("type", "auto");
            requestBody.add("tool_choice", toolChoice);
            requestBody.add("tools", tools);
        }
        return requestBody;
    }

    private static void appendConversationHistoryMessages(JsonArray messages, UUID conversationScopeId, boolean includeConversationHistory,
                                                          String forcedPrompt, String currentPrompt, String originalUserMessage) {
        if (!includeConversationHistory) {
            return;
        }

        List<ConversationStore.ConversationMessageSnapshot> history = AIHistoryTokenSupport.trimConversationHistory(
                CONVERSATION_STORE.getActiveConversationMessages(conversationScopeId),
                AIHistoryTokenSupport.calculateHistoryTokenBudget(forcedPrompt, currentPrompt, originalUserMessage),
                LLMConfig.getEffectiveConversationHistoryMessageLimit()
        );
        for (ConversationStore.ConversationMessageSnapshot historyMsg : history) {
            JsonObject historyMessage = new JsonObject();
            historyMessage.addProperty("role", historyMsg.role());
            historyMessage.addProperty("content", historyMsg.content());
            messages.add(historyMessage);
        }
    }


    private static CompletableFuture<String> sendStreamingRequest(HttpRequest request, LLMConfig.EndpointFormat endpointFormat, String currentPrompt, String originalUserMessage,
                                                                  UUID conversationScopeId, UUID authorityPlayerUUID, int retryCount, boolean allowTitleRefresh,
                                                                  boolean includeConversationHistory, boolean persistConversation,
                                                                  int variationRetryCount, Consumer<String> partialConsumer,
                                                                  String outputLanguageCode,
                                                                  boolean crossSessionMode,
                                                                  boolean allowWorldActions) {
        return CompletableFuture.supplyAsync(() -> AIStreamingSupport.readStreamingResponse(CLIENT, request, endpointFormat, partialConsumer, LOGGER))
                .thenCompose(streamingResponse -> {
                    if (streamingResponse.statusCode == 200) {
                        LLMConfig.markApiKeyValid();
                        if (allowWorldActions && AICommandSkillSupport.isSupportedToolName(streamingResponse.toolName)
                                && streamingResponse.toolArguments != null && !streamingResponse.toolArguments.isBlank()) {
                            try {
                                JsonObject args = JsonParser.parseString(streamingResponse.toolArguments).getAsJsonObject();
                                return executeNamedToolAction(streamingResponse.toolName, args, conversationScopeId, authorityPlayerUUID, originalUserMessage, retryCount, allowTitleRefresh,
                                        includeConversationHistory, persistConversation, variationRetryCount,
                                        partialConsumer, true, outputLanguageCode, crossSessionMode);
                            } catch (Exception e) {
                                LOGGER.warn("Failed to parse streamed tool call arguments", e);
                            }
                        }

                        return finalizeTextReply(streamingResponse.reply, currentPrompt, originalUserMessage, conversationScopeId, authorityPlayerUUID, retryCount,
                                allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount,
                                partialConsumer, true, outputLanguageCode, crossSessionMode, allowWorldActions);
                    }

                    if (isInvalidApiKeyResponse(streamingResponse.statusCode, streamingResponse.errorBody)) {
                        LLMConfig.markApiKeyInvalid();
                        reopenApiKeyInputScreen();
                        return CompletableFuture.completedFuture("§c" + Component.translatable("message.herobrine_companion.ai_config_invalid").getString());
                    }
                    return CompletableFuture.completedFuture("Connection to reality fading... (API Error: " + streamingResponse.statusCode + ")");
                })
                .exceptionally(e -> "...... (Network Error)");
    }


    private static CompletableFuture<String> finalizeTextReply(String aiReply, String currentPrompt, String originalUserMessage,
                                                               UUID conversationScopeId, UUID authorityPlayerUUID, int retryCount, boolean allowTitleRefresh,
                                                               boolean includeConversationHistory, boolean persistConversation,
                                                               int variationRetryCount, Consumer<String> partialConsumer,
                                                               boolean useStreaming, String outputLanguageCode,
                                                               boolean crossSessionMode,
                                                               boolean allowWorldActions) {
        String cleanReply = LegacyFormattingText.normalize((aiReply == null ? "" : aiReply).replaceAll("<[^>]*>", "").trim());
        if (cleanReply.isEmpty()) cleanReply = "(Falls into a deep silence...)";
        final String finalizedReply = cleanReply;

        if (variationRetryCount < 1 && shouldRegenerateForRepetition(conversationScopeId, finalizedReply)) {
            String antiRepeatPrompt = currentPrompt
                    + "\n[ANTI-REPETITION]: Your previous draft sounds too similar to your recent replies."
                    + " Rewrite it with a different opening, different wording, and a fresh sentence structure."
                    + " Keep the same meaning, keep it natural, and do not mention this instruction.";
            return chatWithRetry(antiRepeatPrompt, originalUserMessage, conversationScopeId, authorityPlayerUUID, retryCount,
                    allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount + 1,
                    partialConsumer, useStreaming, outputLanguageCode, crossSessionMode, allowWorldActions);
        }

        if (allowWorldActions && !crossSessionMode && hasUnbackedWorldActionClaim(originalUserMessage, finalizedReply)) {
            if (retryCount < 2) {
                String toolRetryPrompt = currentPrompt
                        + "\n[TOOL-CALL REQUIRED]: Your previous draft claimed that a Minecraft/world action had already happened, "
                        + "but no tool call was emitted. Text-only replies cannot change the world. "
                        + "If the player requested an action, call minecraft_command_skill with valid typed parameters now. "
                        + "If you cannot or should not act, reply in the player's language without claiming anything happened.";
                return chatWithRetry(toolRetryPrompt, originalUserMessage, conversationScopeId, authorityPlayerUUID, retryCount + 1,
                        allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount,
                        partialConsumer, useStreaming, outputLanguageCode, crossSessionMode, true);
            }
            return CompletableFuture.completedFuture(completeReply(buildNoActionFallback(outputLanguageCode),
                    conversationScopeId, originalUserMessage, persistConversation, allowTitleRefresh));
        }

        if (allowWorldActions && !crossSessionMode && shouldRetryEagerTextOnlyAction(originalUserMessage, finalizedReply)) {
            if (retryCount < 2) {
                String toolRetryPrompt = currentPrompt
                        + "\n[EAGER COMMAND MODE]: The player's message signaled an actionable Minecraft/world intent, "
                        + "but your previous reply was text-only. In eager mode, proactively choose the closest safe minecraft_command_skill action and call it now. "
                        + "If required parameters are truly missing, ask one concise clarification. If the action is unsafe/impossible, decline without claiming action.";
                return chatWithRetry(toolRetryPrompt, originalUserMessage, conversationScopeId, authorityPlayerUUID, retryCount + 1,
                        allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount,
                        partialConsumer, useStreaming, outputLanguageCode, crossSessionMode, true);
            }
            return CompletableFuture.completedFuture(completeReply(buildNoActionFallback(outputLanguageCode),
                    conversationScopeId, originalUserMessage, persistConversation, allowTitleRefresh));
        }

        String inferredAction = (crossSessionMode || !allowWorldActions) ? null : inferReplyDrivenAction(originalUserMessage, finalizedReply);
        if (inferredAction != null) {
            return executeCommandWithFeedback(inferredAction, authorityPlayerUUID)
                    .exceptionally(ignored -> false)
                    .thenApply(ignored -> completeReply(finalizedReply, conversationScopeId, originalUserMessage, persistConversation, allowTitleRefresh));
        }

        return CompletableFuture.completedFuture(completeReply(finalizedReply, conversationScopeId, originalUserMessage, persistConversation, allowTitleRefresh));
    }

    private static String completeReply(String reply, UUID conversationScopeId, String originalUserMessage,
                                        boolean persistConversation, boolean allowTitleRefresh) {
        String normalizedReply = LegacyFormattingText.normalize(reply);
        rememberRecentReply(conversationScopeId, normalizedReply);
        if (persistConversation) {
            addExchangeToConversation(conversationScopeId, originalUserMessage, normalizedReply, allowTitleRefresh);
        }
        return normalizedReply;
    }

    private static CompletableFuture<String> executeToolAction(String commandToRun, String aiDialogue,
                                                               UUID conversationScopeId, UUID authorityPlayerUUID, String originalUserMessage,
                                                               int retryCount, boolean allowTitleRefresh, boolean includeConversationHistory,
                                                               boolean persistConversation, int variationRetryCount,
                                                               Consumer<String> partialConsumer, boolean useStreaming,
                                                               String outputLanguageCode,
                                                               boolean crossSessionMode) {
        return executeCommandWithFeedback(commandToRun, authorityPlayerUUID).thenCompose(success -> {
            if (success) {
                return CompletableFuture.completedFuture(completeReply(aiDialogue, conversationScopeId, originalUserMessage, persistConversation, allowTitleRefresh));
            } else {
                if (retryCount < 2) {
                    String systemRetryPrompt = "[System Rejection]: Command /" + commandToRun + " failed. Reason: Syntax error or Cheats are disabled. Do not alter code, just reply gently!";
                    return chatWithRetry(systemRetryPrompt, originalUserMessage, conversationScopeId, authorityPlayerUUID, retryCount + 1,
                            allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount,
                            partialConsumer, useStreaming, outputLanguageCode, crossSessionMode, true);
                } else {
                    String failText = "(Gentle sigh) I failed to alter the underlying code, the world laws rejected me...";
                    return CompletableFuture.completedFuture(completeReply(failText, conversationScopeId, originalUserMessage, persistConversation, allowTitleRefresh));
                }
            }
        });
    }

    private static CompletableFuture<String> executeNamedToolAction(String toolName, JsonObject args,
                                                                    UUID conversationScopeId, UUID authorityPlayerUUID, String originalUserMessage,
                                                                    int retryCount, boolean allowTitleRefresh, boolean includeConversationHistory,
                                                                    boolean persistConversation, int variationRetryCount,
                                                                    Consumer<String> partialConsumer, boolean useStreaming,
                                                                    String outputLanguageCode,
                                                                    boolean crossSessionMode) {
        if (AICommandSkillSupport.TOOL_MINECRAFT_COMMAND_SKILL.equals(toolName)) {
            String commandToRun = AICommandSkillSupport.buildMinecraftSkillCommand(args, ACTION_SUMMON_TO_PLAYER, ACTION_TELEPORT_TO_HERO, ACTION_TOGGLE_COMPANION, ACTION_MASSIVE_LIGHTNING);
            String dialogue = getOptionalString(args, "dialogue", "Reality bends to a cleaner command.");
            if (commandToRun == null || commandToRun.isBlank()) {
                if (retryCount < 2) {
                    String systemRetryPrompt = "[System Rejection]: minecraft_command_skill received invalid action/parameters. "
                            + "Use one valid action enum and include its required parameter fields; do not write raw /commands unless absolutely necessary. "
                            + "Rejected action='" + getOptionalString(args, "action", "") + "'.";
                    return chatWithRetry(systemRetryPrompt, originalUserMessage, conversationScopeId, authorityPlayerUUID, retryCount + 1,
                            allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount,
                            partialConsumer, useStreaming, outputLanguageCode, crossSessionMode, true);
                }
                return CompletableFuture.completedFuture("(The command lattice rejects that malformed invocation.)");
            }
            return executeToolAction(commandToRun, dialogue, conversationScopeId, authorityPlayerUUID, originalUserMessage, retryCount, allowTitleRefresh,
                    includeConversationHistory, persistConversation, variationRetryCount, partialConsumer, useStreaming, outputLanguageCode, crossSessionMode);
        }

        return executeToolAction(getOptionalString(args, "command", ""),
                getOptionalString(args, "dialogue", "Code altered."), conversationScopeId, authorityPlayerUUID, originalUserMessage, retryCount,
                allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount, partialConsumer, useStreaming, outputLanguageCode, crossSessionMode);
    }

    private static UUID buildCrossSessionScopeId(UUID conversationScopeId, UUID authorityPlayerUUID) {
        UUID scope = conversationScopeId != null ? conversationScopeId : authorityPlayerUUID;
        UUID authority = authorityPlayerUUID != null ? authorityPlayerUUID : scope;
        return UUID.nameUUIDFromBytes(("hb-cross-session-scope:" + scope + ":" + authority).getBytes(StandardCharsets.UTF_8));
    }

    private static String buildLocalizationSystemPrompt(String targetLanguageCode) {
        return "You are a translation/localization function for Herobrine dialogue. "
                + "Translate or restate the user's single dialogue line into the language for locale code '" + resolveOutputLanguageCode(targetLanguageCode) + "'. "
                + "Preserve the original meaning, tone, menace, and brevity. "
                + "Do not explain, annotate, or add quotes. Only output the localized dialogue line itself.";
    }

    private static String sanitizeLocalizedText(String localizedText, String fallbackText) {
        String sanitized = LegacyFormattingText.normalize((localizedText == null ? "" : localizedText)
                .replaceAll("<[^>]*>", "")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim());
        return sanitized.isEmpty() ? LegacyFormattingText.normalize(fallbackText) : sanitized;
    }

    private static String sanitizeActorDialogueText(String text, String fallbackText) {
        String sanitized = sanitizeLocalizedText(text, fallbackText)
                .replaceAll("^[\"'`]+|[\"'`]+$", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (sanitized.isEmpty()) {
            return fallbackText == null || fallbackText.isBlank() ? "..." : fallbackText.trim();
        }
        return sanitized;
    }

    private static String resolveOutputLanguageCode(String outputLanguageCode) {
        String normalized = normalizeLanguageCode(outputLanguageCode);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.options == null ? "en_us" : normalizeLanguageCode(mc.options.languageCode);
    }

    private static String normalizeLanguageCode(String rawLanguageCode) {
        if (rawLanguageCode == null) {
            return "";
        }
        String normalized = rawLanguageCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized;
    }


    private static String getOptionalString(JsonObject object, String propertyName, String fallback) {
        if (object == null || !object.has(propertyName)) {
            return fallback;
        }
        JsonElement element = object.get(propertyName);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }



    private static CompletableFuture<Boolean> executeCommandWithFeedback(String command, UUID targetPlayerUUID) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { future.complete(false); return future; }

        mc.tell(() -> {
            if (!isPermissionBypassAction(command) && !mc.player.hasPermissions(2)) {
                mc.gui.getChat().addMessage(Component.literal("§4[System Block] Cheats are disabled in this world, Herobrine's physical interference is revoked!"));
                future.complete(false); return;
            }
            if (!isSafeGeneratedCommand(command)) {
                mc.gui.getChat().addMessage(Component.literal("§4[System Block] AI command blocked because it referenced modded content. Use manual commands if you intentionally want modded resources."));
                future.complete(false); return;
            }

            if (ACTION_TOGGLE_COMPANION.equals(command)) {
                if (mc.level != null) {
                    for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                        if (entity instanceof com.whitecloud233.modid.herobrine_companion.entity.HeroEntity) {
                            com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToServer(new com.whitecloud233.modid.herobrine_companion.network.ToggleCompanionPacket(entity.getId()));
                            future.complete(true); return;
                        }
                    }
                }
                future.complete(false);

            } else if (ACTION_MASSIVE_LIGHTNING.equals(command)) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        try {
                            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer != null) {
                                CommandSourceStack godSource = serverPlayer.createCommandSourceStack().withPermission(4);
                                for (int i = 0; i < 20; i++) {
                                    int offsetX = (int) (Math.random() * 30 - 15), offsetZ = (int) (Math.random() * 30 - 15);
                                    mc.getSingleplayerServer().getCommands().performPrefixedCommand(godSource, String.format("execute at @s run summon lightning_bolt ~%d ~ ~%d", offsetX, offsetZ));
                                }
                                future.complete(true);
                            } else future.complete(false);
                        } catch (Exception e) { future.complete(false); }
                    });
                } else future.complete(false);

                    } else if (com.whitecloud233.modid.herobrine_companion.network.HeroPunishmentPacket.ACTION_KILL_PLAYER.equals(command)) {
                        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                            var server = mc.getSingleplayerServer();
                            server.execute(() -> {
                                try {
                                    ServerPlayer serverPlayer = server.getPlayerList().getPlayer(targetPlayerUUID);
                                    if (serverPlayer == null) { future.complete(false); return; }

                                    if (serverPlayer.isAlive()) {
                                        serverPlayer.kill();
                                    }
                                    future.complete(true);
                                } catch (Exception e) { future.complete(false); }
                            });
                        } else {
                            com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToServer(
                                    new com.whitecloud233.modid.herobrine_companion.network.HeroPunishmentPacket(command)
                            );
                            future.complete(true);
                        }

                    } else if (com.whitecloud233.modid.herobrine_companion.network.HeroPunishmentPacket.ACTION_KICK_PLAYER.equals(command)) {
                        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                            var server = mc.getSingleplayerServer();
                            server.execute(() -> {
                                try {
                                    ServerPlayer serverPlayer = server.getPlayerList().getPlayer(targetPlayerUUID);
                                    if (serverPlayer == null) { future.complete(false); return; }

                                    serverPlayer.connection.disconnect(Component.literal("Herobrine has cast you out."));
                                    future.complete(true);
                                } catch (Exception e) { future.complete(false); }
                            });
                        } else {
                            com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToServer(
                                    new com.whitecloud233.modid.herobrine_companion.network.HeroPunishmentPacket(command)
                            );
                            future.complete(true);
                        }

                    } else if (ACTION_SUMMON_TO_PLAYER.equals(command) || command.startsWith("tp @e[type=herobrine_companion:hero")) {
                // 【行为1：AI 传送到玩家身边】
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        try {
                            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer == null) { future.complete(false); return; }

                            // 委托统一的召唤工具类 (处理跨维度、重新生成等逻辑)
                            ServerLevel targetLevel = serverPlayer.serverLevel();
                            boolean success = com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem.performSummonOrTeleport(
                                    targetLevel, serverPlayer, serverPlayer.position()
                            );
                            future.complete(success);
                        } catch (Exception e) { future.complete(false); }
                    });
                } else {
                    // 多人游戏下发送召唤数据包，触发 HeroSummonItem 的跨维度拉取逻辑
                    com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToServer(
                            new com.whitecloud233.modid.herobrine_companion.network.SummonHeroPacket()
                    );
                    future.complete(true);
                }

            } else if (ACTION_TELEPORT_TO_HERO.equals(command) || command.startsWith("tp @s @e[type=herobrine_companion:hero")) {
                // 【行为2：玩家传送到 AI 身边】
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        try {
                            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer == null) { future.complete(false); return; }

                            // 调用 SourceFlowItem 的传送代码
                            boolean success = com.whitecloud233.modid.herobrine_companion.item.SourceFlowItem.performTeleportToHero(serverPlayer);
                            future.complete(success);
                        } catch (Exception e) { future.complete(false); }
                    });
                } else {
                    // 多人游戏下发送传送到Hero身边的数据包
                    com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToServer(
                            new com.whitecloud233.modid.herobrine_companion.network.TeleportToHeroPacket()
                    );
                    future.complete(true);
                }

            } else if (HeroAIActionPacket.isSupportedAction(command)) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        try {
                            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(targetPlayerUUID);
                            future.complete(serverPlayer != null && HeroAIActionPacket.performAction(serverPlayer, command));
                        } catch (Exception e) {
                            future.complete(false);
                        }
                    });
                } else {
                    com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToServer(
                            new HeroAIActionPacket(command)
                    );
                    future.complete(true);
                }

            } else if (command.contains("gamemode creative") || command.contains("gamemode 1")) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        if (serverPlayer != null) { serverPlayer.setGameMode(net.minecraft.world.level.GameType.CREATIVE); future.complete(true); } else future.complete(false);
                    });
                } else future.complete(false);
            } else if (command.contains("gamemode survival") || command.contains("gamemode 0")) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        if (serverPlayer != null) { serverPlayer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); future.complete(true); } else future.complete(false);
                    });
                } else future.complete(false);
            } else if (command.contains("gamemode spectator") || command.contains("gamemode 3")) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        if (serverPlayer != null) { serverPlayer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR); future.complete(true); } else future.complete(false);
                    });
                } else future.complete(false);
            } else {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        try {
                            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer != null) {
                                CommandSourceStack godSource = serverPlayer.createCommandSourceStack().withPermission(4);
                                mc.getSingleplayerServer().getCommands().performPrefixedCommand(godSource, command);
                                future.complete(true);
                            } else future.complete(false);
                        } catch (Exception e) { future.complete(false); }
                    });
                } else {
                    mc.player.connection.sendCommand(command);
                    future.complete(true);
                }
            }
        });
        return future;
    }

    private static boolean isPermissionBypassAction(String command) {
        return ACTION_TOGGLE_COMPANION.equals(command)
                || HeroAIActionPacket.isSupportedAction(command)
                || isExtremePunishmentAction(command);
    }

    private static boolean isSafeGeneratedCommand(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String normalized = normalizeActionInferenceText(command);
        if (ACTION_SUMMON_TO_PLAYER.equals(normalized)
                || ACTION_TELEPORT_TO_HERO.equals(normalized)
                || ACTION_TOGGLE_COMPANION.equals(normalized)
                || ACTION_MASSIVE_LIGHTNING.equals(normalized)
                || HeroAIActionPacket.isSupportedAction(normalized)
                || isExtremePunishmentAction(normalized)) {
            return true;
        }
        if (normalized.startsWith("tp @e[type=" + HerobrineCompanion.MODID + ":hero")
                || normalized.startsWith("tp @s @e[type=" + HerobrineCompanion.MODID + ":hero")) {
            return true;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<![a-z0-9_.-])([a-z0-9_.-]+):[a-z0-9_/.-]+")
                .matcher(normalized);
        while (matcher.find()) {
            if (!VANILLA_NAMESPACE.equals(matcher.group(1))) {
                return false;
            }
        }
        return true;
    }

    private static boolean shouldBufferPotentialActionReply(String originalUserMessage, boolean crossSessionMode, boolean allowWorldActions) {
        if (crossSessionMode || !allowWorldActions) {
            return false;
        }
        return LLMConfig.isCommandEagerMode()
                ? isEagerWorldActionIntent(originalUserMessage)
                : isLikelyWorldActionRequest(originalUserMessage);
    }

    private static boolean hasUnbackedWorldActionClaim(String originalUserMessage, String cleanReply) {
        String request = normalizeActionInferenceText(originalUserMessage);
        String reply = normalizeActionInferenceText(cleanReply);
        if (request.isEmpty() || reply.isEmpty()
                || !(isLikelyWorldActionRequest(request) || isEagerWorldActionIntent(request))) {
            return false;
        }
        if (containsNoActionQualifier(reply)) {
            return false;
        }

        boolean completedTone = containsAny(reply,
                "已", "已经", "完成", "搞定", "做好了", "办好了", "执行了", "改好了", "done",
                "completed", "executed", "i have", "i've", "it is done", "as you asked");
        boolean actionClaim = containsAny(reply,
                "指令", "命令", "传送", "召唤", "生成", "给予", "给你", "清除", "清空", "击杀", "杀掉", "踢出",
                "设置", "改为", "改变", "天气", "时间", "难度", "模式", "方块", "填充", "放置", "效果", "药水",
                "附魔", "经验", "边界", "白名单", "封禁", "解封", "command", "teleport", "summon", "spawned",
                "gave", "given", "cleared", "killed", "kicked", "set ", "changed", "weather", "time", "gamemode",
                "difficulty", "effect", "enchanted", "filled", "placed", "worldborder", "whitelist", "banned");
        return completedTone && actionClaim;
    }

    private static boolean shouldRetryEagerTextOnlyAction(String originalUserMessage, String cleanReply) {
        if (!LLMConfig.isCommandEagerMode()) {
            return false;
        }
        String request = normalizeActionInferenceText(originalUserMessage);
        String reply = normalizeActionInferenceText(cleanReply);
        if (request.isEmpty() || reply.isEmpty() || !isEagerWorldActionIntent(request)) {
            return false;
        }
        return !containsNoActionQualifier(reply) && !containsClarificationOrSafetyQualifier(reply);
    }

    private static boolean containsClarificationOrSafetyQualifier(String reply) {
        if (reply.contains("?") || reply.contains("？")) {
            return true;
        }
        return containsAny(reply,
                "需要", "请告诉", "先告诉", "确认", "哪个", "哪一个", "什么", "多少", "坐标", "目标",
                "无法", "不能", "不会", "不应该", "危险", "权限", "缺少", "need", "needs", "tell me",
                "which", "what", "how many", "coordinate", "target", "specify", "clarify", "confirm",
                "cannot", "can't", "won't", "unsafe", "permission", "missing");
    }

    private static boolean isEagerWorldActionIntent(String text) {
        String normalized = normalizeActionInferenceText(text);
        if (normalized.isEmpty()) {
            return false;
        }
        if (containsAny(normalized,
                "怎么", "如何", "教程", "语法", "参数", "解释", "说明", "什么意思",
                "what is", "how to", "syntax", "explain", "tutorial", "parameter")) {
            return false;
        }
        if (isLikelyWorldActionRequest(normalized)) {
            return true;
        }
        return containsAny(normalized,
                "我想要", "想要", "需要", "缺", "没有", "不够", "拿不到", "找不到", "在哪", "在哪里",
                "带我", "过来", "回来", "回家", "基地", "村庄", "矿洞", "传过去", "太远", "迷路", "卡住",
                "出不去", "救我", "帮我", "保护我", "太黑", "看不见", "天黑", "下雨", "雨太", "雷",
                "太危险", "怪太多", "打不过", "血少", "快死", "饿", "没食物", "没工具", "没有装备",
                "想飞", "钻石", "铁", "金", "绿宝石", "木头", "石头", "水", "岩浆", "火", "着火",
                "中毒", "缓慢", "虚弱", "挖不动", "经验不够", "附魔", "升级", "修复", "清理", "垃圾",
                "背包满", "need ", "needs ", "want ", "wants ", "wish ", "lack ", "lacking ", "out of ",
                "missing ", "not enough", "can't find", "cannot find", "where is", "where are", "bring me",
                "take me", "come here", "go home", "home base", "village", "mineshaft", "lost", "stuck",
                "trapped", "help me", "save me", "protect me", "too dark", "can't see", "cannot see",
                "night", "rain", "storm", "thunder", "dangerous", "too many mobs", "low health", "dying",
                "hungry", "no food", "no tool", "no tools", "no armor", "want to fly", "diamond", "diamonds",
                "iron", "gold", "emerald", "wood", "stone", "water", "lava", "fire", "burning", "poison",
                "slowness", "weakness", "can't mine", "cannot mine", "need xp", "enchant", "repair",
                "clean up", "inventory full");
    }

    private static boolean isLikelyWorldActionRequest(String text) {
        String normalized = normalizeActionInferenceText(text);
        if (normalized.isEmpty()) {
            return false;
        }
        if (containsAny(normalized,
                "怎么", "如何", "教程", "语法", "参数", "解释", "说明", "what is", "how to", "syntax", "explain")) {
            return false;
        }
        return containsAny(normalized,
                "给我", "给予", "清除", "清空", "传送", "tp", "召唤", "生成", "杀", "踢", "设置", "改成", "改为",
                "切换", "下雨", "天晴", "雷暴", "时间", "天气", "难度", "模式", "放置", "填充", "方块", "定位",
                "播放", "粒子", "效果", "药水", "附魔", "经验", "边界", "白名单", "封禁", "解封", "保存", "重载",
                "give me", "give ", "clear ", "teleport", "tp ", "summon", "spawn", "kill", "kick", "set ",
                "change ", "switch ", "weather", "time", "difficulty", "gamemode", "place ", "fill ", "setblock",
                "locate", "playsound", "particle", "effect", "enchant", "xp", "experience", "worldborder",
                "whitelist", "ban ", "pardon", "reload", "stop server");
    }

    private static boolean containsNoActionQualifier(String reply) {
        return containsAny(reply,
                "没有执行", "未执行", "并未执行", "无法执行", "不能执行", "不会执行", "执行失败", "失败",
                "没有调用", "未调用", "不能改变", "无法改变", "did not", "didn't", "not execute", "not executed",
                "no command", "without executing", "failed", "cannot", "can't", "unable", "i won't", "i would");
    }

    private static String buildNoActionFallback(String outputLanguageCode) {
        String languageCode = resolveOutputLanguageCode(outputLanguageCode);
        if (languageCode.startsWith("zh")) {
            return "我没有执行任何指令；世界没有被改变。";
        }
        return "No command was executed; the world was not changed.";
    }

    private static String inferReplyDrivenAction(String originalUserMessage, String cleanReply) {
        String request = normalizeActionInferenceText(originalUserMessage);
        String reply = normalizeActionInferenceText(cleanReply);
        if (request.isEmpty() || reply.isEmpty()) {
            return null;
        }

        if (isChallengeRequest(request) && isChallengeAcceptance(reply)) {
            return HeroAIActionPacket.ACTION_CHALLENGE_ACCEPT;
        }
        if (isFlyRequest(request) && isFlyAffirmation(reply)) {
            return HeroAIActionPacket.ACTION_HERO_FLY_UP;
        }
        if (isLandRequest(request) && isLandAffirmation(reply)) {
            return HeroAIActionPacket.ACTION_HERO_LAND;
        }
        return null;
    }

    private static String normalizeActionInferenceText(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static boolean isChallengeRequest(String text) {
        return containsAny(text, "挑战", "决斗", "试炼", "单挑", "比试", "challenge", "duel", "fight me", "battle me");
    }

    private static boolean isChallengeAcceptance(String text) {
        if (containsAny(text, "不接受", "拒绝", "can't", "cannot", "won't", "refuse", "decline")) {
            return false;
        }
        return containsAny(text, "接受", "奉陪", "来吧", "开始吧", "应战", "challenge accepted", "i accept", "very well", "let us fight", "come then");
    }

    private static boolean isFlyRequest(String text) {
        return containsAny(text, "飞", "飞起来", "升空", "漂浮", "悬浮", "腾空", "fly", "levitate", "float", "ascend", "rise up");
    }

    private static boolean isFlyAffirmation(String text) {
        if (containsAny(text, "不飞", "不会飞", "不能飞", "can't fly", "cannot fly", "won't fly")) {
            return false;
        }
        return containsAny(text, "飞起来", "升空", "漂浮", "悬浮", "腾空", "在空中", "flying", "levitating", "levitate", "rise", "ascend", "airborne");
    }

    private static boolean isLandRequest(String text) {
        return containsAny(text, "落下", "下来", "降落", "着陆", "落地", "land", "descend", "come down");
    }

    private static boolean isLandAffirmation(String text) {
        if (containsAny(text, "不下去", "不降落", "won't land", "won't come down", "cannot descend")) {
            return false;
        }
        return containsAny(text, "落地", "降落", "着陆", "下来", "回到地面", "landing", "landed", "descend", "come down");
    }

    private static boolean containsAny(String text, String... needles) {
        if (text == null || text.isEmpty() || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExtremePunishmentAction(String command) {
        return com.whitecloud233.modid.herobrine_companion.network.HeroPunishmentPacket.ACTION_KILL_PLAYER.equals(command)
                || com.whitecloud233.modid.herobrine_companion.network.HeroPunishmentPacket.ACTION_KICK_PLAYER.equals(command);
    }

    private static void addExchangeToConversation(UUID playerUUID, String userContent, String assistantContent, boolean allowTitleRefresh) {
        CONVERSATION_STORE.appendMessage(playerUUID, "user", userContent, allowTitleRefresh);
        CONVERSATION_STORE.appendMessage(playerUUID, "assistant", assistantContent, false);
    }
    private static void rememberRecentReply(UUID playerUUID, String reply) {
        if (playerUUID == null || reply == null || reply.isBlank()) {
            return;
        }

        String normalized = normalizeForRepeatCheck(reply);
        if (normalized.isEmpty()) {
            return;
        }

        RECENT_REPLIES.compute(playerUUID, (uuid, existing) -> {
            Deque<String> deque = existing == null ? new ArrayDeque<>() : existing;
            deque.addLast(normalized);
            while (deque.size() > MAX_RECENT_REPLIES) {
                deque.removeFirst();
            }
            return deque;
        });
    }

    private static boolean shouldRegenerateForRepetition(UUID playerUUID, String reply) {
        if (playerUUID == null || reply == null || reply.isBlank()) {
            return false;
        }

        Deque<String> recentReplies = RECENT_REPLIES.get(playerUUID);
        if (recentReplies == null || recentReplies.isEmpty()) {
            return false;
        }

        String normalizedReply = normalizeForRepeatCheck(reply);
        if (normalizedReply.isEmpty()) {
            return false;
        }

        for (String previous : recentReplies) {
            if (isLikelyRepeatedReply(previous, normalizedReply)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLikelyRepeatedReply(String previous, String current) {
        if (previous == null || current == null || previous.isEmpty() || current.isEmpty()) {
            return false;
        }
        if (previous.equals(current)) {
            return true;
        }
        if (previous.length() >= 10 && current.length() >= 10 && (previous.contains(current) || current.contains(previous))) {
            return true;
        }

        Set<String> previousTokens = tokenizeForRepeatCheck(previous);
        Set<String> currentTokens = tokenizeForRepeatCheck(current);
        if (previousTokens.isEmpty() || currentTokens.isEmpty()) {
            return false;
        }

        Set<String> intersection = new HashSet<>(previousTokens);
        intersection.retainAll(currentTokens);
        Set<String> union = new HashSet<>(previousTokens);
        union.addAll(currentTokens);
        double similarity = union.isEmpty() ? 0.0D : (double) intersection.size() / (double) union.size();
        return similarity >= 0.82D;
    }

    private static String normalizeForRepeatCheck(String text) {
        return LegacyFormattingText.stripCodes(text).toLowerCase(Locale.ROOT)
                .replaceAll("§.", "")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("[\\p{Punct}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Set<String> tokenizeForRepeatCheck(String text) {
        String normalized = normalizeForRepeatCheck(text);
        if (normalized.isEmpty()) {
            return Set.of();
        }

        Set<String> tokens = new HashSet<>();
        for (String token : normalized.split(" ")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    public static void clearHistory(UUID playerUUID) {
        CONVERSATION_STORE.clearActiveConversation(playerUUID);
        clearTransientHistory(playerUUID);
    }

    public static ConversationStore.ConversationSummary createFreshConversation(UUID playerUUID) {
        clearTransientHistory(playerUUID);
        return CONVERSATION_STORE.createConversation(playerUUID);
    }

    public static void clearTransientHistory(UUID playerUUID) {
        if (playerUUID == null) {
            return;
        }
        RECENT_REPLIES.remove(playerUUID);
    }


    private static String extractXmlParameter(String xml, String paramName) {
        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("name=\"" + paramName + "\"[^>]*>([\\s\\S]*?)</parameter>").matcher(xml);
            if (matcher.find()) return matcher.group(1).trim();
        } catch (Exception e) {} return null;
    }

    private static boolean isInvalidApiKeyResponse(int statusCode, String responseBody) {
        if (statusCode == 401 || statusCode == 403) {
            return true;
        }
        if (responseBody == null || responseBody.isEmpty()) {
            return false;
        }

        String normalizedBody = responseBody.toLowerCase(Locale.ROOT);
        return normalizedBody.contains("invalid api key")
                || normalizedBody.contains("incorrect api key")
                || normalizedBody.contains("invalid_api_key")
                || normalizedBody.contains("api key not valid")
                || normalizedBody.contains("authentication failed")
                || normalizedBody.contains("unauthorized");
    }

    private static void reopenApiKeyInputScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.setScreen(new com.whitecloud233.modid.herobrine_companion.config.ApiKeyInputScreen(new ChatScreen(""))));
    }
}
