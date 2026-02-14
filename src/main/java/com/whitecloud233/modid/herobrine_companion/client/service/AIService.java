package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.modid.herobrine_companion.entity.ai.AIConfig;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AIService.class);
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    
    private static final Map<UUID, List<JsonObject>> chatHistory = new HashMap<>();
    private static final int MAX_HISTORY_SIZE = 20; 
    

    public static CompletableFuture<String> chat(UUID playerUUID, String userMessage) {
        String apiKey = AIConfig.aiApiKey;
        String endpoint = AIConfig.aiEndpoint;
        String model = AIConfig.aiModel;
        String systemPrompt = AIConfig.aiSystemPrompt;

        if (apiKey.equals("YOUR_API_KEY_HERE") || apiKey.isEmpty()) {
            return CompletableFuture.completedFuture("§c" + Component.translatable("message.herobrine_companion.ai_config_missing").getString());
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", false);
        
        JsonArray messages = new JsonArray();
        
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);
        
        List<JsonObject> history = chatHistory.computeIfAbsent(playerUUID, k -> new ArrayList<>());
        for (JsonObject historyMsg : history) {
            messages.add(historyMsg);
        }
        
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);
        
        requestBody.add("messages", messages);

        LOGGER.info("Sending AI Request to: " + endpoint);
        if (apiKey.length() > 10) {
             LOGGER.info("API Key: " + apiKey.substring(0, 6) + "..." + apiKey.substring(apiKey.length() - 4));
        }

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
        List<JsonObject> history = chatHistory.computeIfAbsent(playerUUID, k -> new ArrayList<>());
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        msg.addProperty("content", content);
        history.add(msg);
        
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }
    }
}