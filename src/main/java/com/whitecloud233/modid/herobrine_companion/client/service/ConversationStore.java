package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
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
import java.util.UUID;

public class ConversationStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORE_TYPE = new TypeToken<StoreData>() {}.getType();
    private static final ConversationStore INSTANCE = new ConversationStore();
    private static final DateTimeFormatter EXPORT_FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter EXPORT_LINE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Object lock = new Object();
    private String loadedSessionKey;
    private File loadedFile;
    private StoreData storeData = new StoreData();

    public static ConversationStore getInstance() {
        return INSTANCE;
    }

    public void loadForCurrentSession() {
        synchronized (this.lock) {
            String sessionKey = this.resolveSessionKey();
            if (sessionKey == null || sessionKey.isBlank()) {
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
                try (FileReader reader = new FileReader(this.loadedFile)) {
                    StoreData loaded = GSON.fromJson(reader, STORE_TYPE);
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
    }

    public void saveAndClearSession() {
        synchronized (this.lock) {
            this.saveLocked();
            this.loadedSessionKey = null;
            this.loadedFile = null;
            this.storeData = new StoreData();
        }
    }

    public ConversationSummary ensureActiveConversation(UUID playerUUID) {
        synchronized (this.lock) {
            PlayerConversationState state = this.ensurePlayerStateLocked(playerUUID);
            ConversationThread active = this.getActiveConversationLocked(state);
            this.saveLocked();
            return this.toSummary(active, true);
        }
    }

    public List<ConversationSummary> listConversations(UUID playerUUID) {
        synchronized (this.lock) {
            PlayerConversationState state = this.ensurePlayerStateLocked(playerUUID);
            ConversationThread active = this.getActiveConversationLocked(state);
            List<ConversationSummary> summaries = new ArrayList<>();
            for (ConversationThread conversation : state.conversations) {
                summaries.add(this.toSummary(conversation, conversation.id.equals(active.id)));
            }
            summaries.sort(Comparator.comparingLong(ConversationSummary::updatedAt).reversed());
            return summaries;
        }
    }

    public List<ConversationSearchResult> searchConversations(UUID playerUUID, String query) {
        synchronized (this.lock) {
            PlayerConversationState state = this.ensurePlayerStateLocked(playerUUID);
            ConversationThread active = this.getActiveConversationLocked(state);
            String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

            List<ConversationSearchResult> results = new ArrayList<>();
            for (ConversationThread conversation : state.conversations) {
                int hitCount = this.countSearchHits(conversation, normalizedQuery);
                if (normalizedQuery.isEmpty() || hitCount > 0) {
                    results.add(new ConversationSearchResult(
                            this.toSummary(conversation, conversation.id.equals(active.id)),
                            hitCount
                    ));
                }
            }
            results.sort(Comparator
                    .comparingInt(ConversationSearchResult::matchCount).reversed()
                    .thenComparing(result -> result.summary().updatedAt(), Comparator.reverseOrder()));
            return results;
        }
    }

    public ConversationSummary createConversation(UUID playerUUID) {
        synchronized (this.lock) {
            PlayerConversationState state = this.ensurePlayerStateLocked(playerUUID);
            ConversationThread conversation = this.createConversationLocked(state);
            this.saveLocked();
            return this.toSummary(conversation, true);
        }
    }

    public void setActiveConversation(UUID playerUUID, String conversationId) {
        synchronized (this.lock) {
            PlayerConversationState state = this.ensurePlayerStateLocked(playerUUID);
            ConversationThread conversation = this.findConversationLocked(state, conversationId);
            if (conversation == null) {
                return;
            }

            state.activeConversationId = conversation.id;
            this.touchConversationLocked(state, conversation, false);
            this.saveLocked();
        }
    }

    public void deleteConversation(UUID playerUUID, String conversationId) {
        synchronized (this.lock) {
            PlayerConversationState state = this.ensurePlayerStateLocked(playerUUID);
            state.conversations.removeIf(conversation -> Objects.equals(conversation.id, conversationId));
            this.normalizePlayerStateLocked(state);
            this.saveLocked();
        }
    }

    public File exportActiveConversation(UUID playerUUID) {
        synchronized (this.lock) {
            PlayerConversationState state = this.ensurePlayerStateLocked(playerUUID);
            ConversationThread active = this.getActiveConversationLocked(state);
            return this.exportConversationLocked(active);
        }
    }

    public String getActiveConversationTitle(UUID playerUUID) {
        synchronized (this.lock) {
            PlayerConversationState state = this.ensurePlayerStateLocked(playerUUID);
            return this.getActiveConversationLocked(state).title;
        }
    }

    public List<ConversationMessageSnapshot> getActiveConversationMessages(UUID playerUUID) {
        synchronized (this.lock) {
            PlayerConversationState state = this.ensurePlayerStateLocked(playerUUID);
            ConversationThread active = this.getActiveConversationLocked(state);
            List<ConversationMessageSnapshot> snapshots = new ArrayList<>();
            for (ConversationMessage message : active.messages) {
                snapshots.add(new ConversationMessageSnapshot(message.role, message.content));
            }
            return snapshots;
        }
    }

    public void appendMessage(UUID playerUUID, String role, String content, boolean allowAutoTitle) {
        synchronized (this.lock) {
            if (content == null || content.isBlank()) {
                return;
            }

            PlayerConversationState state = this.ensurePlayerStateLocked(playerUUID);
            ConversationThread active = this.getActiveConversationLocked(state);
            ConversationMessage message = new ConversationMessage();
            message.role = role == null || role.isBlank() ? "user" : role.trim();
            message.content = content.trim();
            message.createdAt = System.currentTimeMillis();
            active.messages.add(message);

            if (allowAutoTitle && active.autoTitle && "user".equalsIgnoreCase(message.role)) {
                active.title = this.makeConversationTitle(message.content);
                active.autoTitle = false;
            }

            this.touchConversationLocked(state, active, true);
            this.saveLocked();
        }
    }

    public void clearActiveConversation(UUID playerUUID) {
        synchronized (this.lock) {
            PlayerConversationState state = this.ensurePlayerStateLocked(playerUUID);
            ConversationThread active = this.getActiveConversationLocked(state);
            active.messages.clear();
            active.updatedAt = System.currentTimeMillis();
            active.autoTitle = true;
            active.title = this.buildDefaultConversationTitle(state);
            this.touchConversationLocked(state, active, false);
            this.saveLocked();
        }
    }

    private PlayerConversationState ensurePlayerStateLocked(UUID playerUUID) {
        this.ensureSessionLoadedLocked();
        String playerKey = playerUUID == null ? "unknown_player" : playerUUID.toString();
        if (this.storeData.players == null) {
            this.storeData.players = new HashMap<>();
        }

        PlayerConversationState state = this.storeData.players.computeIfAbsent(playerKey, key -> new PlayerConversationState());
        this.normalizePlayerStateLocked(state);
        return state;
    }

    private void ensureSessionLoadedLocked() {
        if (this.loadedFile == null) {
            this.loadForCurrentSession();
        }
        if (this.storeData == null) {
            this.storeData = new StoreData();
        }
    }

    private void normalizeStoreLocked() {
        if (this.storeData == null) {
            this.storeData = new StoreData();
        }
        if (this.storeData.players == null) {
            this.storeData.players = new HashMap<>();
        }

        for (PlayerConversationState state : this.storeData.players.values()) {
            this.normalizePlayerStateLocked(state);
        }
    }

    private void normalizePlayerStateLocked(PlayerConversationState state) {
        if (state.conversations == null) {
            state.conversations = new ArrayList<>();
        }

        state.conversations.removeIf(conversation -> conversation == null || conversation.id == null || conversation.id.isBlank());
        for (ConversationThread conversation : state.conversations) {
            if (conversation.messages == null) {
                conversation.messages = new ArrayList<>();
            }
            if (conversation.title == null || conversation.title.isBlank()) {
                conversation.title = this.fallbackConversationPrefix();
            }
        }

        if (state.conversations.isEmpty()) {
            ConversationThread created = this.createConversationLocked(state);
            state.activeConversationId = created.id;
            return;
        }

        if (state.activeConversationId == null || this.findConversationLocked(state, state.activeConversationId) == null) {
            state.activeConversationId = state.conversations.get(0).id;
        }
    }

    private ConversationThread createConversationLocked(PlayerConversationState state) {
        ConversationThread conversation = new ConversationThread();
        conversation.id = UUID.randomUUID().toString();
        conversation.title = this.buildDefaultConversationTitle(state);
        conversation.autoTitle = true;
        conversation.createdAt = System.currentTimeMillis();
        conversation.updatedAt = conversation.createdAt;
        conversation.messages = new ArrayList<>();
        state.conversations.add(0, conversation);
        state.activeConversationId = conversation.id;
        return conversation;
    }

    private ConversationThread getActiveConversationLocked(PlayerConversationState state) {
        ConversationThread active = this.findConversationLocked(state, state.activeConversationId);
        if (active == null) {
            active = this.createConversationLocked(state);
        }
        return active;
    }

    private ConversationThread findConversationLocked(PlayerConversationState state, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        for (ConversationThread conversation : state.conversations) {
            if (conversationId.equals(conversation.id)) {
                return conversation;
            }
        }
        return null;
    }

    private void touchConversationLocked(PlayerConversationState state, ConversationThread conversation, boolean moveToFront) {
        conversation.updatedAt = System.currentTimeMillis();
        if (moveToFront) {
            state.conversations.remove(conversation);
            state.conversations.add(0, conversation);
        }
        state.activeConversationId = conversation.id;
    }

    private ConversationSummary toSummary(ConversationThread conversation, boolean active) {
        return new ConversationSummary(
                conversation.id,
                conversation.title,
                conversation.messages == null ? 0 : conversation.messages.size(),
                conversation.updatedAt,
                active
        );
    }

    private int countSearchHits(ConversationThread conversation, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isEmpty() || conversation == null) {
            return 0;
        }

        int hits = 0;
        if (this.containsNormalized(conversation.title, normalizedQuery)) {
            hits++;
        }
        if (conversation.messages != null) {
            for (ConversationMessage message : conversation.messages) {
                if (this.containsNormalized(message.content, normalizedQuery)) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private boolean containsNormalized(String source, String normalizedQuery) {
        return source != null && normalizedQuery != null && !normalizedQuery.isEmpty()
                && source.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private File exportConversationLocked(ConversationThread conversation) {
        if (conversation == null) {
            return null;
        }

        File exportDir = this.resolveExportDirectory();
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            return null;
        }

        String fileName = this.buildExportFileName(conversation);
        File output = new File(exportDir, fileName);

        try (BufferedWriter writer = Files.newBufferedWriter(output.toPath(), StandardCharsets.UTF_8)) {
            writer.write("Herobrine Companion Conversation Export");
            writer.newLine();
            writer.write("Session: " + (this.loadedSessionKey == null ? "unknown" : this.loadedSessionKey));
            writer.newLine();
            writer.write("Title: " + (conversation.title == null ? this.fallbackConversationPrefix() : conversation.title));
            writer.newLine();
            writer.write("Created: " + this.formatTimestamp(conversation.createdAt));
            writer.newLine();
            writer.write("Updated: " + this.formatTimestamp(conversation.updatedAt));
            writer.newLine();
            writer.newLine();
            writer.write("Messages");
            writer.newLine();
            writer.write("========");
            writer.newLine();

            if (conversation.messages == null || conversation.messages.isEmpty()) {
                writer.write("(No messages)");
                writer.newLine();
            } else {
                for (ConversationMessage message : conversation.messages) {
                    writer.write("[" + this.formatTimestamp(message.createdAt) + "] " + this.formatRoleLabel(message.role) + ":");
                    writer.newLine();
                    writer.write(message.content == null ? "" : message.content);
                    writer.newLine();
                    writer.newLine();
                }
            }
            return output;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private File resolveExportDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("herobrine_companion").resolve("conversation_exports").toFile();
    }

    private String buildExportFileName(ConversationThread conversation) {
        String timestamp = EXPORT_FILE_TIME.format(Instant.ofEpochMilli(Math.max(System.currentTimeMillis(), conversation.updatedAt)).atZone(ZoneId.systemDefault()));
        String safeTitle = this.sanitizeFileName(conversation.title == null || conversation.title.isBlank()
                ? this.fallbackConversationPrefix()
                : conversation.title);
        return timestamp + "_" + safeTitle + ".txt";
    }

    private String sanitizeFileName(String input) {
        String sanitized = (input == null ? "conversation" : input)
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .trim();
        if (sanitized.isBlank()) {
            return "conversation";
        }
        return sanitized.length() > 48 ? sanitized.substring(0, 48) : sanitized;
    }

    private String formatTimestamp(long timestamp) {
        long safeTimestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        return EXPORT_LINE_TIME.format(Instant.ofEpochMilli(safeTimestamp).atZone(ZoneId.systemDefault()));
    }

    private String formatRoleLabel(String role) {
        if (role == null) {
            return "User";
        }
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "assistant" -> "Herobrine";
            case "system" -> "System";
            default -> "Player";
        };
    }

    private String buildDefaultConversationTitle(PlayerConversationState state) {
        String prefix = this.fallbackConversationPrefix();
        int nextIndex = 1;
        if (state != null && state.conversations != null) {
            for (ConversationThread conversation : state.conversations) {
                String title = conversation.title == null ? "" : conversation.title.trim();
                if (title.startsWith(prefix + " ")) {
                    String suffix = title.substring((prefix + " ").length()).trim();
                    try {
                        nextIndex = Math.max(nextIndex, Integer.parseInt(suffix) + 1);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return prefix + " " + nextIndex;
    }

    private String fallbackConversationPrefix() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            return Component.translatable("gui.herobrine_companion.conversation_manager.default_name").getString();
        }
        return "Conversation";
    }

    private String makeConversationTitle(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return this.fallbackConversationPrefix();
        }
        int maxLength = 24;
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 1).trim() + "…";
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
        File dir = FMLPaths.CONFIGDIR.get().resolve("herobrine_companion").resolve("conversations").toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, sessionKey + ".json");
    }

    private void saveLocked() {
        if (this.loadedFile == null) {
            return;
        }

        try (FileWriter writer = new FileWriter(this.loadedFile)) {
            GSON.toJson(this.storeData, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class StoreData {
        Map<String, PlayerConversationState> players = new HashMap<>();
    }

    private static class PlayerConversationState {
        String activeConversationId;
        List<ConversationThread> conversations = new ArrayList<>();
    }

    private static class ConversationThread {
        String id;
        String title;
        boolean autoTitle = true;
        long createdAt;
        long updatedAt;
        List<ConversationMessage> messages = new ArrayList<>();
    }

    private static class ConversationMessage {
        String role;
        String content;
        long createdAt;
    }

    public static final class ConversationSummary {
        private final String id;
        private final String title;
        private final int messageCount;
        private final long updatedAt;
        private final boolean active;

        public ConversationSummary(String id, String title, int messageCount, long updatedAt, boolean active) {
            this.id = id;
            this.title = title;
            this.messageCount = messageCount;
            this.updatedAt = updatedAt;
            this.active = active;
        }

        public String id() {
            return this.id;
        }

        public String title() {
            return this.title;
        }

        public int messageCount() {
            return this.messageCount;
        }

        public long updatedAt() {
            return this.updatedAt;
        }

        public boolean active() {
            return this.active;
        }
    }

    public static final class ConversationMessageSnapshot {
        private final String role;
        private final String content;

        public ConversationMessageSnapshot(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String role() {
            return this.role;
        }

        public String content() {
            return this.content;
        }
    }

    public static final class ConversationSearchResult {
        private final ConversationSummary summary;
        private final int matchCount;

        public ConversationSearchResult(ConversationSummary summary, int matchCount) {
            this.summary = summary;
            this.matchCount = matchCount;
        }

        public ConversationSummary summary() {
            return this.summary;
        }

        public int matchCount() {
            return this.matchCount;
        }
    }
}



