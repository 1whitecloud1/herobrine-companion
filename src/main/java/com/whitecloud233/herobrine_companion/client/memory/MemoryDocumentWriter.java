package com.whitecloud233.herobrine_companion.client.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** 可再次导入的单会话 JSON，不包含玩家身份、服务器地址、密钥或执行权限。 */
public final class MemoryDocumentWriter {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    private MemoryDocumentWriter() { }

    public static String encode(ImportedMemory memory) {
        return encode(memory.title(), memory.notes(), memory.messages());
    }

    public static String encode(String title, String notes, java.util.List<ImportedMemory.Message> history) {
        JsonObject object = new JsonObject();
        object.addProperty("format", "herobrine_memory");
        object.addProperty("version", 1);
        object.addProperty("title", title);
        object.addProperty("memory", notes);
        JsonArray messages = new JsonArray();
        for (ImportedMemory.Message message : history) {
            JsonObject item = new JsonObject();
            item.addProperty("role", message.role());
            item.addProperty("content", message.content());
            messages.add(item);
        }
        object.add("messages", messages);
        return JSON.toJson(object);
    }
}
