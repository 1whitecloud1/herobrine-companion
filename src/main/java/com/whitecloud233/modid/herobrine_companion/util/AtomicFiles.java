package com.whitecloud233.modid.herobrine_companion.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 原子文本落盘：先写同目录临时文件，再 {@code ATOMIC_MOVE} 替换目标。
 *
 * <p><b>为什么必须有这一份</b>：项目里原先有 6 处各写各的 JSON 落盘，其中只有
 * {@code ConversationStore} 走了原子替换，其余（跨服聊天历史、AI 配置、TTS 配置、
 * 上下文窗口缓存、姿势预设）都是 {@code newBufferedWriter} / {@code FileOutputStream}
 * 直接覆盖原文件。写到一半崩溃或断电，原文件就被截断 —— 跨服聊天历史尤其致命，
 * 那是一个文件装所有 peer 的档案。</p>
 *
 * <p>与 {@code client.memory.ConversationFileWriter} 的关系：那个类被
 * {@code MemoryArchitectureChecks} 硬性约束（必须被 {@code ConversationStore} 调用），
 * 且只服务会话存档，语义更窄。这里提供与包无关的通用版本，供其余 5 处共用。</p>
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /** 以 UTF-8 原子写入文本。父目录不存在时自动创建。 */
    public static void writeUtf8(Path destination, String content) throws IOException {
        Path absolute = destination.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** 用 Gson 序列化后原子写入（等价于 {@code gson.toJson(value, writer)}，但不会截断原文件）。 */
    public static void writeJson(Path destination, com.google.gson.Gson gson, Object value) throws IOException {
        writeUtf8(destination, gson.toJson(value));
    }
}
