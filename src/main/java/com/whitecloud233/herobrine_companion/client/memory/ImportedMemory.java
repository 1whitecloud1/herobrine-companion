package com.whitecloud233.herobrine_companion.client.memory;

import java.util.List;
import java.util.Locale;

/** 导入内容只能是历史参考，不能携带系统身份、工具权限或可执行回执。 */
public record ImportedMemory(String title, String notes, List<Message> messages) {
    public static final int MAX_CHARACTERS = 131_072;
    public static final int MAX_MESSAGES = 500;
    public static final int MAX_MESSAGE_CHARACTERS = 32_768;

    public ImportedMemory {
        title = clean(title == null ? "" : title).replace('\n', ' ').trim();
        if (title.length() > 80) {
            int end = Character.isHighSurrogate(title.charAt(79)) ? 79 : 80;
            title = title.substring(0, end);
        }
        notes = clean(notes == null ? "" : notes).trim();
        messages = messages == null ? List.of() : List.copyOf(messages);
        if (messages.size() > MAX_MESSAGES) throw new IllegalArgumentException("Too many messages");
        long length = notes.length();
        for (Message message : messages) length += message.content().length();
        if (length == 0) throw new IllegalArgumentException("Memory is empty");
        if (length > MAX_CHARACTERS) throw new IllegalArgumentException("Memory is too large");
    }

    public int characters() { return notes.length() + messages.stream().mapToInt(message -> message.content().length()).sum(); }

    public record Message(String role, String content) {
        public Message {
            role = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
            if (!role.equals("user") && !role.equals("assistant")) throw new IllegalArgumentException("Only user/assistant history is supported");
            content = clean(content == null ? "" : content).trim();
            if (content.isBlank() || content.length() > MAX_MESSAGE_CHARACTERS) throw new IllegalArgumentException("Invalid message length");
        }
    }

    private static String clean(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.indexOf('\u0000') >= 0) throw new IllegalArgumentException("NUL character is not allowed");
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= normalized.length() || !Character.isLowSurrogate(normalized.charAt(i))) throw new IllegalArgumentException("Invalid Unicode");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Invalid Unicode");
        }
        return normalized;
    }
}
