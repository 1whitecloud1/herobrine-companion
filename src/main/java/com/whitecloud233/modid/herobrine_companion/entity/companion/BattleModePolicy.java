package com.whitecloud233.modid.herobrine_companion.entity.companion;

import java.util.UUID;

/**
 * 战斗模式切换门槛（单一职责：只回答「此刻能不能把 Herobrine 切进/切出战斗模式」）。
 *
 * <p>开严关宽：开启要过信任门槛与冷却；收刀（关闭）永远允许——不能因为信任下降
 * 或冷却进行中而无法让他停手。挑战期间双向锁定，沿用收包入口的旧行为。</p>
 *
 * <p>门槛数值由服务器规则提供（{@code ServerRule.BATTLE_MODE_*}），这里不持有配置。</p>
 */
public final class BattleModePolicy {
    private BattleModePolicy() {
    }

    /**
     * trustThreshold &lt;= 0 表示服务器关闭了信任门槛；cooldownRemainingTicks &lt;= 0 表示当前不在冷却。
     */
    public record State(UUID ownerUuid, UUID requester, int trust, int trustThreshold,
                        boolean battleActive, boolean alive, boolean restricted,
                        long cooldownRemainingTicks) {
    }

    public enum Result {
        CHANGED(null),
        UNCHANGED(null),
        NOT_OWNER("message.herobrine_companion.not_your_hero"),
        UNAVAILABLE("message.herobrine_companion.companion_unavailable"),
        RESTRICTED("message.herobrine_companion.battle_mode_blocked_challenge"),
        TRUST_TOO_LOW("message.herobrine_companion.battle_mode_trust_low"),
        COOLDOWN("message.herobrine_companion.battle_mode_cooldown");

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

    public static Result evaluate(State state, boolean desired) {
        if (state.ownerUuid() != null && !state.ownerUuid().equals(state.requester())) return Result.NOT_OWNER;
        if (state.restricted()) return Result.RESTRICTED;
        if (!state.alive()) return Result.UNAVAILABLE;

        // 收刀永远允许：战斗中信任跌破门槛或冷却未清都不影响停止。
        if (!desired) return state.battleActive() ? Result.CHANGED : Result.UNCHANGED;
        if (state.battleActive()) return Result.UNCHANGED;
        if (state.cooldownRemainingTicks() > 0) return Result.COOLDOWN;
        if (state.trustThreshold() > 0 && state.trust() < state.trustThreshold()) return Result.TRUST_TOO_LOW;
        return Result.CHANGED;
    }
}
