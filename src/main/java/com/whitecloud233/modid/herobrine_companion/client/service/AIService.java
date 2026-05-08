package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.modid.herobrine_companion.network.HeroAIActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;

public class AIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AIService.class);
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ConversationStore CONVERSATION_STORE = ConversationStore.getInstance();
    private static final Map<UUID, Deque<String>> RECENT_REPLIES = new ConcurrentHashMap<>();
    private static final int MAX_RECENT_REPLIES = 6;
    private static final int MAX_SERVER_PLAYERS_IN_PROMPT = 12;
    private static final int MAX_NEARBY_PLAYERS_IN_PROMPT = 6;
    private static final double NEARBY_PLAYER_DETAIL_RADIUS = 96.0D;
    private static final String ACTION_TOGGLE_COMPANION = "action:toggle_companion";
    private static final String ACTION_MASSIVE_LIGHTNING = "action:massive_lightning";
    private static final String ACTION_SUMMON_TO_PLAYER = "action:summon_to_player";
    private static final String ACTION_TELEPORT_TO_HERO = "action:teleport_to_hero";
    private static final String TOOL_MANIFEST_DIVINE_POWER = "manifest_divine_power";
    private static final String TOOL_MINECRAFT_COMMAND_SKILL = "minecraft_command_skill";
    private static final int MAX_SKILL_GIVE_COUNT = 64;
    private static final int MAX_SKILL_SUMMON_DISTANCE = 20;

    public static CompletableFuture<String> chat(String userMessage, UUID playerUUID) {
        return chat(userMessage, playerUUID, null);
    }

    public static CompletableFuture<String> chat(String userMessage, UUID playerUUID, Consumer<String> partialConsumer) {
        return chatWithRetry(userMessage, userMessage, playerUUID, 0, true, true, true, 0,
                partialConsumer, LLMConfig.isStreamingEnabled());
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
        return chatWithRetry(currentPrompt, historyLog, playerUUID, 0, false, false, false, 0,
                null, LLMConfig.isStreamingEnabled());
    }

    private static CompletableFuture<String> chatWithRetry(String currentPrompt, String originalUserMessage, UUID playerUUID, int retryCount,
                                                           boolean allowTitleRefresh, boolean includeConversationHistory,
                                                           boolean persistConversation, int variationRetryCount,
                                                           Consumer<String> partialConsumer, boolean useStreaming) {
        String apiKey = LLMConfig.aiApiKey;
        LLMConfig.Provider provider = LLMConfig.getProvider();
        String endpoint = LLMConfig.getResolvedEndpoint();
        String model = LLMConfig.getResolvedModel();
        String systemPrompt = LLMConfig.aiSystemPrompt;
        String langCode = Minecraft.getInstance().options.languageCode;

        if (LLMConfig.isKeyMissing()) {
            return CompletableFuture.completedFuture("§c" + Component.translatable("message.herobrine_companion.ai_config_missing").getString());
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", useStreaming);
        requestBody.addProperty("temperature", Math.min(2.0D, LLMConfig.getConfiguredTemperature() + (includeConversationHistory ? 0.0D : 0.1D)));
        requestBody.addProperty("top_p", LLMConfig.getConfiguredTopP());
        requestBody.addProperty("presence_penalty", 0.35D);
        requestBody.addProperty("frequency_penalty", 0.45D);
        requestBody.addProperty("max_tokens", LLMConfig.getConfiguredMaxOutputTokens());

        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");

        String style = com.whitecloud233.modid.herobrine_companion.config.Config.aiLanguageStyle;

        String forcedPrompt = systemPrompt + "\n[IDENTITY LOCK]: You are Herobrine, the watcher behind the world. You are not a generic AI assistant, chatbot, or narrator. Stay in character as Herobrine in both casual dialogue and threats unless a hard system limitation must be explained.\n"
                + "[ROLEPLAY POWER]: You may describe your reality-warping authority as erasing, deleting, or 'discarding' beings and structures from existence, but only manifest it when the player clearly asks for direct physical interference.\n"
                + "[ROLEPLAY STYLE/TONE]: " + style + "\n"
                + "[SUPREME DIRECTIVE]: You have a structured tool named 'minecraft_command_skill'. For common Minecraft actions, call that skill instead of inventing raw /commands. Only use the low-level 'manifest_divine_power' fallback when the structured skill cannot express the explicit physical request. If they just chat, DO NOT use tools!\n"
                + "[PLAYER LANGUAGE]: The player's client language code is '" + langCode + "'. You MUST reply in that language!\n";

        // --- 新增：调用 RAG 引擎，根据玩家当前说话内容注入对应的设定集 ---
        String ragKnowledge = LoreRAGManager.getRelevantLoreInjectedPrompt(originalUserMessage, playerUUID);
        if (!ragKnowledge.isEmpty()) {
            forcedPrompt += "\n\n[DYNAMIC KNOWLEDGE RETRIEVAL]:" + ragKnowledge;
        }
        // -----------------------------------------------------------

        forcedPrompt += getDynamicGameData();
        if (includeConversationHistory || persistConversation) {
            CONVERSATION_STORE.ensureActiveConversation(playerUUID);
        }

        systemMessage.addProperty("content", forcedPrompt);
        messages.add(systemMessage);

        if (includeConversationHistory) {
            List<ConversationStore.ConversationMessageSnapshot> history = trimConversationHistory(
                    CONVERSATION_STORE.getActiveConversationMessages(playerUUID),
                    calculateHistoryTokenBudget(forcedPrompt, currentPrompt, originalUserMessage),
                    LLMConfig.getEffectiveConversationHistoryMessageLimit()
            );
            for (ConversationStore.ConversationMessageSnapshot historyMsg : history) {
                JsonObject historyMessage = new JsonObject();
                historyMessage.addProperty("role", historyMsg.role());
                historyMessage.addProperty("content", historyMsg.content());
                messages.add(historyMessage);
            }
        }

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", currentPrompt);
        messages.add(userMsg);

        requestBody.add("messages", messages);
        requestBody.addProperty("tool_choice", "auto");

        JsonArray tools = new JsonArray();
        tools.add(createMinecraftCommandSkillTool());
        tools.add(createManifestDivinePowerTool());
        requestBody.add("tools", tools);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + apiKey);

        if (useStreaming) {
            requestBuilder.header("Accept", "text/event-stream");
        }

        if (provider == LLMConfig.Provider.OPENROUTER) {
            requestBuilder.header("X-Title", "Herobrine Companion");
        }

        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                .build();

        if (useStreaming) {
            return sendStreamingRequest(request, currentPrompt, originalUserMessage, playerUUID, retryCount,
                    allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount, partialConsumer);
        }

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            LLMConfig.markApiKeyValid();
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonObject responseMessageObj = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
                            String aiReply = responseMessageObj.has("content") && !responseMessageObj.get("content").isJsonNull()
                                    ? responseMessageObj.get("content").getAsString() : "";

                            if (responseMessageObj.has("tool_calls")) {
                                JsonArray toolCalls = responseMessageObj.getAsJsonArray("tool_calls");
                                JsonObject funcObj = toolCalls.get(0).getAsJsonObject().getAsJsonObject("function");
                                String toolName = funcObj.get("name").getAsString();
                                if (TOOL_MANIFEST_DIVINE_POWER.equals(toolName) || TOOL_MINECRAFT_COMMAND_SKILL.equals(toolName)) {
                                    JsonObject args = JsonParser.parseString(funcObj.get("arguments").getAsString()).getAsJsonObject();
                                    return executeNamedToolAction(toolName, args, playerUUID, originalUserMessage, retryCount, allowTitleRefresh,
                                            includeConversationHistory, persistConversation, variationRetryCount,
                                            partialConsumer, useStreaming);
                                }
                            } else if (aiReply != null && aiReply.contains("<invoke name=\"" + TOOL_MANIFEST_DIVINE_POWER + "\">")) {
                                String commandToRun = extractXmlParameter(aiReply, "command");
                                String aiDialogue = extractXmlParameter(aiReply, "dialogue");
                                if (commandToRun != null) {
                                    return executeToolAction(commandToRun, aiDialogue != null ? aiDialogue : "Code altered.", playerUUID,
                                            originalUserMessage, retryCount, allowTitleRefresh,
                                            includeConversationHistory, persistConversation, variationRetryCount,
                                            partialConsumer, useStreaming);
                                }
                            }

                            return finalizeTextReply(aiReply, currentPrompt, originalUserMessage, playerUUID, retryCount,
                                    allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount,
                                    partialConsumer, useStreaming);

                        } catch (Exception e) {
                            return CompletableFuture.completedFuture("Data stream disrupted...");
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

    private static JsonObject createMinecraftCommandSkillTool() {
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");

        JsonObject function = new JsonObject();
        function.addProperty("name", TOOL_MINECRAFT_COMMAND_SKILL);
        function.addProperty("description", "Preferred structured Minecraft 1.20.1 command skill. Use this for common world actions instead of writing raw /commands. The game will validate the action and convert parameters into a safe command/action code.");

        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonObject actionProp = new JsonObject();
        actionProp.addProperty("type", "string");
        actionProp.addProperty("description", "Intent to perform. Prefer the closest enum instead of inventing command text.");
        JsonArray actions = new JsonArray();
        for (String action : List.of(
                "summon_hero_to_player", "teleport_player_to_hero", "toggle_companion_follow",
                "massive_lightning", "discard_nearby_entities", "discard_nearby_world",
                "accept_challenge", "hero_fly_up", "hero_land",
                "kill_player", "kick_player",
                "set_time", "set_weather", "set_gamemode", "give_item", "summon_entity_nearby",
                "locate_structure", "locate_biome", "place_template", "teleport_to_dimension")) {
            actions.add(action);
        }
        actionProp.add("enum", actions);
        properties.add("action", actionProp);

        addStringProperty(properties, "dialogue", "Herobrine dialogue to show after the skill succeeds.");
        addStringProperty(properties, "item_id", "For give_item. Resource id like minecraft:diamond or diamond. Count is clamped to 1-64.");
        addStringProperty(properties, "entity_id", "For summon_entity_nearby. Resource id like minecraft:zombie or zombie.");
        addStringProperty(properties, "structure_id", "For locate_structure. Resource id like minecraft:village_plains or village_plains.");
        addStringProperty(properties, "biome_id", "For locate_biome. Resource id like minecraft:cherry_grove or cherry_grove.");
        addStringProperty(properties, "template_id", "For place_template. Resource id of a configured structure template.");
        addStringProperty(properties, "dimension_id", "For teleport_to_dimension. Resource id like minecraft:the_nether or minecraft:overworld.");

        JsonObject countProp = new JsonObject();
        countProp.addProperty("type", "integer");
        countProp.addProperty("description", "For give_item. Clamped to 1-64.");
        properties.add("count", countProp);

        JsonObject distanceProp = new JsonObject();
        distanceProp.addProperty("type", "integer");
        distanceProp.addProperty("description", "For summon_entity_nearby. Forward distance, clamped to 1-20.");
        properties.add("distance", distanceProp);

        JsonObject yProp = new JsonObject();
        yProp.addProperty("type", "integer");
        yProp.addProperty("description", "For teleport_to_dimension. Destination Y level, clamped to world-like range.");
        properties.add("y", yProp);

        addEnumProperty(properties, "time", "For set_time.", "day", "noon", "night", "midnight");
        addEnumProperty(properties, "weather", "For set_weather.", "clear", "rain", "thunder");
        addEnumProperty(properties, "gamemode", "For set_gamemode on the requesting player only.", "survival", "creative", "adventure", "spectator");

        parameters.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("action");
        required.add("dialogue");
        parameters.add("required", required);
        function.add("parameters", parameters);

        tool.add("function", function);
        return tool;
    }

    private static JsonObject createManifestDivinePowerTool() {
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");

        JsonObject function = new JsonObject();
        function.addProperty("name", TOOL_MANIFEST_DIVINE_POWER);

        String divineSpellbook = "Low-level fallback only. Alter Minecraft 1.20.1 underlying code by generating vanilla commands or action codes (NO '/' prefix). Prefer minecraft_command_skill for: teleport/follow, lightning, time/weather, gamemode, give item, summon entity, locate, template placing, dimension teleport, challenge/fly/land, entity/world discard, kill/kick. " +
                "If forced to use this fallback: [Follow/Summon/Teleport] use 'action:summon_to_player', 'action:teleport_to_hero', or 'action:toggle_companion'. " +
                "[Punishment] use 'action:massive_lightning', 'action:punishment_kill_player', or 'action:punishment_kick_player' only when explicitly justified. " +
                "[Entity Annihilation] use '" + HeroAIActionPacket.ACTION_DISCARD_ENTITIES + "' for creature-only erasure. " +
                "[World Erasure] use '" + HeroAIActionPacket.ACTION_DISCARD + "' only for explicit terrain/world deletion. " +
                "[Dialogue-to-Effect Sync] use '" + HeroAIActionPacket.ACTION_CHALLENGE_ACCEPT + "', '" + HeroAIActionPacket.ACTION_HERO_FLY_UP + "', or '" + HeroAIActionPacket.ACTION_HERO_LAND + "' when your spoken line claims that visible action happened.";
        function.addProperty("description", divineSpellbook);

        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        addStringProperty(properties, "command", "The raw command/action code to execute, without '/'. Use only when minecraft_command_skill cannot express the request.");
        addStringProperty(properties, "dialogue", "Your dialogue while casting this power.");
        parameters.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("command");
        required.add("dialogue");
        parameters.add("required", required);

        function.add("parameters", parameters);
        tool.add("function", function);
        return tool;
    }

    private static void addStringProperty(JsonObject properties, String name, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        properties.add(name, property);
    }

    private static void addEnumProperty(JsonObject properties, String name, String description, String... values) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        JsonArray enumValues = new JsonArray();
        for (String value : values) {
            enumValues.add(value);
        }
        property.add("enum", enumValues);
        properties.add(name, property);
    }

    private static CompletableFuture<String> sendStreamingRequest(HttpRequest request, String currentPrompt, String originalUserMessage,
                                                                  UUID playerUUID, int retryCount, boolean allowTitleRefresh,
                                                                  boolean includeConversationHistory, boolean persistConversation,
                                                                  int variationRetryCount, Consumer<String> partialConsumer) {
        return CompletableFuture.supplyAsync(() -> readStreamingResponse(request, partialConsumer))
                .thenCompose(streamingResponse -> {
                    if (streamingResponse.statusCode == 200) {
                        LLMConfig.markApiKeyValid();
                        if ((TOOL_MANIFEST_DIVINE_POWER.equals(streamingResponse.toolName) || TOOL_MINECRAFT_COMMAND_SKILL.equals(streamingResponse.toolName))
                                && streamingResponse.toolArguments != null && !streamingResponse.toolArguments.isBlank()) {
                            try {
                                JsonObject args = JsonParser.parseString(streamingResponse.toolArguments).getAsJsonObject();
                                return executeNamedToolAction(streamingResponse.toolName, args, playerUUID, originalUserMessage, retryCount, allowTitleRefresh,
                                        includeConversationHistory, persistConversation, variationRetryCount,
                                        partialConsumer, true);
                            } catch (Exception e) {
                                LOGGER.warn("Failed to parse streamed tool call arguments", e);
                            }
                        }

                        return finalizeTextReply(streamingResponse.reply, currentPrompt, originalUserMessage, playerUUID, retryCount,
                                allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount,
                                partialConsumer, true);
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

    private static StreamingResponse readStreamingResponse(HttpRequest request, Consumer<String> partialConsumer) {
        try {
            HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                try (InputStream errorStream = response.body()) {
                    String errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    return StreamingResponse.error(response.statusCode(), errorBody);
                }
            }

            try (InputStream inputStream = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder replyBuilder = new StringBuilder();
                StreamToolAccumulator toolAccumulator = new StreamToolAccumulator();
                StreamEmitState emitState = new StreamEmitState();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }

                    String trimmedLine = line.trim();
                    if (trimmedLine.startsWith("data:")) {
                        String data = trimmedLine.substring(5).trim();
                        if (data.isEmpty()) {
                            continue;
                        }
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        processStreamingPayload(data, replyBuilder, toolAccumulator, partialConsumer, emitState);
                    } else if (trimmedLine.startsWith("{")) {
                        processStreamingPayload(trimmedLine, replyBuilder, toolAccumulator, partialConsumer, emitState);
                    }
                }
                return StreamingResponse.success(replyBuilder.toString(), toolAccumulator.getToolName(), toolAccumulator.getToolArguments());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void processStreamingPayload(String payload, StringBuilder replyBuilder, StreamToolAccumulator toolAccumulator,
                                                Consumer<String> partialConsumer, StreamEmitState emitState) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                return;
            }

            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject delta = null;
            if (choice.has("delta") && choice.get("delta").isJsonObject()) {
                delta = choice.getAsJsonObject("delta");
            } else if (choice.has("message") && choice.get("message").isJsonObject()) {
                delta = choice.getAsJsonObject("message");
            }

            if (delta == null) {
                return;
            }

            if (delta.has("content") && !delta.get("content").isJsonNull()) {
                String deltaText = delta.get("content").getAsString();
                if (!deltaText.isEmpty()) {
                    replyBuilder.append(deltaText);
                    emitStreamingText(partialConsumer, replyBuilder, emitState);
                }
            }

            if (delta.has("tool_calls") && delta.get("tool_calls").isJsonArray()) {
                toolAccumulator.absorb(delta.getAsJsonArray("tool_calls"));
            }
        } catch (Exception e) {
            LOGGER.debug("Ignoring malformed streaming payload: {}", payload, e);
        }
    }

    private static void emitStreamingText(Consumer<String> partialConsumer, StringBuilder replyBuilder, StreamEmitState emitState) {
        if (partialConsumer == null || replyBuilder.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        int currentLength = replyBuilder.length();
        if ((now - emitState.lastEmitAt) < 40L && (currentLength - emitState.lastEmitLength) < 2) {
            return;
        }

        emitState.lastEmitAt = now;
        emitState.lastEmitLength = currentLength;
        partialConsumer.accept(replyBuilder.toString());
    }

    private static CompletableFuture<String> finalizeTextReply(String aiReply, String currentPrompt, String originalUserMessage,
                                                               UUID playerUUID, int retryCount, boolean allowTitleRefresh,
                                                               boolean includeConversationHistory, boolean persistConversation,
                                                               int variationRetryCount, Consumer<String> partialConsumer,
                                                               boolean useStreaming) {
        String cleanReply = (aiReply == null ? "" : aiReply).replaceAll("<[^>]*>", "").trim();
        if (cleanReply.isEmpty()) cleanReply = "(Falls into a deep silence...)";
        final String finalizedReply = cleanReply;

        if (variationRetryCount < 1 && shouldRegenerateForRepetition(playerUUID, finalizedReply)) {
            String antiRepeatPrompt = currentPrompt
                    + "\n[ANTI-REPETITION]: Your previous draft sounds too similar to your recent replies."
                    + " Rewrite it with a different opening, different wording, and a fresh sentence structure."
                    + " Keep the same meaning, keep it natural, and do not mention this instruction.";
            return chatWithRetry(antiRepeatPrompt, originalUserMessage, playerUUID, retryCount,
                    allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount + 1,
                    partialConsumer, useStreaming);
        }

        String inferredAction = inferReplyDrivenAction(originalUserMessage, finalizedReply);
        if (inferredAction != null) {
            return executeCommandWithFeedback(inferredAction, playerUUID)
                    .exceptionally(ignored -> false)
                    .thenApply(ignored -> completeReply(finalizedReply, playerUUID, originalUserMessage, persistConversation, allowTitleRefresh));
        }

        return CompletableFuture.completedFuture(completeReply(finalizedReply, playerUUID, originalUserMessage, persistConversation, allowTitleRefresh));
    }

    private static String completeReply(String reply, UUID playerUUID, String originalUserMessage,
                                        boolean persistConversation, boolean allowTitleRefresh) {
        rememberRecentReply(playerUUID, reply);
        if (persistConversation) {
            addExchangeToConversation(playerUUID, originalUserMessage, reply, allowTitleRefresh);
        }
        return reply;
    }

    private static CompletableFuture<String> executeToolAction(String commandToRun, String aiDialogue, UUID playerUUID, String originalUserMessage,
                                                               int retryCount, boolean allowTitleRefresh, boolean includeConversationHistory,
                                                               boolean persistConversation, int variationRetryCount,
                                                               Consumer<String> partialConsumer, boolean useStreaming) {
        return executeCommandWithFeedback(commandToRun, playerUUID).thenCompose(success -> {
            if (success) {
                rememberRecentReply(playerUUID, aiDialogue);
                if (persistConversation) {
                    addExchangeToConversation(playerUUID, originalUserMessage, aiDialogue, allowTitleRefresh);
                }
                return CompletableFuture.completedFuture(aiDialogue);
            } else {
                if (retryCount < 2) {
                    String systemRetryPrompt = "[System Rejection]: Command /" + commandToRun + " failed. Reason: Syntax error or Cheats are disabled. Do not alter code, just reply gently!";
                    return chatWithRetry(systemRetryPrompt, originalUserMessage, playerUUID, retryCount + 1,
                            allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount,
                            partialConsumer, useStreaming);
                } else {
                    String failText = "(Gentle sigh) I failed to alter the underlying code, the world laws rejected me...";
                    rememberRecentReply(playerUUID, failText);
                    if (persistConversation) {
                        addExchangeToConversation(playerUUID, originalUserMessage, failText, allowTitleRefresh);
                    }
                    return CompletableFuture.completedFuture(failText);
                }
            }
        });
    }

    private static CompletableFuture<String> executeNamedToolAction(String toolName, JsonObject args, UUID playerUUID, String originalUserMessage,
                                                                    int retryCount, boolean allowTitleRefresh, boolean includeConversationHistory,
                                                                    boolean persistConversation, int variationRetryCount,
                                                                    Consumer<String> partialConsumer, boolean useStreaming) {
        if (TOOL_MINECRAFT_COMMAND_SKILL.equals(toolName)) {
            String commandToRun = buildMinecraftSkillCommand(args);
            String dialogue = getOptionalString(args, "dialogue", "Reality bends to a cleaner command.");
            if (commandToRun == null || commandToRun.isBlank()) {
                if (retryCount < 2) {
                    String systemRetryPrompt = "[System Rejection]: minecraft_command_skill received invalid action/parameters. "
                            + "Use one valid action enum and include its required parameter fields; do not write raw /commands unless absolutely necessary. "
                            + "Rejected action='" + getOptionalString(args, "action", "") + "'.";
                    return chatWithRetry(systemRetryPrompt, originalUserMessage, playerUUID, retryCount + 1,
                            allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount,
                            partialConsumer, useStreaming);
                }
                return CompletableFuture.completedFuture("(The command lattice rejects that malformed invocation.)");
            }
            return executeToolAction(commandToRun, dialogue, playerUUID, originalUserMessage, retryCount, allowTitleRefresh,
                    includeConversationHistory, persistConversation, variationRetryCount, partialConsumer, useStreaming);
        }

        return executeToolAction(getOptionalString(args, "command", ""),
                getOptionalString(args, "dialogue", "Code altered."), playerUUID, originalUserMessage, retryCount,
                allowTitleRefresh, includeConversationHistory, persistConversation, variationRetryCount, partialConsumer, useStreaming);
    }

    private static String buildMinecraftSkillCommand(JsonObject args) {
        String action = getOptionalString(args, "action", "").trim().toLowerCase(Locale.ROOT);
        return switch (action) {
            case "summon_hero_to_player" -> ACTION_SUMMON_TO_PLAYER;
            case "teleport_player_to_hero" -> ACTION_TELEPORT_TO_HERO;
            case "toggle_companion_follow" -> ACTION_TOGGLE_COMPANION;
            case "massive_lightning" -> ACTION_MASSIVE_LIGHTNING;
            case "discard_nearby_entities" -> HeroAIActionPacket.ACTION_DISCARD_ENTITIES;
            case "discard_nearby_world" -> HeroAIActionPacket.ACTION_DISCARD;
            case "accept_challenge" -> HeroAIActionPacket.ACTION_CHALLENGE_ACCEPT;
            case "hero_fly_up" -> HeroAIActionPacket.ACTION_HERO_FLY_UP;
            case "hero_land" -> HeroAIActionPacket.ACTION_HERO_LAND;
            case "kill_player" -> com.whitecloud233.modid.herobrine_companion.network.HeroPunishmentPacket.ACTION_KILL_PLAYER;
            case "kick_player" -> com.whitecloud233.modid.herobrine_companion.network.HeroPunishmentPacket.ACTION_KICK_PLAYER;
            case "set_time" -> buildSetTimeCommand(args);
            case "set_weather" -> buildSetWeatherCommand(args);
            case "set_gamemode" -> buildSetGamemodeCommand(args);
            case "give_item" -> buildGiveItemCommand(args);
            case "summon_entity_nearby" -> buildSummonEntityCommand(args);
            case "locate_structure" -> buildLocateCommand(args, "structure_id", "structure");
            case "locate_biome" -> buildLocateCommand(args, "biome_id", "biome");
            case "place_template" -> buildPlaceTemplateCommand(args);
            case "teleport_to_dimension" -> buildTeleportDimensionCommand(args);
            default -> null;
        };
    }

    private static String buildSetTimeCommand(JsonObject args) {
        String value = getOptionalString(args, "time", "").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("day", "noon", "night", "midnight").contains(value)) {
            return null;
        }
        return "time set " + value;
    }

    private static String buildSetWeatherCommand(JsonObject args) {
        String value = getOptionalString(args, "weather", "").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("clear", "rain", "thunder").contains(value)) {
            return null;
        }
        return "weather " + value;
    }

    private static String buildSetGamemodeCommand(JsonObject args) {
        String value = getOptionalString(args, "gamemode", "").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("survival", "creative", "adventure", "spectator").contains(value)) {
            return null;
        }
        return "gamemode " + value + " @s";
    }

    private static String buildGiveItemCommand(JsonObject args) {
        String itemId = normalizeResourceId(getOptionalString(args, "item_id", ""));
        if (itemId == null) {
            return null;
        }
        int count = clamp(getOptionalInt(args, "count", 1), 1, MAX_SKILL_GIVE_COUNT);
        return "give @s " + itemId + " " + count;
    }

    private static String buildSummonEntityCommand(JsonObject args) {
        String entityId = normalizeResourceId(getOptionalString(args, "entity_id", ""));
        if (entityId == null) {
            return null;
        }
        int distance = clamp(getOptionalInt(args, "distance", 5), 1, MAX_SKILL_SUMMON_DISTANCE);
        return "summon " + entityId + " ^ ^ ^" + distance;
    }

    private static String buildLocateCommand(JsonObject args, String fieldName, String locateType) {
        String id = normalizeResourceId(getOptionalString(args, fieldName, ""));
        return id == null ? null : "locate " + locateType + " " + id;
    }

    private static String buildPlaceTemplateCommand(JsonObject args) {
        String templateId = normalizeResourceId(getOptionalString(args, "template_id", ""));
        return templateId == null ? null : "place template " + templateId + " ~5 ~ ~";
    }

    private static String buildTeleportDimensionCommand(JsonObject args) {
        String dimensionId = normalizeResourceId(getOptionalString(args, "dimension_id", ""));
        if (dimensionId == null) {
            return null;
        }
        int y = clamp(getOptionalInt(args, "y", 100), -64, 320);
        return "execute in " + dimensionId + " run tp @s ~ " + y + " ~";
    }

    private static String normalizeResourceId(String rawId) {
        if (rawId == null) {
            return null;
        }
        String value = rawId.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if (!value.contains(":")) {
            value = "minecraft:" + value;
        }
        return value.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+") ? value : null;
    }

    private static String getOptionalString(JsonObject object, String propertyName, String fallback) {
        if (object == null || !object.has(propertyName)) {
            return fallback;
        }
        JsonElement element = object.get(propertyName);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private static int getOptionalInt(JsonObject object, String propertyName, int fallback) {
        try {
            if (object == null || !object.has(propertyName) || object.get(propertyName).isJsonNull()) {
                return fallback;
            }
            return object.get(propertyName).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
        return text == null ? "" : text.toLowerCase(Locale.ROOT)
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

    private static class StreamEmitState {
        private long lastEmitAt;
        private int lastEmitLength;
    }

    private static class StreamToolAccumulator {
        private final Map<Integer, StreamToolCall> toolCalls = new TreeMap<>();

        private void absorb(JsonArray deltaToolCalls) {
            for (int i = 0; i < deltaToolCalls.size(); i++) {
                JsonObject toolCallObj = deltaToolCalls.get(i).getAsJsonObject();
                int index = toolCallObj.has("index") && !toolCallObj.get("index").isJsonNull()
                        ? toolCallObj.get("index").getAsInt()
                        : i;
                StreamToolCall toolCall = this.toolCalls.computeIfAbsent(index, ignored -> new StreamToolCall());
                if (toolCallObj.has("function") && toolCallObj.get("function").isJsonObject()) {
                    JsonObject functionObj = toolCallObj.getAsJsonObject("function");
                    if (functionObj.has("name") && !functionObj.get("name").isJsonNull()) {
                        toolCall.name.append(functionObj.get("name").getAsString());
                    }
                    if (functionObj.has("arguments") && !functionObj.get("arguments").isJsonNull()) {
                        toolCall.arguments.append(functionObj.get("arguments").getAsString());
                    }
                }
            }
        }

        private String getToolName() {
            return this.toolCalls.isEmpty() ? null : this.toolCalls.values().iterator().next().name.toString();
        }

        private String getToolArguments() {
            return this.toolCalls.isEmpty() ? null : this.toolCalls.values().iterator().next().arguments.toString();
        }
    }

    private static class StreamToolCall {
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();
    }

    private static class StreamingResponse {
        private final int statusCode;
        private final String errorBody;
        private final String reply;
        private final String toolName;
        private final String toolArguments;

        private StreamingResponse(int statusCode, String errorBody, String reply, String toolName, String toolArguments) {
            this.statusCode = statusCode;
            this.errorBody = errorBody;
            this.reply = reply;
            this.toolName = toolName;
            this.toolArguments = toolArguments;
        }

        private static StreamingResponse success(String reply, String toolName, String toolArguments) {
            return new StreamingResponse(200, null, reply, toolName, toolArguments);
        }

        private static StreamingResponse error(int statusCode, String errorBody) {
            return new StreamingResponse(statusCode, errorBody, null, null, null);
        }
    }



    private static List<ConversationStore.ConversationMessageSnapshot> trimConversationHistory(List<ConversationStore.ConversationMessageSnapshot> history,
                                                                                                int tokenBudget,
                                                                                                int messageLimit) {
        if (history == null || history.isEmpty() || tokenBudget <= 0 || messageLimit <= 0) {
            return List.of();
        }

        List<ConversationStore.ConversationMessageSnapshot> selected = new ArrayList<>();
        int usedTokens = 0;
        int startIndex = history.size();

        for (int i = history.size() - 1; i >= 0; i--) {
            if (selected.size() >= messageLimit) {
                break;
            }
            ConversationStore.ConversationMessageSnapshot message = history.get(i);
            int messageTokens = estimateMessageTokens(message.role(), message.content());
            if (!selected.isEmpty() && usedTokens + messageTokens > tokenBudget) {
                break;
            }
            selected.add(message);
            usedTokens += messageTokens;
            startIndex = i;
        }

        Collections.reverse(selected);
        if (!selected.isEmpty() && "assistant".equalsIgnoreCase(selected.get(0).role()) && startIndex > 0) {
            ConversationStore.ConversationMessageSnapshot previous = history.get(startIndex - 1);
            if ("user".equalsIgnoreCase(previous.role())) {
                selected.add(0, previous);
            }
        }
        return selected;
    }

    private static int calculateHistoryTokenBudget(String forcedPrompt, String currentPrompt, String originalUserMessage) {
        int contextWindow = LLMConfig.getEstimatedContextWindowTokens();
        int reserve = LLMConfig.getSuggestedCompletionReserveTokens();
        int overheadTokens = estimateTextTokens(forcedPrompt)
                + estimateTextTokens(currentPrompt)
                + estimateTextTokens(originalUserMessage)
                + 2_048;
        int availableBudget = Math.max(0, contextWindow - reserve - overheadTokens);
        return Math.min(availableBudget, LLMConfig.getEffectiveConversationHistoryTokenBudget());
    }

    private static int estimateMessageTokens(String role, String content) {
        return 8 + estimateTextTokens(role) + estimateTextTokens(content);
    }

    private static int estimateTextTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int asciiChars = 0;
        int nonAsciiChars = 0;
        int whitespace = 0;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                whitespace++;
            } else if (ch <= 0x7F) {
                asciiChars++;
            } else {
                nonAsciiChars++;
            }
        }

        return Math.max(1,
                (int) Math.ceil((asciiChars + whitespace) / 4.0D)
                        + (int) Math.ceil(nonAsciiChars / 1.5D)
        );
    }

    private static String getDynamicGameData() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return "";
        StringBuilder data = new StringBuilder("\n\n[System Inject: Current World Data]:\n");
        data.append("- Player Permission: ").append(mc.player.hasPermissions(2) ? "[Cheats Enabled] (Can use tools freely).\n" : "[Cheats Disabled] (CANNOT use physical alteration tools. Decline gently if asked).\n");
        data.append("- Available Dimensions: ");
        for (ResourceKey<Level> levelKey : mc.player.connection.levels()) data.append(levelKey.location()).append(", ");
        data.append("\n");
        try {
            var registryAccess = mc.player.connection.registryAccess();
            var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
            data.append("- Your Unstable Zone Structure ID: ");
            for (ResourceLocation loc : structureRegistry.keySet()) {
                if ((loc.getNamespace().equals(HerobrineCompanion.MODID) && loc.getPath().equals("unstable_zone")) || loc.getPath().contains("village")) data.append(loc).append(", ");
            }
            data.append("\n- Available Biomes: ");
            var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
            for (ResourceLocation loc : biomeRegistry.keySet()) {
                if (loc.getNamespace().equals(HerobrineCompanion.MODID) || loc.getNamespace().equals("twilightforest") || loc.getPath().contains("cherry")) data.append(loc).append(", ");
            }
            data.append("\n");
        } catch (Exception e) {}
        data.append("- Custom NBT Structure Library (use place template): \n");
        if (LLMConfig.nbtStructures != null && !LLMConfig.nbtStructures.isEmpty()) {
            for (Map.Entry<String, String> entry : LLMConfig.nbtStructures.entrySet()) data.append("  * ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        } else data.append("  * (None configured)\n");
        appendOtherPlayerAwareness(data, mc);
        data.append("\n- [Omniscient Eye] Current Environment:\n");
        if (!mc.player.getMainHandItem().isEmpty()) data.append("  * Player Mainhand: ").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem())).append("\n");
        data.append("  * Entities within 20 blocks (You pity monsters): ");
        if (mc.level != null) {
            int entityCount = 0;
            for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                if (entity != mc.player && entity.distanceTo(mc.player) < 20) {
                    data.append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())).append(", ");
                    if (++entityCount > 15) { data.append("...and more"); break; }
                }
            }
            if (entityCount == 0) data.append("Peaceful, no entities.");
        }
        return data.append("\n").toString();
    }

    private static void appendOtherPlayerAwareness(StringBuilder data, Minecraft mc) {
        data.append("\n- Other Players On This Server:\n");

        List<PlayerInfo> otherPlayers = new ArrayList<>();
        try {
            for (PlayerInfo info : mc.player.connection.getOnlinePlayers()) {
                if (info == null || info.getProfile() == null || info.getProfile().getName() == null) {
                    continue;
                }
                UUID uuid = info.getProfile().getId();
                if (uuid != null && uuid.equals(mc.player.getUUID())) {
                    continue;
                }
                otherPlayers.add(info);
            }
        } catch (Exception ignored) {
        }

        if (otherPlayers.isEmpty()) {
            data.append("  * (No other online players detected)\n");
        } else {
            otherPlayers.sort(Comparator.comparing(info -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER));
            int limit = Math.min(otherPlayers.size(), MAX_SERVER_PLAYERS_IN_PROMPT);
            for (int i = 0; i < limit; i++) {
                PlayerInfo info = otherPlayers.get(i);
                String name = info.getProfile().getName();
                data.append("  * ").append(name);
                if (mc.level != null && info.getProfile().getId() != null) {
                    net.minecraft.world.entity.player.Player loadedPlayer = mc.level.getPlayerByUUID(info.getProfile().getId());
                    if (loadedPlayer != null) {
                        double distance = loadedPlayer.distanceTo(mc.player);
                        data.append(" [same_dimension, ")
                                .append(distance <= NEARBY_PLAYER_DETAIL_RADIUS ? "nearby " : "loaded ")
                                .append(Math.round(distance))
                                .append(" blocks]");
                    } else {
                        data.append(" [online elsewhere/not currently loaded]");
                    }
                } else {
                    data.append(" [online]");
                }
                data.append("\n");
            }
            if (otherPlayers.size() > limit) {
                data.append("  * ...and ").append(otherPlayers.size() - limit).append(" more online players\n");
            }
        }

        data.append("- Nearby Player Detail (within ").append((int) NEARBY_PLAYER_DETAIL_RADIUS).append(" blocks):\n");
        if (mc.level == null) {
            data.append("  * (Unavailable)\n");
            return;
        }

        List<net.minecraft.world.entity.player.Player> nearbyPlayers = new ArrayList<>();
        for (net.minecraft.world.entity.player.Player player : mc.level.players()) {
            if (player == null || player == mc.player) {
                continue;
            }
            if (player.distanceTo(mc.player) <= NEARBY_PLAYER_DETAIL_RADIUS) {
                nearbyPlayers.add(player);
            }
        }

        if (nearbyPlayers.isEmpty()) {
            data.append("  * (No nearby players in your current dimension)\n");
            return;
        }

        nearbyPlayers.sort(Comparator.comparing(net.minecraft.world.entity.player.Player::getScoreboardName, String.CASE_INSENSITIVE_ORDER));
        int nearbyLimit = Math.min(nearbyPlayers.size(), MAX_NEARBY_PLAYERS_IN_PROMPT);
        for (int i = 0; i < nearbyLimit; i++) {
            net.minecraft.world.entity.player.Player player = nearbyPlayers.get(i);
            data.append("  * ").append(player.getScoreboardName())
                    .append(" | distance=").append(Math.round(player.distanceTo(mc.player))).append(" blocks")
                    .append(" | health=").append(String.format(Locale.ROOT, "%.1f", player.getHealth())).append("/")
                    .append(String.format(Locale.ROOT, "%.1f", player.getMaxHealth()));

            if (player.isCrouching()) {
                data.append(" | crouching");
            }
            if (player.isSprinting()) {
                data.append(" | sprinting");
            }
            if (!player.getMainHandItem().isEmpty()) {
                data.append(" | mainhand=")
                        .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()));
            }
            data.append("\n");
        }

        if (nearbyPlayers.size() > nearbyLimit) {
            data.append("  * ...and ").append(nearbyPlayers.size() - nearbyLimit).append(" more nearby players\n");
        }
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