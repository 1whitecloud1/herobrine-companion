package com.whitecloud233.herobrine_companion.client.service;

import com.whitecloud233.herobrine_companion.client.memory.ImportedMemory;
import com.whitecloud233.herobrine_companion.client.memory.MemoryImportService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class ConversationImportChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("hc-conversation-import-");
        Environment environment = new Environment(directory);
        UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID other = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ImportedMemory memory = new ImportedMemory("Imported", "prefers rivers", List.of(
                new ImportedMemory.Message("user", "old question"), new ImportedMemory.Message("assistant", "old reply")));
        try {
            ConversationStore store = new ConversationStore(environment);
            store.loadForCurrentSession();
            String originalId = store.ensureActiveConversation(owner).id();
            store.appendMessage(owner, "user", "current world history", false);
            store.setActiveConversationSummary(owner, "existing summary");
            var target = store.captureImportTarget(owner);
            var imported = store.importMemory(target, memory);
            check(!imported.id().equals(originalId), "Import overwrote current conversation identity");
            check(store.listConversations(owner).size() == 2, "Import did not preserve original conversation");
            check(store.getActiveImportedMemory(owner).equals(memory), "Imported memory was not stored");
            check(store.getActiveConversationMessages(owner).isEmpty(), "Imported reference became live message/tool context");
            check(store.getActiveImportedMemory(other) == null, "Import crossed player boundary");
            check(store.searchConversations(owner, "rivers").size() == 1, "Imported notes are not searchable");
            store.setActiveConversation(owner, originalId);
            check(store.getActiveConversationMessages(owner).get(0).content().equals("current world history"), "Existing conversation messages changed");
            check(store.getActiveConversationSummary(owner).equals("existing summary"), "Existing summary changed");
            store.setActiveConversation(owner, imported.id());
            store.appendMessage(owner, "user", "new question", false);
            var exported = store.exportActiveConversation(owner);
            check(exported != null && exported.getName().endsWith(".json"), "Imported conversation must export its memory");
            ImportedMemory again = MemoryImportService.read(exported.toPath());
            check(again.notes().equals(memory.notes()) && again.messages().size() == 3, "Re-export lost imported reference or new chat");
            store.saveAndClearSession();
            store.loadForCurrentSession();
            check(store.getActiveImportedMemory(owner).equals(memory), "Imported memory did not survive reload");
            reject(() -> store.importMemory(target, memory));
            check(store.listConversations(owner).size() == 2, "Stale import changed conversation count");
            store.clearActiveConversation(owner);
            check(store.getActiveImportedMemory(owner) == null && store.getActiveConversationSummary(owner).isEmpty(), "Clear conversation left hidden memory");
            store.setActiveConversationSummary(owner, "stale summary");
            store.clearActiveConversation(owner);
            check(store.getActiveConversationSummary(owner).isEmpty(), "Clear history left a stale summary");

            var validTarget = store.captureImportTarget(owner);
            String activeBefore = store.ensureActiveConversation(owner).id();
            int beforeCount = store.listConversations(owner).size();
            Path sessionFile = environment.sessionFile(environment.session);
            Files.delete(sessionFile);
            Files.createDirectories(sessionFile);
            Files.writeString(sessionFile.resolve("keep"), "keep");
            reject(() -> store.importMemory(validTarget, memory));
            check(store.listConversations(owner).size() == beforeCount, "Failed save retained partial import");
            check(store.getActiveConversationTitle(owner).equals(store.listConversations(owner).stream().filter(c -> c.id().equals(activeBefore)).findFirst().orElseThrow().title()),
                    "Failed save did not restore active conversation");
            check(Files.readString(sessionFile.resolve("keep")).equals("keep"), "Failed import damaged original file target");
            Files.delete(sessionFile.resolve("keep"));
            Files.delete(sessionFile);
            store.saveAndClearSession();
            environment.session = "world_b";
            store.loadForCurrentSession();
            reject(() -> store.importMemory(validTarget, memory));
            check(store.getActiveImportedMemory(owner) == null, "Import crossed world boundary");
            store.saveAndClearSession();
            environment.session = "corrupt";
            Path corrupt = environment.sessionFile(environment.session);
            Files.writeString(corrupt, "{broken-original-file");
            expectCorruptLoadDiagnostic(store);
            check(Files.readString(corrupt).equals("{broken-original-file"), "Loading corrupt state overwrote original file");
            var corruptTarget = store.captureImportTarget(owner);
            reject(() -> store.importMemory(corruptTarget, memory));
            store.saveAndClearSession();
            check(Files.readString(corrupt).equals("{broken-original-file"), "Failed import/logout overwrote corrupt source");
            System.out.println("ConversationImportChecks: " + checks + " checks passed");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    private interface IoAction { void run() throws IOException; }

    private static void expectCorruptLoadDiagnostic(ConversationStore store) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream originalError = System.err;
        // This regression runs in its own JVM. Capture only the intentionally corrupt load.
        try (PrintStream error = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            try {
                System.setErr(error);
                store.loadForCurrentSession();
            } finally {
                System.setErr(originalError);
            }
        }
        String diagnostic = captured.toString(StandardCharsets.UTF_8);
        List<String> lines = diagnostic.lines().toList();
        boolean expected = lines.size() > 1
                && lines.get(0).equals("java.io.IOException: Conversation file cannot be parsed; original file kept unchanged")
                && lines.subList(1, lines.size()).stream().allMatch(line -> line.startsWith("\tat "));
        // Unexpected exceptions or extra diagnostics must remain visible and fail the check.
        if (!expected) originalError.print(diagnostic);
        check(expected, "Corrupt conversation load did not report the expected diagnostic");
    }

    private static void reject(IoAction action) throws IOException {
        try { action.run(); } catch (IOException expected) { checks++; return; }
        throw new AssertionError("Stale or unsavable import was accepted");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }

    private static final class Environment implements ConversationEnvironment {
        private final Path directory;
        private String session = "world_a";
        private Environment(Path directory) { this.directory = directory; }
        @Override public String sessionKey() { return session; }
        @Override public Path sessionFile(String key) { return directory.resolve(key + ".json"); }
        @Override public Path exportDirectory() { return directory.resolve("exports"); }
        @Override public String defaultTitle() { return "Conversation"; }
    }
}
