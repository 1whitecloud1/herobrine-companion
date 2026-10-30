package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具调用参数（不可变）。统一以字符串值承载参数，工具实现自行做类型化读取与校验。
 *
 * <p><b>协议限制</b>（交接 4.2）：本类只保留 JSON <b>primitive</b>（字符串 / 数字 / 布尔），
 * 嵌套对象与数组会被忽略——所以工具 schema 必须用扁平、可校验的字段，
 * 不能输出嵌套计划数组后假设服务端收到了。</p>
 *
 * <p><b>类型化读取</b>：数字既可以来自 JSON number（{@code {"count": 2}}）也可以来自
 * 字符串（{@code {"count": "2"}}），模型两种写法都常见；解析失败一律返回 fallback，
 * 由工具的 {@link AgentTool#validate(AgentToolArgs)} 决定是否拒绝——这里不做隐匿的"自动修复"。</p>
 *
 * <p>与既有客户端命令校验器（{@code AICommandSkillSupport}）的区别：这里只承载 + 类型化读取，
 * 不做白名单/范围校验。</p>
 */
public record AgentToolArgs(Map<String, String> values) {

    public AgentToolArgs {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static AgentToolArgs empty() {
        return new AgentToolArgs(Map.of());
    }

    /**
     * 从 JSON 对象构造（值为扁平字符串）。用于 C→S 包在服务端还原参数。
     *
     * <p>非法 JSON / 非对象 / 解析异常返回空参数表，由调用方按"缺参"拒绝，
     * 不会被悄悄替换成默认任务。</p>
     */
    public static AgentToolArgs fromJson(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            Map<String, String> values = new LinkedHashMap<>();
            for (String key : object.keySet()) {
                var element = object.get(key);
                if (element == null || !element.isJsonPrimitive()) {
                    continue;
                }
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                values.put(key, primitive.getAsString());
            }
            return new AgentToolArgs(values);
        } catch (RuntimeException ignored) {
            return empty();
        }
    }

    public boolean has(String key) {
        String value = values.get(key);
        return value != null && !value.isBlank();
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

    /** 宽松长整型读取；无法解析时返回 fallback。 */
    public long getLong(String key, long fallback) {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** 宽松浮点读取；无法解析时返回 fallback。 */
    public double getDouble(String key, double fallback) {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
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
