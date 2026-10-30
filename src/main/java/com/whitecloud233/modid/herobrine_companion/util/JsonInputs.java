package com.whitecloud233.modid.herobrine_companion.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.GsonHelper;

/** JSON 输入契约：区分宽容的工具参数读取与保留原有类型错误的可选读取。 */
public final class JsonInputs {
    private JsonInputs() { }

    public static String string(JsonObject object, String name, String fallback) {
        if (object == null || !object.has(name) || !object.get(name).isJsonPrimitive()) return fallback;
        try {
            return GsonHelper.getAsString(object, name, fallback);
        } catch (RuntimeException malformed) {
            return fallback;
        }
    }

    public static String optionalString(JsonObject object, String name, String fallback) {
        JsonElement value = object == null ? null : object.get(name);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    /** 模板已经带引号时，只插入标准 JSON 字符串的内容部分。 */
    public static String escapeStringContent(String value) {
        String json = new JsonPrimitive(value == null ? "" : value).toString();
        return json.substring(1, json.length() - 1);
    }
}
