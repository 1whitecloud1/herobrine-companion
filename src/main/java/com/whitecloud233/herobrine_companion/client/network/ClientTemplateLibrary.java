package com.whitecloud233.herobrine_companion.client.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.structure.TemplateFileReader;
import com.whitecloud233.herobrine_companion.network.TemplateLibraryReply;
import com.whitecloud233.herobrine_companion.network.TemplateLibraryRequest;
import com.whitecloud233.herobrine_companion.network.TemplateUploadPacket;
import com.whitecloud233.herobrine_companion.structure.template.TemplateEntry;
import com.whitecloud233.herobrine_companion.structure.template.TemplateLimits;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Connection-scoped server catalog and paced upload state. UI and LLM consume the same snapshot. */
@EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public final class ClientTemplateLibrary {
    private static volatile List<TemplateEntry> entries = List.of();
    private static Connection connection;
    private static int sequence, pending;
    private static long started, revision;
    private static boolean editable;
    /**
     * 检测窗口。NeoForge 与 Forge 的容错语义相反，这里不能照搬 1.20.1 的「乐观发包 + 等回执」：
     * Forge 收到未注册通道是静默丢弃，NeoForge 则是
     * {@code Received a modded payload ... with an unknown or unaccepted channel; disconnecting.}
     * 直接断连。所以只按协商结果判定，但给协商落地留一个窗口：窗口内显示「正在检测」，
     * 窗口结束仍不可用才判为服务端版本较旧。
     */
    private static final long PROBE_WINDOW_NS = 6_000_000_000L;
    private static long probeUntil;
    private static String status = "ready", detail = "";
    private static TemplateFileReader.FileData upload;
    private static String uploadName;
    private static int offset, chunk;

    private ClientTemplateLibrary() { }

    public static List<TemplateEntry> entries() { return entries; }
    public static boolean has(String id) { return entries.stream().anyMatch(entry -> entry.id().equals(id)); }
    public static long revision() { return revision; }
    public static boolean busy() { return pending != 0; }
    public static boolean editable() { return supported() && editable; }
    public static String status() {
        if (supported()) return status;
        return probing() ? "probing" : "unsupported_legacy";
    }

    /** 协商结果尚未落地（登录后几秒内）时不要立刻判死，否则界面会闪一下「不支持」。 */
    private static boolean probing() { return probeUntil != 0 && System.nanoTime() - probeUntil < 0; }
    public static String detail() { return detail; }
    public static int progress() { return upload == null ? 0 : offset * 100 / upload.bytes().length; }

    public static boolean supported() {
        var listener = Minecraft.getInstance().getConnection();
        return listener != null && TemplateChannel.supported(listener.getConnection());
    }

    public static void refresh() {
        if (busy()) return;
        if (!supported()) {
            // 重新开一次检测窗口（不发包）。协商结果迟到时 tick() 会自动补一次同步。
            probeUntil = System.nanoTime() + PROBE_WINDOW_NS;
            return;
        }
        probeUntil = 0;
        begin("loading");
        PacketDistributor.sendToServer(new TemplateLibraryRequest(pending, ""));
    }

    public static void delete(String id) {
        if (!editable() || busy() || !has(id)) return;
        begin("loading");
        PacketDistributor.sendToServer(new TemplateLibraryRequest(pending, id));
    }

    public static void importFile(Path path, String name) {
        if (!editable() || busy()) return;
        begin("uploading");
        int request = pending;
        Connection source = connection;
        CompletableFuture.supplyAsync(() -> {
            try {
                return TemplateFileReader.read(path);
            } catch (Exception error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        }).whenComplete((file, error) -> Minecraft.getInstance().execute(() -> {
            if (pending != request || connection != source) return;
            if (error != null) {
                fail(error.getCause() == null ? error.getMessage() : error.getCause().getMessage());
                return;
            }
            upload = file;
            uploadName = name.substring(0, Math.min(name.length(), TemplateLimits.MAX_NAME));
            offset = chunk = 0;
        }));
    }

    private static void begin(String nextStatus) {
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null) return;
        connection = listener.getConnection();
        sequence = sequence == Integer.MAX_VALUE ? 1 : sequence + 1;
        pending = sequence;
        started = System.nanoTime();
        status = nextStatus;
        detail = "";
        revision++;
    }

    public static void accept(Connection source, TemplateLibraryReply reply) {
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null || listener.getConnection() != source || source != connection
                || reply.requestId() != pending) return;
        entries = List.copyOf(reply.entries());
        editable = reply.editable();
        status = reply.status();
        detail = reply.detail();
        if (reply.ok() && !editable && status.equals("ready")) status = "permission";
        pending = 0;
        upload = null;
        revision++;
    }

    private static void fail(String message) {
        pending = 0;
        upload = null;
        status = "error";
        detail = message == null ? "" : message;
        revision++;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientTemplatePlacement.tick();
        if (probeUntil != 0) {
            if (supported()) {
                // 协商结果迟到：窗口内拿到了通道，立刻补一次索引同步。
                probeUntil = 0;
                refresh();
            } else if (System.nanoTime() - probeUntil >= 0) {
                probeUntil = 0;
            }
        }
        if (pending == 0) return;
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null || listener.getConnection() != connection) {
            clear();
            return;
        }
        if (System.nanoTime() - started > 90_000_000_000L) {
            fail("Server response timed out");
            return;
        }
        if (upload != null && offset < upload.bytes().length) {
            int end = Math.min(offset + TemplateLimits.CHUNK_BYTES, upload.bytes().length);
            PacketDistributor.sendToServer(new TemplateUploadPacket(pending, upload.filename(), uploadName,
                    upload.bytes().length, chunk++, Arrays.copyOfRange(upload.bytes(), offset, end)));
            offset = end;
        }
    }

    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        clear();
        refresh();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        ClientTemplatePlacement.clear();
        entries = List.of();
        connection = null;
        pending = 0;
        editable = false;
        probeUntil = 0;
        upload = null;
        status = "ready";
        detail = "";
        revision++;
    }
}
