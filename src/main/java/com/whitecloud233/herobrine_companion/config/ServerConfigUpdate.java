package com.whitecloud233.herobrine_companion.config;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** 配置修改按字段补丁表达；旧协议缺少的尾字段必须保持未修改。 */
public record ServerConfigUpdate(Map<ServerRule, Integer> changes) {
    public ServerConfigUpdate {
        Objects.requireNonNull(changes, "changes");
        EnumMap<ServerRule, Integer> copy = new EnumMap<>(ServerRule.class);
        changes.forEach((rule, value) -> copy.put(Objects.requireNonNull(rule), Objects.requireNonNull(value)));
        changes = Map.copyOf(copy);
    }

    public static ServerConfigUpdate single(ServerRule rule, int value) { return new ServerConfigUpdate(Map.of(rule, value)); }
    public static ServerConfigUpdate full(ServerConfigSnapshot snapshot) { return new ServerConfigUpdate(snapshot.values()); }

    public ServerConfigSnapshot applyTo(ServerConfigSnapshot current) {
        EnumMap<ServerRule, Integer> merged = new EnumMap<>(ServerRule.class);
        merged.putAll(current.values());
        merged.putAll(changes);
        return new ServerConfigSnapshot(merged);
    }
}
