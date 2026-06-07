package com.whitecloud233.modid.herobrine_companion.entity.dialogue;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.UUID;

public record ActorDialogueSpec(
        LivingEntity speaker,
        UUID conversationScopeId,
        ActorDialoguePersona persona,
        String userPrompt,
        String seedText,
        String fallbackKey,
        List<String> fallbackArgs,
        ServerPlayer preferredAudience,
        ActorDialogueFollowUp followUp
) {
    public ActorDialogueSpec {
        userPrompt = userPrompt == null ? "" : userPrompt.trim();
        seedText = seedText == null ? "" : seedText.trim();
        fallbackKey = fallbackKey == null ? "" : fallbackKey.trim();
        fallbackArgs = fallbackArgs == null ? List.of() : List.copyOf(fallbackArgs);
    }
}
