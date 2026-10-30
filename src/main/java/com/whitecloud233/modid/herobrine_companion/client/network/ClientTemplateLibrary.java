package com.whitecloud233.modid.herobrine_companion.client.network;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.structure.TemplateFileReader;
import com.whitecloud233.modid.herobrine_companion.network.*;
import com.whitecloud233.modid.herobrine_companion.structure.template.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Connection-scoped server catalog and paced upload state. UI and LLM consume the same snapshot. */
@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public final class ClientTemplateLibrary {
    /**
     * 通道探测超时。握手表（S2CModList 的 channels）缺失不代表通道真的不可用：
     * 混合端（Arclight/Mohist）、群组服转发都可能把表裁掉，而 {@code sendTo()} 本身不检查远端是否存在。
     * 因此登录后总是乐观地发一次索引请求，按“有没有回执”判定，超时才认输。
     */
    private static final long PROBE_TIMEOUT_NS = 15_000_000_000L;
    /** 上传/删除这类真实操作要跨多个分片，给足时间。 */
    private static final long OPERATION_TIMEOUT_NS = 90_000_000_000L;
    private static volatile List<TemplateEntry> entries = List.of();
    private static Connection connection;
    private static int sequence, pending;
    private static long started, revision;
    private static boolean editable;
    /** 收到过任意一次回执 ⇒ 通道确实可用，即使握手表里没有它。 */
    private static boolean confirmed;
    /** 探测发出去了但一个回执都没有：服务端没有这条通道，或中间层把包吞了。 */
    private static boolean unreachable;
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

    /**
     * 状态文案 key 后缀。只有“握手表里能看到通道”或“真的收到过回执”才算支持；
     * 探测进行中显示 probing，探测超时显示 unreachable，其余才是 unsupported。
     */
    public static String status() {
        if (supported()) return status;
        if (unreachable) return "unreachable";
        return pending != 0 ? "probing" : "unsupported";
    }

    public static String detail() { return detail; }
    public static int progress() { return upload == null ? 0 : offset * 100 / upload.bytes().length; }

    public static boolean supported() { return remotePresent() || confirmed; }

    /** 仅指“握手表里点了名”，不含探测结果；{@link #supported()} 才是最终判定。 */
    private static boolean remotePresent() {
        var listener = Minecraft.getInstance().getConnection();
        return listener != null && TemplateSyncChannel.supported(listener.getConnection());
    }

    public static void refresh() {
        // 故意不看 supported()：表里没有也必须发一次，否则混合端/代理环境下永远没有自愈机会。
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null || busy()) return;
        begin("loading");
        TemplateSyncChannel.send(new TemplateLibraryRequest(pending, ""));
    }

    public static void delete(String id) {
        if (!editable() || busy() || !has(id)) return;
        begin("loading");
        TemplateSyncChannel.send(new TemplateLibraryRequest(pending, id));
    }

    public static void importFile(Path path, String name) {
        if (!editable() || busy()) return;
        begin("uploading");
        int request = pending;
        Connection source = connection;
        CompletableFuture.supplyAsync(() -> {
            try { return TemplateFileReader.read(path); }
            catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
        }).whenComplete((file, error) -> Minecraft.getInstance().execute(() -> {
            if (pending != request || connection != source) return;
            if (error != null) { fail(error.getCause() == null ? error.getMessage() : error.getCause().getMessage()); return; }
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
        unreachable = false;
        status = nextStatus; detail = ""; revision++;
    }

    public static void accept(Connection source, TemplateLibraryReply reply) {
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null || listener.getConnection() != source || source != connection || reply.requestId() != pending) return;
        // 有回执就是铁证：握手表可能被混合端/代理裁掉，但服务端确实处理了这条通道。
        confirmed = true;
        unreachable = false;
        entries = List.copyOf(reply.entries());
        editable = reply.editable();
        status = reply.status(); detail = reply.detail();
        if (reply.ok() && !editable && status.equals("ready")) status = "permission";
        pending = 0; upload = null; revision++;
    }

    private static void fail(String message) { pending = 0; upload = null; status = "error"; detail = message == null ? "" : message; revision++; }

    /** 只发了探测、迟迟没有回执：不报“操作失败”，而是标记为服务端没回应，界面据此提示版本问题。 */
    private static void probeLost() { pending = 0; upload = null; unreachable = true; revision++; }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientTemplatePlacement.tick();
        if (pending == 0) return;
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null || listener.getConnection() != connection) { clear(); return; }
        // 探测阶段（握手表没点名、又还没收到回执）用短超时，别让玩家干等 90 秒。
        boolean probing = !supported();
        long limit = probing ? PROBE_TIMEOUT_NS : OPERATION_TIMEOUT_NS;
        if (System.nanoTime() - started > limit) {
            if (probing) probeLost();
            else fail("Server response timed out");
            return;
        }
        if (upload != null && offset < upload.bytes().length) {
            int end = Math.min(offset + TemplateLimits.CHUNK_BYTES, upload.bytes().length);
            TemplateSyncChannel.send(new TemplateUploadPacket(pending, upload.filename(), uploadName, upload.bytes().length, chunk++, Arrays.copyOfRange(upload.bytes(), offset, end)));
            offset = end;
        }
    }

    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) { clear(); refresh(); }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    private static void clear() { ClientTemplatePlacement.clear(); entries = List.of(); connection = null; pending = 0; editable = false; upload = null; status = "ready"; detail = ""; confirmed = false; unreachable = false; revision++; }
}
