package com.whitecloud233.herobrine_companion.entity.dialogue;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.UUID;

/**
 * 一次 Actor 台词请求。
 *
 * @param channel 台词通道(决定使用哪条独立限流;见 {@link DialogueChannel})。
 *                只想本地显示、不发起 LLM 请求的台词请直接用
 *                {@code HeroEntity.showGiftLine(...)},不要构造 spec。
 */
public record ActorDialogueSpec(
        LivingEntity speaker,
        UUID conversationScopeId,
        ActorDialoguePersona persona,
        String userPrompt,
        String seedText,
        String fallbackKey,
        List<String> fallbackArgs,
        ServerPlayer preferredAudience,
        ActorDialogueFollowUp followUp,
        DialogueChannel channel
) {
    public ActorDialogueSpec {
        userPrompt = userPrompt == null ? "" : userPrompt.trim();
        seedText = seedText == null ? "" : seedText.trim();
        fallbackKey = fallbackKey == null ? "" : fallbackKey.trim();
        fallbackArgs = fallbackArgs == null ? List.of() : List.copyOf(fallbackArgs);
        channel = channel == null ? DialogueChannel.AWAKENED : channel;
    }

    /** 觉醒生物/通用情景台词:沿用共享的觉醒对话限流。 */
    public ActorDialogueSpec(LivingEntity speaker, UUID conversationScopeId, ActorDialoguePersona persona,
                             String userPrompt, String seedText, String fallbackKey,
                             List<String> fallbackArgs, ServerPlayer preferredAudience,
                             ActorDialogueFollowUp followUp) {
        this(speaker, conversationScopeId, persona, userPrompt, seedText, fallbackKey,
                fallbackArgs, preferredAudience, followUp, DialogueChannel.AWAKENED);
    }
}