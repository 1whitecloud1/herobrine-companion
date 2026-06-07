package com.whitecloud233.herobrine_companion.client.service;

import java.util.UUID;

public record ActorDialogueRequest(
        String systemPrompt,
        String userPrompt,
        String seedText,
        UUID conversationScopeId,
        UUID authorityPlayerUUID,
        String outputLanguageCode
) {
    public ActorDialogueRequest {
        systemPrompt = systemPrompt == null ? "" : systemPrompt.trim();
        userPrompt = userPrompt == null ? "" : userPrompt.trim();
        seedText = seedText == null ? "" : seedText.trim();
        if (conversationScopeId == null) {
            conversationScopeId = authorityPlayerUUID;
        }
    }
}
