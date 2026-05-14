package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class CrossChatHistoryStore {
    public static final String KIND_PLAYER = "player";
    public static final String KIND_HB = "hb";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORE_TYPE = new TypeToken<StoreData>() {}.getType();
    private static final CrossChatHistoryStore INSTANCE = new CrossChatHistoryStore();
    private static final int MAX_HISTORY_ENTRIES = 240;
    private static final DateTimeFormatter EXPORT_FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter EXPORT_LINE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Object lock = new Object();
    private String loadedSessionKey;
    private File loadedFile;
    private StoreData storeData = new StoreData();

    public static CrossChatHistoryStore getInstance() {
        return INSTANCE;
    }

    public void appendEntry(String peerName, boolean hbMode, String speaker, String content, String kind) {
        synchronized (this.lock) {
            String sanitizedPeer = sanitizePeerName(peerName);
            String sanitizedSpeaker = sanitizeText(speaker);
            String sanitizedContent = sanitizeText(content);
            if (sanitizedPeer.isEmpty() || sanitizedContent.isEmpty()) {
                return;
            }

            HistoryThread thread = this.ensureHistoryThreadLocked(sanitizedPeer, hbMode);
            HistoryEntry entry = new HistoryEntry();
            entry.kind = kind == null || kind.isBlank() ? KIND_PLAYER : kind.trim().toLowerCase(Locale.ROOT);
            entry.speaker = sanitizedSpeaker.isEmpty() ? sanitizedPeer : sanitizedSpeaker;
            entry.content = sanitizedContent;
            entry.createdAt = System.currentTimeMillis();
            thread.entries.add(entry);
            if (thread.entries.size() > MAX_HISTORY_ENTRIES) {
                thread.entries = new ArrayList<>(thread.entries.subList(thread.entries.size() - MAX_HISTORY_ENTRIES, thread.entries.size()));
            }
            thread.updatedAt = entry.createdAt;
            this.saveLocked();
        }
    }


    public void saveAndClearSession() {
        synchronized (this.lock) {
            this.saveLocked();
            this.loadedSessionKey = null;
            this.loadedFile = null;
            this.storeData = new StoreData();
        }
    }

    public List<ArchiveSearchResult> searchPeerArchives(String query) {
        synchronized (this.lock) {
            String normalizedQuery = normalizeQuery(query);
            Map<String, ArchiveAccumulator> grouped = new HashMap<>();

            for (SessionStoreSnapshot snapshot : this.loadAllStoreSnapshotsLocked()) {
                if (snapshot.storeData == null || snapshot.storeData.histories == null) {
                    continue;
                }
                for (HistoryThread thread : snapshot.storeData.histories.values()) {
                    if (thread == null) {
                        continue;
                    }
                    String peerName = sanitizePeerName(thread.peerName);
                    if (peerName.isBlank()) {
                        continue;
                    }

                    String groupKey = peerName.toLowerCase(Locale.ROOT);
                    ArchiveAccumulator accumulator = grouped.computeIfAbsent(groupKey, ignored -> new ArchiveAccumulator(peerName));
                    accumulator.peerName = peerName;
                    accumulator.updatedAt = Math.max(accumulator.updatedAt, thread.updatedAt);

                    int messageCount = countValidEntries(thread.entries);
                    if (thread.hbMode) {
                        accumulator.hbMessageCount += messageCount;
                    } else {
                        accumulator.playerMessageCount += messageCount;
                    }

                    if (!normalizedQuery.isEmpty()) {
                        accumulator.matchCount += countArchiveHits(peerName, thread.entries, normalizedQuery, accumulator.peerNameMatched);
                        if (containsNormalized(peerName, normalizedQuery)) {
                            accumulator.peerNameMatched = true;
                        }
                    }
                }
            }

            List<ArchiveSearchResult> results = new ArrayList<>();
            for (ArchiveAccumulator accumulator : grouped.values()) {
                ArchiveSummary summary = new ArchiveSummary(
                        accumulator.peerName,
                        accumulator.playerMessageCount,
                        accumulator.hbMessageCount,
                        accumulator.updatedAt
                );
                if (normalizedQuery.isEmpty() || accumulator.matchCount > 0) {
                    results.add(new ArchiveSearchResult(summary, accumulator.matchCount));
                }
            }
            results.sort((left, right) -> {
                int updatedCompare = Long.compare(right.summary().updatedAt(), left.summary().updatedAt());
                if (updatedCompare != 0) {
                    return updatedCompare;
                }
                return left.summary().peerName().compareToIgnoreCase(right.summary().peerName());
            });
            return results;
        }
    }

    public ArchiveSummary getArchiveSummary(String peerName) {
        synchronized (this.lock) {
            String sanitizedPeer = sanitizePeerName(peerName);
            if (sanitizedPeer.isEmpty()) {
                return null;
            }

            int playerCount = 0;
            int hbCount = 0;
            long updatedAt = 0L;
            for (HistoryThread thread : this.collectHistoryThreadsLocked(sanitizedPeer, false)) {
                playerCount += countValidEntries(thread.entries);
                updatedAt = Math.max(updatedAt, thread.updatedAt);
            }
            for (HistoryThread thread : this.collectHistoryThreadsLocked(sanitizedPeer, true)) {
                hbCount += countValidEntries(thread.entries);
                updatedAt = Math.max(updatedAt, thread.updatedAt);
            }
            if (playerCount <= 0 && hbCount <= 0) {
                return null;
            }
            return new ArchiveSummary(sanitizedPeer, playerCount, hbCount, updatedAt);
        }
    }

    public List<HistoryEntrySnapshot> getEntries(String peerName, boolean hbMode) {
        synchronized (this.lock) {
            String sanitizedPeer = sanitizePeerName(peerName);
            if (sanitizedPeer.isEmpty()) {
                return List.of();
            }
            return this.getEntriesLocked(sanitizedPeer, hbMode);
        }
    }

    public void deletePeerArchive(String peerName) {
        synchronized (this.lock) {
            String sanitizedPeer = sanitizePeerName(peerName);
            if (sanitizedPeer.isEmpty()) {
                return;
            }
            this.deletePeerArchiveLocked(sanitizedPeer, null);
        }
    }

    public void deletePeerArchive(String peerName, boolean hbMode) {
        synchronized (this.lock) {
            String sanitizedPeer = sanitizePeerName(peerName);
            if (sanitizedPeer.isEmpty()) {
                return;
            }
            this.deletePeerArchiveLocked(sanitizedPeer, hbMode);
        }
    }

    public List<File> exportPeerHistories(String peerName) {
        synchronized (this.lock) {
            String sanitizedPeer = sanitizePeerName(peerName);
            if (sanitizedPeer.isEmpty()) {
                return List.of();
            }

            List<File> exported = new ArrayList<>();
            File playerHistory = this.exportHistoryLocked(sanitizedPeer, false);
            if (playerHistory != null) {
                exported.add(playerHistory);
            }

            File hbHistory = this.exportHistoryLocked(sanitizedPeer, true);
            if (hbHistory != null) {
                exported.add(hbHistory);
            }
            return exported;
        }
    }

    public File exportPeerHistory(String peerName, boolean hbMode) {
        synchronized (this.lock) {
            String sanitizedPeer = sanitizePeerName(peerName);
            if (sanitizedPeer.isEmpty()) {
                return null;
            }
            return this.exportHistoryLocked(sanitizedPeer, hbMode);
        }
    }

    private HistoryThread ensureHistoryThreadLocked(String peerName, boolean hbMode) {
        this.ensureSessionLoadedLocked();
        if (this.storeData.histories == null) {
            this.storeData.histories = new HashMap<>();
        }

        String key = buildHistoryKey(peerName, hbMode);
        HistoryThread thread = this.storeData.histories.computeIfAbsent(key, ignored -> new HistoryThread());
        if (thread.entries == null) {
            thread.entries = new ArrayList<>();
        }
        thread.peerName = peerName;
        thread.hbMode = hbMode;
        return thread;
    }

    private HistoryThread getHistoryThreadLocked(String peerName, boolean hbMode) {
        this.ensureSessionLoadedLocked();
        if (this.storeData.histories == null) {
            this.storeData.histories = new HashMap<>();
        }
        return this.storeData.histories.get(buildHistoryKey(peerName, hbMode));
    }

    private List<HistoryEntrySnapshot> getEntriesLocked(String peerName, boolean hbMode) {
        List<HistoryEntrySnapshot> snapshots = new ArrayList<>();
        for (HistoryThread thread : this.collectHistoryThreadsLocked(peerName, hbMode)) {
            if (thread.entries == null || thread.entries.isEmpty()) {
                continue;
            }
            for (HistoryEntry entry : thread.entries) {
                if (entry == null || entry.content == null || entry.content.isBlank()) {
                    continue;
                }
                snapshots.add(new HistoryEntrySnapshot(entry.kind, entry.speaker, entry.content, entry.createdAt));
            }
        }
        snapshots.sort(Comparator.comparingLong(HistoryEntrySnapshot::createdAt));
        return snapshots;
    }

    private List<HistoryThread> collectHistoryThreadsLocked(String peerName, boolean hbMode) {
        List<HistoryThread> threads = new ArrayList<>();
        String key = buildHistoryKey(peerName, hbMode);
        for (SessionStoreSnapshot snapshot : this.loadAllStoreSnapshotsLocked()) {
            if (snapshot.storeData == null || snapshot.storeData.histories == null) {
                continue;
            }
            HistoryThread thread = snapshot.storeData.histories.get(key);
            if (thread != null) {
                threads.add(thread);
            }
        }
        return threads;
    }

    private void ensureSessionLoadedLocked() {
        String sessionKey = this.resolveSessionKey();
        if (sessionKey == null || sessionKey.isBlank()) {
            if (this.loadedFile == null) {
                this.storeData = new StoreData();
            }
            return;
        }

        if (Objects.equals(sessionKey, this.loadedSessionKey) && this.loadedFile != null) {
            return;
        }

        this.saveLocked();
        this.loadedSessionKey = sessionKey;
        this.loadedFile = this.resolveSessionFile(sessionKey);
        this.storeData = new StoreData();

        if (this.loadedFile.exists()) {
            try {
                String json = Files.readString(this.loadedFile.toPath(), StandardCharsets.UTF_8);
                StoreData loaded = GSON.fromJson(json, STORE_TYPE);
                if (loaded != null) {
                    this.storeData = loaded;
                }
            } catch (Exception e) {
                e.printStackTrace();
                this.storeData = new StoreData();
            }
        }

        this.normalizeStoreLocked();
        this.saveLocked();
    }

    private void normalizeStoreLocked() {
        this.storeData = normalizeStore(this.storeData);
    }

    private File exportHistoryLocked(String peerName, boolean hbMode) {
        List<HistoryEntrySnapshot> entries = this.getEntriesLocked(peerName, hbMode);
        if (entries.isEmpty()) {
            return null;
        }

        long updatedAt = 0L;
        List<String> sessionKeys = new ArrayList<>();
        for (SessionStoreSnapshot snapshot : this.loadAllStoreSnapshotsLocked()) {
            if (snapshot.storeData == null || snapshot.storeData.histories == null) {
                continue;
            }
            HistoryThread thread = snapshot.storeData.histories.get(buildHistoryKey(peerName, hbMode));
            if (thread == null || thread.entries == null || thread.entries.isEmpty()) {
                continue;
            }
            updatedAt = Math.max(updatedAt, thread.updatedAt);
            if (snapshot.sessionKey != null && !snapshot.sessionKey.isBlank() && !sessionKeys.contains(snapshot.sessionKey)) {
                sessionKeys.add(snapshot.sessionKey);
            }
        }

        File exportDir = this.resolveExportDirectory();
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            return null;
        }

        File output = new File(exportDir, this.buildExportFileName(peerName, hbMode, updatedAt));
        try (BufferedWriter writer = Files.newBufferedWriter(output.toPath(), StandardCharsets.UTF_8)) {
            writer.write("Herobrine Companion Cross Chat Export");
            writer.newLine();
            writer.write("Session: " + (sessionKeys.isEmpty() ? "unknown" : (sessionKeys.size() == 1 ? sessionKeys.get(0) : "multiple")));
            writer.newLine();
            if (sessionKeys.size() > 1) {
                writer.write("Sessions: " + String.join(", ", sessionKeys));
                writer.newLine();
            }
            writer.write("Peer: " + peerName);
            writer.newLine();
            writer.write("Mode: " + (hbMode ? "HB↔HB" : "Player↔HB"));
            writer.newLine();
            writer.write("Updated: " + this.formatTimestamp(updatedAt));
            writer.newLine();
            writer.newLine();
            writer.write("Messages");
            writer.newLine();
            writer.write("========");
            writer.newLine();

            for (HistoryEntrySnapshot entry : entries) {
                writer.write("[" + this.formatTimestamp(entry.createdAt()) + "] ");
                writer.write((entry.speaker() == null || entry.speaker().isBlank() ? peerName : entry.speaker()) + ": ");
                writer.write(entry.content() == null ? "" : entry.content());
                writer.newLine();
            }
            return output;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveLocked() {
        if (this.loadedFile == null) {
            return;
        }

        try {
            File parent = this.loadedFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (var writer = Files.newBufferedWriter(this.loadedFile.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(this.storeData, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String resolveSessionKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return null;
        }

        String worldIdentifier = null;
        if (mc.getSingleplayerServer() != null) {
            worldIdentifier = "local_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        } else if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null && !mc.getCurrentServer().ip.isBlank()) {
            worldIdentifier = "server_" + mc.getCurrentServer().ip;
        }

        if (worldIdentifier == null || worldIdentifier.isBlank()) {
            return null;
        }
        return worldIdentifier.replaceAll("[^a-zA-Z0-9\\-_]", "_").toLowerCase(Locale.ROOT);
    }

    private File resolveSessionFile(String sessionKey) {
        File dir = this.resolveHistoryDirectory();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, sessionKey + ".json");
    }

    /**
     * Cross-session chat archives are intentionally persisted on the local client only.
     * This uses the current physical game's config directory, so each participant keeps
     * their own copy on their own disk instead of storing the transcript on the server.
     */
    private File resolveHistoryDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("herobrine_companion").resolve("cross_chat_histories").toFile();
    }

    private File resolveExportDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("herobrine_companion").resolve("cross_chat_exports").toFile();
    }

    private String buildExportFileName(String peerName, boolean hbMode, long updatedAt) {
        String timestamp = EXPORT_FILE_TIME.format(Instant.ofEpochMilli(updatedAt > 0 ? updatedAt : System.currentTimeMillis()).atZone(ZoneId.systemDefault()));
        String safePeer = this.sanitizeFileName(peerName);
        return timestamp + "_" + safePeer + "_" + (hbMode ? "hb_to_hb" : "player_to_hb") + ".txt";
    }

    private List<SessionStoreSnapshot> loadAllStoreSnapshotsLocked() {
        this.ensureSessionLoadedLocked();
        List<SessionStoreSnapshot> snapshots = new ArrayList<>();
        File dir = this.resolveHistoryDirectory();
        File[] files = dir.listFiles((ignored, name) -> name != null && name.toLowerCase(Locale.ROOT).endsWith(".json"));
        boolean loadedIncluded = false;
        if (files != null) {
            List<File> sortedFiles = new ArrayList<>();
            for (File file : files) {
                if (file != null && file.isFile()) {
                    sortedFiles.add(file);
                }
            }
            sortedFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : sortedFiles) {
                if (this.loadedFile != null && this.loadedFile.equals(file)) {
                    snapshots.add(new SessionStoreSnapshot(this.loadedSessionKey, file, normalizeStore(this.storeData)));
                    loadedIncluded = true;
                    continue;
                }
                StoreData loaded = this.readStoreData(file);
                if (loaded != null) {
                    snapshots.add(new SessionStoreSnapshot(this.sessionKeyFromFile(file), file, loaded));
                }
            }
        }
        if (!loadedIncluded && this.loadedFile != null) {
            snapshots.add(new SessionStoreSnapshot(this.loadedSessionKey, this.loadedFile, normalizeStore(this.storeData)));
        }
        return snapshots;
    }

    private StoreData readStoreData(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return normalizeStore(GSON.fromJson(json, STORE_TYPE));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveStoreData(File file, StoreData data) {
        if (file == null || data == null) {
            return;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (var writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(normalizeStore(data), writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deletePeerArchiveLocked(String peerName, Boolean hbModeFilter) {
        List<SessionStoreSnapshot> snapshots = this.loadAllStoreSnapshotsLocked();
        String playerKey = buildHistoryKey(peerName, false);
        String hbKey = buildHistoryKey(peerName, true);
        for (SessionStoreSnapshot snapshot : snapshots) {
            if (snapshot.storeData == null || snapshot.storeData.histories == null) {
                continue;
            }
            boolean changed = false;
            if (hbModeFilter == null || !hbModeFilter) {
                changed |= snapshot.storeData.histories.remove(playerKey) != null;
            }
            if (hbModeFilter == null || hbModeFilter) {
                changed |= snapshot.storeData.histories.remove(hbKey) != null;
            }
            if (!changed) {
                continue;
            }
            if (snapshot.file != null && this.loadedFile != null && this.loadedFile.equals(snapshot.file)) {
                this.storeData = normalizeStore(snapshot.storeData);
                this.saveLocked();
            } else {
                this.saveStoreData(snapshot.file, snapshot.storeData);
            }
        }
    }

    private String sessionKeyFromFile(File file) {
        if (file == null) {
            return "";
        }
        String name = file.getName();
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    private static StoreData normalizeStore(StoreData data) {
        if (data == null) {
            data = new StoreData();
        }
        if (data.histories == null) {
            data.histories = new HashMap<>();
        }
        data.histories.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null);
        for (HistoryThread thread : data.histories.values()) {
            if (thread.entries == null) {
                thread.entries = new ArrayList<>();
            }
            thread.peerName = sanitizePeerName(thread.peerName);
            thread.entries.removeIf(entry -> entry == null || entry.content == null || entry.content.isBlank());
        }
        return data;
    }

    private String sanitizeFileName(String input) {
        String sanitized = (input == null ? "cross_chat" : input)
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .trim();
        if (sanitized.isBlank()) {
            return "cross_chat";
        }
        return sanitized.length() > 48 ? sanitized.substring(0, 48) : sanitized;
    }

    private String formatTimestamp(long timestamp) {
        long safeTimestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        return EXPORT_LINE_TIME.format(Instant.ofEpochMilli(safeTimestamp).atZone(ZoneId.systemDefault()));
    }

    private static int countValidEntries(List<HistoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (HistoryEntry entry : entries) {
            if (entry != null && entry.content != null && !entry.content.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static int countArchiveHits(String peerName, List<HistoryEntry> entries, String normalizedQuery, boolean peerAlreadyMatched) {
        int hits = 0;
        if (!peerAlreadyMatched && containsNormalized(peerName, normalizedQuery)) {
            hits++;
        }
        if (entries == null || normalizedQuery.isEmpty()) {
            return hits;
        }

        for (HistoryEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            if (containsNormalized(entry.speaker, normalizedQuery) || containsNormalized(entry.content, normalizedQuery)) {
                hits++;
            }
        }
        return hits;
    }

    private static boolean containsNormalized(String source, String normalizedQuery) {
        return source != null && normalizedQuery != null && !normalizedQuery.isEmpty()
                && source.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private static String normalizeQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private static String buildHistoryKey(String peerName, boolean hbMode) {
        return (hbMode ? "hb" : "player") + "|" + sanitizePeerName(peerName).toLowerCase(Locale.ROOT);
    }

    private static String sanitizePeerName(String peerName) {
        return sanitizeText(peerName);
    }

    private static String sanitizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static class StoreData {
        Map<String, HistoryThread> histories = new HashMap<>();
    }

    private static class HistoryThread {
        String peerName;
        boolean hbMode;
        long updatedAt;
        List<HistoryEntry> entries = new ArrayList<>();
    }

    private static class HistoryEntry {
        String kind;
        String speaker;
        String content;
        long createdAt;
    }

    public static final class HistoryEntrySnapshot {
        private final String kind;
        private final String speaker;
        private final String content;
        private final long createdAt;

        public HistoryEntrySnapshot(String kind, String speaker, String content, long createdAt) {
            this.kind = kind;
            this.speaker = speaker;
            this.content = content;
            this.createdAt = createdAt;
        }

        public String kind() {
            return this.kind;
        }

        public String speaker() {
            return this.speaker;
        }

        public String content() {
            return this.content;
        }

        public long createdAt() {
            return this.createdAt;
        }
    }

    public static final class ArchiveSummary {
        private final String peerName;
        private final int playerMessageCount;
        private final int hbMessageCount;
        private final long updatedAt;

        public ArchiveSummary(String peerName, int playerMessageCount, int hbMessageCount, long updatedAt) {
            this.peerName = peerName;
            this.playerMessageCount = playerMessageCount;
            this.hbMessageCount = hbMessageCount;
            this.updatedAt = updatedAt;
        }

        public String peerName() {
            return this.peerName;
        }

        public int playerMessageCount() {
            return this.playerMessageCount;
        }

        public int hbMessageCount() {
            return this.hbMessageCount;
        }

        public long updatedAt() {
            return this.updatedAt;
        }
    }

    public static final class ArchiveSearchResult {
        private final ArchiveSummary summary;
        private final int matchCount;

        public ArchiveSearchResult(ArchiveSummary summary, int matchCount) {
            this.summary = summary;
            this.matchCount = matchCount;
        }

        public ArchiveSummary summary() {
            return this.summary;
        }

        public int matchCount() {
            return this.matchCount;
        }
    }

    private static final class ArchiveAccumulator {
        private String peerName;
        private int playerMessageCount;
        private int hbMessageCount;
        private long updatedAt;
        private int matchCount;
        private boolean peerNameMatched;

        private ArchiveAccumulator(String peerName) {
            this.peerName = peerName;
        }
    }

    private static final class SessionStoreSnapshot {
        private final String sessionKey;
        private final File file;
        private final StoreData storeData;

        private SessionStoreSnapshot(String sessionKey, File file, StoreData storeData) {
            this.sessionKey = sessionKey;
            this.file = file;
            this.storeData = storeData;
        }
    }
}

