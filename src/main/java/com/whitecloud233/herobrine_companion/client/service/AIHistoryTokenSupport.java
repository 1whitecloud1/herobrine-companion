package com.whitecloud233.herobrine_companion.client.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AIHistoryTokenSupport {
    private AIHistoryTokenSupport() {}

    static List<ConversationStore.ConversationMessageSnapshot> trimConversationHistory(List<ConversationStore.ConversationMessageSnapshot> history,
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

    static int calculateHistoryTokenBudget(String forcedPrompt, String currentPrompt, String originalUserMessage) {
        int contextWindow = LLMConfig.getEstimatedContextWindowTokens();
        int reserve = LLMConfig.getSuggestedCompletionReserveTokens();
        int systemTokens = estimateTextTokens(forcedPrompt);
        int latestPromptTokens = estimateTextTokens(currentPrompt) + estimateTextTokens(originalUserMessage);
        int targetHistoryBudget = LLMConfig.getEffectiveConversationHistoryTokenBudget();
        int available = Math.max(0, contextWindow - reserve - systemTokens - latestPromptTokens);
        return Math.max(0, Math.min(targetHistoryBudget, available));
    }

    static int estimateMessageTokens(String role, String content) {
        return 6 + estimateTextTokens(role) + estimateTextTokens(content);
    }

    static int estimateTextTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int asciiChars = 0;
        int nonAsciiChars = 0;
        int words = 0;
        boolean inWord = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c <= 0x7F) {
                asciiChars++;
            } else {
                nonAsciiChars++;
            }

            if (Character.isLetterOrDigit(c)) {
                if (!inWord) {
                    words++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
        }

        int asciiTokenEstimate = Math.max(words, (asciiChars + 3) / 4);
        int nonAsciiTokenEstimate = (nonAsciiChars + 1) / 2;
        return asciiTokenEstimate + nonAsciiTokenEstimate;
    }
}
