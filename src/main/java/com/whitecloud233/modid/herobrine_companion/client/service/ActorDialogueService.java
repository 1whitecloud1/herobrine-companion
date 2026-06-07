package com.whitecloud233.modid.herobrine_companion.client.service;

import java.util.concurrent.CompletableFuture;

public final class ActorDialogueService {
    private ActorDialogueService() {
    }

    public static CompletableFuture<String> generateLine(ActorDialogueRequest request) {
        if (request == null) {
            return CompletableFuture.completedFuture("");
        }

        return AIService.generateActorDialogue(
                request.systemPrompt(),
                request.userPrompt(),
                request.seedText(),
                request.conversationScopeId(),
                request.authorityPlayerUUID(),
                request.outputLanguageCode()
        );
    }
}
