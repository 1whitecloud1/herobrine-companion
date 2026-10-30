package com.whitecloud233.modid.herobrine_companion.client.structure;

import com.whitecloud233.modid.herobrine_companion.structure.template.TemplateLimits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Reads only the file explicitly selected by the player, with a bound even if it grows during reading. */
public final class TemplateFileReader {
    public record FileData(String filename, byte[] bytes) { }
    private TemplateFileReader() { }
    public static FileData read(Path path) throws IOException {
        String filename = path.getFileName().toString();
        if (!filename.toLowerCase(Locale.ROOT).matches(".+\\.(nbt|litematic|schem|schematic)"))
            throw new IOException("Supported formats: .nbt, .litematic, .schem, .schematic");
        if (filename.length() > 255 || Files.size(path) > TemplateLimits.MAX_FILE_BYTES) throw new IOException("File exceeds 8 MiB or its name is too long");
        try (var stream = Files.newInputStream(path)) {
            byte[] bytes = stream.readNBytes(TemplateLimits.MAX_FILE_BYTES + 1);
            if (bytes.length == 0 || bytes.length > TemplateLimits.MAX_FILE_BYTES) throw new IOException("File must be between 1 byte and 8 MiB");
            return new FileData(filename, bytes);
        }
    }
}
