package com.whitecloud233.herobrine_companion.entity.companion;

import java.util.UUID;

public final class CompanionModePolicy {
    public static final int FOLLOW_TRUST_THRESHOLD = 50;

    private CompanionModePolicy() {
    }

    public record State(UUID ownerUuid, int trust, boolean following, boolean alive,
                        boolean restricted, boolean conflictingHero) {
    }

    public enum Result {
        CHANGED(null),
        UNCHANGED(null),
        NO_REQUESTER("message.herobrine_companion.tool.set_mode.no_companion"),
        NOT_OWNER("message.herobrine_companion.not_your_hero"),
        UNAVAILABLE("message.herobrine_companion.companion_unavailable"),
        TRUST_TOO_LOW("message.herobrine_companion.tool.set_mode.trust_low"),
        CONFLICTING_HERO("message.herobrine_companion.companion_conflict");

        private final String messageKey;

        Result(String messageKey) {
            this.messageKey = messageKey;
        }

        public boolean successful() {
            return this == CHANGED || this == UNCHANGED;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    public static Result evaluate(UUID requester, State state, boolean desiredMode) {
        if (requester == null) return Result.NO_REQUESTER;
        if (state.ownerUuid() != null && !state.ownerUuid().equals(requester)) return Result.NOT_OWNER;
        if (!state.alive()) return Result.UNAVAILABLE;

        // 退出跟随不解绑，也不能因信任下降或进入试炼而无法停止。
        if (!desiredMode) return state.following() ? Result.CHANGED : Result.UNCHANGED;
        if (state.restricted()) return Result.UNAVAILABLE;
        if (state.conflictingHero()) return Result.CONFLICTING_HERO;
        if (state.following() && state.ownerUuid() != null) return Result.UNCHANGED;
        if (state.trust() < FOLLOW_TRUST_THRESHOLD) return Result.TRUST_TOO_LOW;
        return Result.CHANGED;
    }
}
