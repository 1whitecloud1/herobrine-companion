package com.whitecloud233.herobrine_companion.entity.dialogue;

import java.util.List;

public record ActorDialoguePersona(
        String actorName,
        String identity,
        String speakingStyle,
        List<String> rules
) {
    public ActorDialoguePersona {
        actorName = actorName == null ? "" : actorName.trim();
        identity = identity == null ? "" : identity.trim();
        speakingStyle = speakingStyle == null ? "" : speakingStyle.trim();
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public String buildSystemPrompt(String outputLanguageCode) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are ").append(actorName.isBlank() ? "an in-world speaker" : actorName).append(".\n");
        if (!identity.isBlank()) {
            builder.append(identity).append('\n');
        }
        if (!speakingStyle.isBlank()) {
            builder.append("Style: ").append(speakingStyle).append('\n');
        }
        builder.append("Hard rules:\n");
        builder.append("- Speak as this character, in first person when natural.\n");
        builder.append("- Output exactly one short spoken line, under 28 words.\n");
        builder.append("- No narration, no stage directions, no asterisks, no brackets, no quotes.\n");
        builder.append("- Do not mention being an AI, language model, assistant, or game system.\n");
        builder.append("- Do not explain your reasoning. Do not describe actions outside the spoken line.\n");
        builder.append("- Reply in the language for Minecraft locale code '")
                .append(outputLanguageCode == null || outputLanguageCode.isBlank() ? "en_us" : outputLanguageCode)
                .append("'.\n");
        for (String rule : rules) {
            if (rule != null && !rule.isBlank()) {
                builder.append("- ").append(rule.trim()).append('\n');
            }
        }
        return builder.toString().trim();
    }
}
