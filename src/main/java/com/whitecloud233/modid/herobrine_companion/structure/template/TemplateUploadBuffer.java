package com.whitecloud233.modid.herobrine_companion.structure.template;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** One ordered, bounded upload. Replays, gaps and changed metadata invalidate the transfer. */
public final class TemplateUploadBuffer {
    private final int requestId, total;
    private final String filename, name;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private int nextChunk;

    public TemplateUploadBuffer(int requestId, int total, String filename, String name) throws IOException {
        if (total < 1 || total > TemplateLimits.MAX_FILE_BYTES || filename.length() > 255 || name.length() > TemplateLimits.MAX_NAME)
            throw new IOException("Invalid upload size or name");
        this.requestId = requestId; this.total = total; this.filename = filename; this.name = name;
    }

    public boolean append(int requestId, int total, String filename, String name, int chunk, byte[] data) throws IOException {
        int expected = Math.min(TemplateLimits.CHUNK_BYTES, this.total - bytes.size());
        if (requestId != this.requestId || total != this.total || !filename.equals(this.filename) || !name.equals(this.name)
                || chunk != nextChunk || expected == 0 || data.length != expected) throw new IOException("Invalid upload chunk");
        bytes.write(data);
        nextChunk++;
        return bytes.size() == this.total;
    }

    public byte[] bytes() throws IOException {
        if (bytes.size() != total) throw new IOException("Upload is incomplete");
        return bytes.toByteArray();
    }
}
