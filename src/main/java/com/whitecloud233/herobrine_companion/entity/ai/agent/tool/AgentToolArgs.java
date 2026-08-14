package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具调用参数（不可变）。统一以字符串值承载参数，工具实现自行做类型化读取与校验。
 *
 * <p>与既有客户端命令校验器（{@code AICommandSkillSupport}）的区别：这里只承载 + 提供类型化读取，
 * 不做任何白名单/范围校验——那属于 {@link AgentTool#validate(AgentToolArgs)} 的职责（单一职责）。</p>
 */
public record AgentToolArgs(Map<String, String> values) {

    public AgentToolArgs {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static AgentToolArgs empty() {
        return new AgentToolArgs(Map.of());
    }

    /** 从 JSON 对象构造（值为扁平字符串）。用于 C→S 包在服务端还原参数。 */
    public static AgentToolArgs fromJson(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            Map<String, String> values = new LinkedHashMap<>();
            for (String key : object.keySet()) {
                if (object.get(key).isJsonPrimitive()) {
                    values.put(key, object.get(key).getAsString());
                }
            }
            return new AgentToolArgs(values);
        } catch (RuntimeException ignored) {
            return empty();
        }
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public String getString(String key) {
        return values.getOrDefault(key, "");
    }

    public String getString(String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value;
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /** 宽松整型读取；无法解析时返回 fallback。 */
    public int getInt(String key, int fallback) {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(values);
    }

    @Override
    public String toString() {
        return values.isEmpty() ? "{}" : values.toString();
    }
}