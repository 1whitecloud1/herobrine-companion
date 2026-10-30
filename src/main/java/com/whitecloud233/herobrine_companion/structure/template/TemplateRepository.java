package com.whitecloud233.herobrine_companion.structure.template;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.OutputStream;
import java.io.DataInputStream;
import java.util.zip.GZIPInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Per-player durable catalog and generated structures; all paths derive from server-generated IDs. */
public final class TemplateRepository {
    private static final Gson JSON = new Gson();
    private final Path catalog, generated, compact;
    private final String prefix;

    public TemplateRepository(Path worldRoot, UUID owner) {
        catalog = worldRoot.resolve("herobrine_companion/templates/" + owner + ".json");
        generated = worldRoot.resolve("generated/herobrine_companion/structures");
        compact = worldRoot.resolve("herobrine_companion/blueprints");
        prefix = "herobrine_companion:uploaded/" + owner.toString().replace("-", "") + "/";
    }

    public String id(String digest) {
        if (!digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid digest");
        return prefix + digest;
    }

    public List<TemplateEntry> list() throws IOException {
        if (!Files.exists(catalog)) return List.of();
        if (Files.size(catalog) > 256 * 1024) throw new IOException("Template catalog is oversized");
        try {
            JsonArray array = JsonParser.parseString(Files.readString(catalog, StandardCharsets.UTF_8)).getAsJsonArray();
            if (array.size() > TemplateLimits.MAX_TEMPLATES) throw new IOException("Too many catalog entries");
            List<TemplateEntry> result = new ArrayList<>();
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (var value : array) {
                TemplateEntry entry = JSON.fromJson(value, TemplateEntry.class);
                path(entry.id());
                long volume = TemplateLimits.volume(entry.x(), entry.y(), entry.z());
                if (entry.name() == null || entry.name().isBlank() || entry.name().length() > TemplateLimits.MAX_NAME
                        || entry.format() == null || entry.format().length() > 32 || entry.blocks() < 0 || entry.blocks() > volume
                        || !ids.add(entry.id())) throw new IOException("Invalid template catalog entry");
                if (Files.isRegularFile(path(entry.id())) || Files.isRegularFile(compactPath(entry.id()))) result.add(entry);
            }
            return List.copyOf(result);
        } catch (RuntimeException error) { throw new IOException("Invalid template catalog", error); }
    }

    public void save(TemplateEntry entry, CompoundTag structure) throws IOException {
        try (Staged staged = stage(entry, structure)) { commit(staged); }
    }

    /** Compression happens on the dedicated template worker, never on a server tick. */
    public Staged stage(TemplateEntry entry, CompoundTag structure) throws IOException {
        Path target = structure.contains(CompactTemplateBuilder.REGIONS) ? compactPath(entry.id()) : path(entry.id());
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "template-", ".tmp");
        try {
            try (OutputStream stream = Files.newOutputStream(temp)) { NbtIo.writeCompressed(structure, stream); }
            return new Staged(entry, temp, target);
        } catch (Throwable error) { Files.deleteIfExists(temp); throw error; }
    }

    public record Staged(TemplateEntry entry, Path temp, Path target) implements AutoCloseable {
        @Override public void close() throws IOException { Files.deleteIfExists(temp); }
    }

    /** Only atomic file publication and the small catalog update remain on the server thread. */
    public void commit(Staged staged) throws IOException {
        TemplateEntry entry = staged.entry();
        List<TemplateEntry> entries = new ArrayList<>(list());
        boolean existed = entries.removeIf(old -> old.id().equals(entry.id()));
        if (entries.size() >= TemplateLimits.MAX_TEMPLATES) throw new IOException("Template library is full (128)");
        move(staged.temp(), staged.target());
        entries.add(entry);
        try { saveCatalog(entries); }
        catch (IOException error) { if (!existed) Files.deleteIfExists(staged.target()); throw error; }
    }

    /** Called on the template worker; old generated vanilla structures remain readable. */
    public CompoundTag read(String id) throws IOException {
        if (list().stream().noneMatch(entry -> entry.id().equals(id))) throw new IOException("Template is not in your library");
        Path file = Files.isRegularFile(compactPath(id)) ? compactPath(id) : path(id);
        try (var input = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            return NbtIo.read(input, NbtAccounter.create(TemplateLimits.MAX_NBT_BYTES));
        }
    }

    public void delete(String id) throws IOException {
        List<TemplateEntry> entries = new ArrayList<>(list());
        if (!entries.removeIf(entry -> entry.id().equals(id))) throw new IOException("Template is not in your library");
        Path target = Files.isRegularFile(compactPath(id)) ? compactPath(id) : path(id);
        Path backup = target.resolveSibling(target.getFileName() + ".deleting");
        Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
        try { saveCatalog(entries); }
        catch (IOException error) { move(backup, target); throw error; }
        Files.deleteIfExists(backup);
        Files.deleteIfExists(path(id));
    }

    private Path path(String id) throws IOException {
        if (id == null || !id.startsWith(prefix) || !id.substring(prefix.length()).matches("[0-9a-f]{64}"))
            throw new IOException("Invalid template owner or id");
        ResourceLocation location = ResourceLocation.parse(id);
        Path path = generated.resolve(location.getPath() + ".nbt").normalize();
        if (!path.startsWith(generated.normalize())) throw new IOException("Invalid template path");
        return path;
    }

    private Path compactPath(String id) throws IOException {
        Path relative = generated.relativize(path(id));
        Path result = compact.resolve(relative).normalize();
        if (!result.startsWith(compact.normalize())) throw new IOException("Invalid template path");
        return result;
    }

    private void saveCatalog(List<TemplateEntry> entries) throws IOException {
        Files.createDirectories(catalog.getParent());
        Path temp = Files.createTempFile(catalog.getParent(), "catalog-", ".tmp");
        try {
            Files.writeString(temp, JSON.toJson(entries), StandardCharsets.UTF_8);
            move(temp, catalog);
        } finally { Files.deleteIfExists(temp); }
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
