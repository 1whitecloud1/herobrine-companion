package com.whitecloud233.herobrine_companion.client.network;

import com.whitecloud233.herobrine_companion.network.TemplatePlacementRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/** Adapts generated place commands to acknowledged uploads, preserving the normal command permission gate. */
public final class ClientTemplatePlacement {
    private static final Pattern COMMAND =
            Pattern.compile("place template (\\S+) (~(?:-?\\d+)?) (~(?:-?\\d+)?) (~(?:-?\\d+)?)");

    private record Pending(Connection source, CompletableFuture<Boolean> future, AtomicLong activity) { }

    private static final ConcurrentMap<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private ClientTemplatePlacement() { }

    /** null means this is not an uploaded-template command; the existing executor continues normally. */
    public static CompletableFuture<Boolean> execute(String command) {
        var match = COMMAND.matcher(command);
        if (!match.matches() || !ClientTemplateLibrary.has(match.group(1))) return null;
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null || !TemplateChannel.supported(listener.getConnection()) || PENDING.size() >= 4) {
            return CompletableFuture.completedFuture(false);
        }
        int x;
        int y;
        int z;
        try {
            x = offset(match.group(2));
            y = offset(match.group(3));
            z = offset(match.group(4));
        } catch (NumberFormatException error) {
            return CompletableFuture.completedFuture(false);
        }
        UUID request = UUID.randomUUID();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        PENDING.put(request, new Pending(listener.getConnection(), future, new AtomicLong(System.nanoTime())));
        PacketDistributor.sendToServer(new TemplatePlacementRequest(request, match.group(1), x, y, z));
        return future.whenComplete((ok, error) -> PENDING.remove(request));
    }

    private static int offset(String token) {
        return token.length() == 1 ? 0 : Integer.parseInt(token.substring(1));
    }

    public static void accept(Connection source, UUID request, boolean ok, boolean complete) {
        var listener = Minecraft.getInstance().getConnection();
        Pending pending = PENDING.get(request);
        if (pending != null && pending.source() == source && listener != null && listener.getConnection() == source) {
            pending.activity().set(System.nanoTime());
            if (complete) pending.future().complete(ok);
        }
    }

    public static void tick() {
        var minecraft = Minecraft.getInstance();
        var listener = minecraft.getConnection();
        long now = System.nanoTime();
        for (Pending pending : PENDING.values()) {
            if (minecraft.isPaused()) pending.activity().set(now);
            if (listener == null || listener.getConnection() != pending.source()
                    || now - pending.activity().get() > 60_000_000_000L) {
                pending.future().complete(false);
            }
        }
    }

    public static void clear() {
        for (Pending pending : PENDING.values()) pending.future().complete(false);
        PENDING.clear();
    }
}
