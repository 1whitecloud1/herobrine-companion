package com.whitecloud233.herobrine_companion.client.service;

import java.util.UUID;
import java.util.function.Consumer;

/** 一次对话的身份与会话策略在重试、工具回填和路由回退期间保持不变。 */
public record AIRequestContext(String originalMessage, UUID conversationScopeId, UUID authorityPlayerId,
                               String outputLanguageCode, Consumer<String> partialConsumer,
                               ConversationPolicy conversationPolicy, boolean crossSessionMode) {
    public enum ConversationPolicy {
        PERSISTENT(true, true, true),
        EPHEMERAL(false, false, false);

        private final boolean includeHistory;
        private final boolean persist;
        private final boolean refreshTitle;

        ConversationPolicy(boolean includeHistory, boolean persist, boolean refreshTitle) {
            this.includeHistory = includeHistory;
            this.persist = persist;
            this.refreshTitle = refreshTitle;
        }

        public boolean includeHistory() { return includeHistory; }
        public boolean persist() { return persist; }
        public boolean refreshTitle() { return refreshTitle; }
    }

    public AIRequestContext {
        originalMessage = originalMessage == null ? "" : originalMessage;
        conversationScopeId = conversationScopeId != null ? conversationScopeId : authorityPlayerId;
        authorityPlayerId = authorityPlayerId != null ? authorityPlayerId : conversationScopeId;
        if (conversationPolicy == null) throw new IllegalArgumentException("Missing conversation policy");
    }
}
