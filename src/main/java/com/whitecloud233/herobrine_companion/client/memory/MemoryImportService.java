package com.whitecloud233.herobrine_companion.client.memory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

/** 用户选定本地文件的有界读取，不联网、不扫描目录、不负责保存会话。 */
public final class MemoryImportService {
    public static final int MAX_FILE_BYTES = 524_288;

    private MemoryImportService() { }

    public record Document(String source, ImportedMemory memory) { }

    public static ImportedMemory read(Path path) throws IOException { return readDocument(path).memory(); }

    public static Document readDocument(Path path) throws IOException {
        String name = path.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".txt") && !lower.endsWith(".md") && !lower.endsWith(".json")) {
            throw new IllegalArgumentException("Only .txt, .md and .json files are supported");
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Select a regular local file");
        if (Files.size(path) > MAX_FILE_BYTES) throw new IllegalArgumentException("File is larger than 512 KiB");
        byte[] bytes;
        try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(MAX_FILE_BYTES + 1); }
        if (bytes.length > MAX_FILE_BYTES) throw new IllegalArgumentException("File is larger than 512 KiB");
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) { throw new IllegalArgumentException("File must use UTF-8 encoding", e); }
        int extension = name.lastIndexOf('.');
        ImportedMemory memory = MemoryImportParser.parse(text, extension > 0 ? name.substring(0, extension) : name);
        return new Document(text, memory);
    }

    public static ImportedMemory pasted(String text, String title) {
        return MemoryImportParser.parse(text, title);
    }
}
