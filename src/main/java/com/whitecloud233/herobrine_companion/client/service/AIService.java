package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.herobrine_companion.entity.ai.LLMConfig;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AIService.class);
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    
    // [修改] 使用 Map 存储每个玩家的聊天记录
    private static final Map<UUID, List<JsonObject>> chatHistories = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_SIZE = 20; 

    // [修改] 增加 playerUUID 参数
    public static CompletableFuture<String> chat(String userMessage, UUID playerUUID) {
        String apiKey = LLMConfig.aiApiKey;
        String endpoint = LLMConfig.aiEndpoint;
        String model = LLMConfig.aiModel;
        String systemPrompt = LLMConfig.aiSystemPrompt;

        if (apiKey.equals("YOUR_API_KEY_HERE") || apiKey.isEmpty()) {
            return CompletableFuture.completedFuture("§c" + Component.translatable("message.herobrine_companion.ai_config_missing").getString());
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", false);
        
        JsonArray messages = new JsonArray();
        
        // 1. System Prompt
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);
        
        // 2. History (Specific to Player)
        List<JsonObject> history = chatHistories.computeIfAbsent(playerUUID, k -> new ArrayList<>());
        synchronized (history) {
            for (JsonObject historyMsg : history) {
                messages.add(historyMsg);
            }
        }
        
        // 3. Current User Message
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);
        
        requestBody.add("messages", messages);

        LOGGER.info("Sending AI Request to: " + endpoint + " for player: " + playerUUID);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            String aiReply = json.getAsJsonArray("choices")
                                    .get(0).getAsJsonObject()
                                    .getAsJsonObject("message")
                                    .get("content").getAsString();
                            
                            // Update History
                            addToHistory(playerUUID, "user", userMessage);
                            addToHistory(playerUUID, "assistant", aiReply);
                            
                            return aiReply;
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse AI response", e);
                            return "I am confused... (Parse Error)";
                        }
                    } else {
                        LOGGER.error("AI API Error: " + response.statusCode() + " Body: " + response.body());
                        return "I cannot hear you clearly... (API Error: " + response.statusCode() + ")";
                    }
                })
                .exceptionally(e -> {
                    LOGGER.error("AI Request Failed", e);
                    return "Silence... (Network Error: " + e.getMessage() + ")";
                });
    }
    
    private static void addToHistory(UUID playerUUID, String role, String content) {
        List<JsonObject> history = chatHistories.computeIfAbsent(playerUUID, k -> new ArrayList<>());
        synchronized (history) {
            JsonObject msg = new JsonObject();
            msg.addProperty("role", role);
            msg.addProperty("content", content);
            history.add(msg);
            
            while (history.size() > MAX_HISTORY_SIZE) {
                history.remove(0);
            }
        }
    }
    
    public static void clearHistory(UUID playerUUID) {
        chatHistories.remove(playerUUID);
    }
}
