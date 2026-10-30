package com.whitecloud233.herobrine_companion.structure.template;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.config.ServerConfigAccess;
import com.whitecloud233.herobrine_companion.network.*;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Coordinates authenticated uploads and commits; parsing is delegated off the server tick thread. */
@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class ServerTemplateLibrary {
    private record Transfer(TemplateUploadBuffer buffer, long lastActivity) { }
    private static final Map<MinecraftServer, ServerTemplateLibrary> LIBRARIES = new IdentityHashMap<>();
    private final Map<UUID, Transfer> transfers = new HashMap<>();
    private final Set<UUID> processing = new HashSet<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "HerobrineTemplateIO"); thread.setDaemon(true); thread.setPriority(Thread.MIN_PRIORITY); return thread;
    });
    private ServerTemplateLibrary() { }

    private static ServerTemplateLibrary of(ServerPlayer player) { return LIBRARIES.computeIfAbsent(player.server, key -> new ServerTemplateLibrary()); }
    static Executor worker(ServerPlayer player) { return of(player).worker; }
    private static TemplateRepository repository(ServerPlayer player) { return new TemplateRepository(player.server.getWorldPath(LevelResource.ROOT), player.getUUID()); }

    public static void request(ServerPlayer player, TemplateLibraryRequest request) {
        if (player == null) return;
        var library = of(player);
        try {
            if (!request.deleteId().isEmpty()) {
                if (!ServerConfigAccess.canEdit(player)) { reply(player, request.requestId(), false, "permission", ""); return; }
                if (library.processing.contains(player.getUUID()) || library.transfers.containsKey(player.getUUID())) throw new IOException("An import is already in progress");
                repository(player).delete(request.deleteId());
                player.server.getStructureManager().remove(ResourceLocation.parse(request.deleteId()));
            }
            reply(player, request.requestId(), true, request.deleteId().isEmpty() ? "ready" : "deleted", "");
        } catch (Exception error) { reply(player, request.requestId(), false, "error", error.getMessage()); }
    }

    public static void upload(ServerPlayer player, TemplateUploadPacket packet) {
        if (player == null) return;
        var library = of(player);
        UUID owner = player.getUUID();
        if (!ServerConfigAccess.canEdit(player)) { reply(player, packet.requestId(), false, "permission", ""); return; }
        try {
            if (library.processing.contains(owner)) return;
            Transfer transfer = library.transfers.get(owner);
            if (packet.chunk() == 0) {
                if (transfer != null || library.transfers.size() + library.processing.size() >= 8) throw new IOException("Template upload is busy");
                transfer = new Transfer(new TemplateUploadBuffer(packet.requestId(), packet.total(), packet.filename(), packet.name()), System.nanoTime());
            }
            if (transfer == null) return;
            library.transfers.put(owner, new Transfer(transfer.buffer(), System.nanoTime()));
            if (!transfer.buffer().append(packet.requestId(), packet.total(), packet.filename(), packet.name(), packet.chunk(), packet.data())) return;
            byte[] bytes = transfer.buffer().bytes();
            library.transfers.remove(owner);
            library.processing.add(owner);
            MinecraftServer server = player.server;
            var connection = player.connection;
            CompletableFuture.supplyAsync(() -> {
                try {
                    ImportedTemplate imported = TemplateImportService.decode(packet.filename(), bytes, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
                    int version = imported.nbt().getInt("DataVersion");
                    if (version > SharedConstants.getCurrentVersion().getDataVersion().getVersion()) throw new IOException("Template was made for a newer Minecraft version");
                    CompoundTag fixed = version == SharedConstants.getCurrentVersion().getDataVersion().getVersion() ? imported.nbt()
                            : DataFixTypes.STRUCTURE.updateToCurrentVersion(server.getFixerUpper(), imported.nbt(), version);
                    fixed.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
                    TemplateStateValidator.validate(fixed);
                    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                    String name = displayName(packet.name().isBlank() ? packet.filename() : packet.name());
                    TemplateEntry entry = new TemplateEntry(repository(player).id(digest), name, imported.format(), imported.x(), imported.y(), imported.z(), imported.blocks());
                    return repository(player).stage(entry, fixed);
                } catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
            }, library.worker).whenComplete((prepared, error) -> {
                if (server.isStopped()) { discard(prepared); return; }
                server.execute(() -> {
                    library.processing.remove(owner);
                    if (player.connection != connection || !connection.getConnection().isConnected()) { discard(prepared); return; }
                    if (!ServerConfigAccess.canEdit(player)) { discard(prepared); reply(player, packet.requestId(), false, "permission", ""); return; }
                    try (var staged = prepared) {
                        if (error != null) throw new IOException(error.getCause() == null ? error.getMessage() : error.getCause().getMessage());
                        repository(player).commit(staged);
                        server.getStructureManager().remove(ResourceLocation.parse(prepared.entry().id()));
                        reply(player, packet.requestId(), true, "imported", prepared.entry().name());
                    } catch (Exception failure) { reply(player, packet.requestId(), false, "error", failure.getMessage()); }
                });
            });
        } catch (Exception error) {
            library.transfers.remove(owner);
            reply(player, packet.requestId(), false, "error", error.getMessage());
        }
    }

    private static void discard(TemplateRepository.Staged staged) {
        if (staged != null) try { staged.close(); } catch (IOException ignored) { }
    }

    private static String displayName(String value) {
        String clean = value.replaceAll("[\\p{Cntrl}§]", " ").strip();
        if (clean.isEmpty()) clean = "Building";
        return clean.substring(0, Math.min(clean.length(), TemplateLimits.MAX_NAME));
    }

    private static void reply(ServerPlayer player, int request, boolean ok, String status, String detail) {
        List<TemplateEntry> entries;
        try { entries = repository(player).list(); }
        catch (IOException error) { entries = List.of(); ok = false; status = "error"; detail = error.getMessage(); }
        String message = detail == null ? "" : detail;
        PacketDistributor.sendToPlayer(player, new TemplateLibraryReply(request, ok, ServerConfigAccess.canEdit(player), status,
                message.substring(0, Math.min(message.length(), 512)), entries));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 200 != 0) return;
        var library = LIBRARIES.get(event.getServer());
        if (library != null) library.transfers.values().removeIf(transfer -> System.nanoTime() - transfer.lastActivity() > 60_000_000_000L);
    }
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var library = LIBRARIES.get(player.server);
        if (library != null) library.transfers.remove(player.getUUID());
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ServerTemplateLibrary library = LIBRARIES.remove(event.getServer());
        if (library != null) library.worker.shutdownNow();
    }
}
