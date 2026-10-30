package com.whitecloud233.herobrine_companion.client.memory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 纯解析器：不读取文件、不选择会话、不修改存储。 */
public final class MemoryImportParser {
    private static final String EXPORT_HEADER = "Herobrine Companion Conversation Export";
    private static final Pattern EXPORT_MESSAGE = Pattern.compile("(?m)^\\[([^\\]\\r\\n]+)] (Player|Herobrine|User|Assistant|System):\\s*$");

    private MemoryImportParser() { }

    public static ImportedMemory parse(String source, String suggestedTitle) {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Memory is empty");
        if (source.length() > ImportedMemory.MAX_CHARACTERS) throw new IllegalArgumentException("Memory is too large");
        String text = source.startsWith("\uFEFF") ? source.substring(1) : source;
        text = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (text.startsWith("{") || text.startsWith("[")) return parseJson(text, suggestedTitle);
        if (text.startsWith(EXPORT_HEADER)) return parseExport(text, suggestedTitle);
        return new ImportedMemory(suggestedTitle, text, List.of());
    }

    private static ImportedMemory parseJson(String text, String suggestedTitle) {
        checkDepth(text);
        JsonElement root;
        try { root = JsonParser.parseString(text); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid JSON", e); }
        String title = suggestedTitle;
        String notes = "";
        JsonArray messages;
        if (root.isJsonArray()) messages = root.getAsJsonArray();
        else if (root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            for (String key : object.keySet()) {
                if (!Set.of("format", "version", "title", "summary", "memory", "messages").contains(key)) {
                    throw new IllegalArgumentException("Unsupported JSON field: " + key);
                }
            }
            if (object.has("format") && !"herobrine_memory".equals(string(object, "format"))) throw new IllegalArgumentException("Unsupported memory format");
            if (object.has("version") && !"1".equals(object.get("version").getAsString())) throw new IllegalArgumentException("Unsupported memory version");
            if (object.has("title")) title = string(object, "title");
            if (object.has("summary")) notes = string(object, "summary");
            if (object.has("memory")) notes += (notes.isEmpty() ? "" : "\n\n") + string(object, "memory");
            if (object.has("messages") && !object.get("messages").isJsonArray()) throw new IllegalArgumentException("messages must be an array");
            messages = object.has("messages") ? object.getAsJsonArray("messages") : new JsonArray();
        } else throw new IllegalArgumentException("Expected memory object or message array");
        if (messages.size() > ImportedMemory.MAX_MESSAGES) throw new IllegalArgumentException("Too many messages");
        List<ImportedMemory.Message> history = new ArrayList<>();
        for (JsonElement element : messages) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Message must be an object");
            JsonObject message = element.getAsJsonObject();
            if (!message.keySet().equals(Set.of("role", "content"))) throw new IllegalArgumentException("Message only supports role and content");
            history.add(new ImportedMemory.Message(string(message, "role"), string(message, "content")));
        }
        return new ImportedMemory(title, notes, history);
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " must be text");
        return element.getAsString();
    }

    private static ImportedMemory parseExport(String text, String suggestedTitle) {
        int messagesAt = text.indexOf("\nMessages\n========");
        if (messagesAt < 0) throw new IllegalArgumentException("Invalid conversation export");
        String header = text.substring(0, messagesAt);
        String title = header.lines().filter(line -> line.startsWith("Title: ")).map(line -> line.substring(7)).findFirst().orElse(suggestedTitle);
        String body = text.substring(messagesAt + "\nMessages\n========".length()).strip();
        Matcher matcher = EXPORT_MESSAGE.matcher(body);
        List<ImportedMemory.Message> messages = new ArrayList<>();
        String role = null;
        int contentStart = 0;
        while (matcher.find()) {
            if (role != null) messages.add(new ImportedMemory.Message(role, body.substring(contentStart, matcher.start()).strip()));
            if (matcher.group(2).equals("System")) throw new IllegalArgumentException("System messages cannot be imported as memory");
            role = matcher.group(2).equals("Herobrine") || matcher.group(2).equals("Assistant") ? "assistant" : "user";
            contentStart = matcher.end();
        }
        if (role != null) messages.add(new ImportedMemory.Message(role, body.substring(contentStart).strip()));
        return new ImportedMemory(title, "", messages);
    }

    private static void checkDepth(String text) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') {
                if (++depth > 32) throw new IllegalArgumentException("JSON nesting is too deep");
            } else if (c == '}' || c == ']') depth--;
        }
    }
}
