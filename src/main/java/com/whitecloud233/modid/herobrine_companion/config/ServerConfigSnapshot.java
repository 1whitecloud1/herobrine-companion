package com.whitecloud233.modid.herobrine_companion.config;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** 一次读取获取完整不可变快照，避免异步线程看见逐字段更新的中间态。 */
public record ServerConfigSnapshot(Map<ServerRule, Integer> values) {
    public ServerConfigSnapshot {
        Objects.requireNonNull(values, "values");
        EnumMap<ServerRule, Integer> normalized = new EnumMap<>(ServerRule.class);
        for (ServerRule rule : ServerRule.values()) {
            Integer value = values.get(rule);
            normalized.put(rule, rule.normalize(value == null ? rule.defaultValue() : value));
        }
        values = Map.copyOf(normalized);
    }

    public static ServerConfigSnapshot defaults() { return new ServerConfigSnapshot(Map.of()); }
    public int value(ServerRule rule) { return values.get(rule); }
    public boolean enabled(ServerRule rule) { return value(rule) != 0; }

    public ServerConfigSnapshot with(ServerRule rule, int value) {
        EnumMap<ServerRule, Integer> updated = new EnumMap<>(ServerRule.class);
        updated.putAll(values);
        updated.put(rule, value);
        return new ServerConfigSnapshot(updated);
    }
}
