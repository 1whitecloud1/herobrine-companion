package com.whitecloud233.herobrine_companion.client.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.config.ServerConfigAccess;
import com.whitecloud233.herobrine_companion.config.ServerConfigSnapshot;
import com.whitecloud233.herobrine_companion.config.ServerConfigUpdate;
import com.whitecloud233.herobrine_companion.config.ServerRule;
import com.whitecloud233.herobrine_companion.network.ConfigSyncChannel;
import com.whitecloud233.herobrine_companion.network.ServerConfigReply;
import com.whitecloud233.herobrine_companion.network.ServerConfigRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** 远端规则只作显示快照；收到服务端确认前不修改本机规范或运行状态。 */
@EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public final class ClientServerConfig {
    private static final ServerConfigClientState STATE = new ServerConfigClientState();
    private static Connection connection;
    private static int nextRequestId;

    private ClientServerConfig() { }

    public static ServerConfigSnapshot snapshot() {
        return connected() && STATE.snapshot() != null ? STATE.snapshot() : ServerConfigAccess.readLocal();
    }

    public static boolean hasSnapshot() { return !connected() || STATE.snapshot() != null; }
    public static boolean connected() { return Minecraft.getInstance().getConnection() != null; }

    public static boolean editable() {
        STATE.expire(System.nanoTime());
        return !connected() || (remoteSupported() && STATE.editable());
    }

    public static String statusKey() {
        STATE.expire(System.nanoTime());
        if (!connected()) return "";
        if (!remoteSupported()) return "gui.herobrine_companion.config.server_unsupported";
        return switch (STATE.status()) {
            case EMPTY, PENDING -> "gui.herobrine_companion.config.server_pending";
            case CONFIRMED -> "gui.herobrine_companion.config.server_confirmed";
            case READ_ONLY -> "gui.herobrine_companion.config.server_read_only";
            case REJECTED -> "gui.herobrine_companion.config.server_rejected";
            case TIMED_OUT -> "gui.herobrine_companion.config.server_timeout";
        };
    }

    public static long revision() { STATE.expire(System.nanoTime()); return STATE.revision(); }

    public static void requestCurrent() {
        if (!connected()) return;
        ensureConnection();
        // NeoForge 收到未注册 payload 会直接断连：对端没这条通道就别发，宁可不显示服务端规则。
        if (!remoteSupported()) return;
        send(ServerConfigRequest.query(newRequestId()));
    }

    public static void update(ServerRule rule, int value) {
        if (!connected()) {
            ServerConfigAccess.saveLocal(ServerConfigAccess.readLocal().with(rule, value));
            STATE.clear();
            return;
        }
        if (!editable()) return;
        send(new ServerConfigRequest(newRequestId(), ServerConfigUpdate.single(rule, value)));
    }

    public static void accept(Connection source, ServerConfigReply reply) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null || minecraft.getConnection().getConnection() != source) return;
        ensureConnection();
        if (!STATE.accept(reply.requestId(), reply.snapshot(), reply.canEdit(), reply.accepted())) return;
        // 单机两端共用运行快照，客户端旧回执不能回滚服务端刚生效的新值。
        if (minecraft.getSingleplayerServer() == null) ServerConfigAccess.acceptRemote(reply.snapshot());
    }

    private static int newRequestId() {
        nextRequestId = nextRequestId == Integer.MAX_VALUE ? 1 : nextRequestId + 1;
        return nextRequestId;
    }

    private static void send(ServerConfigRequest request) {
        STATE.request(request.requestId(), System.nanoTime());
        ConfigSyncChannel.sendRequest(request);
    }

    private static boolean remoteSupported() {
        Connection current = connection;
        if (current == null) {
            var listener = Minecraft.getInstance().getConnection();
            current = listener == null ? null : listener.getConnection();
        }
        return current != null && ConfigSyncChannel.supported(current);
    }

    private static void ensureConnection() {
        Connection current = Minecraft.getInstance().getConnection().getConnection();
        if (connection == current) return;
        connection = current;
        STATE.clear();
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) { requestCurrent(); }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        connection = null;
        STATE.clear();
        ServerConfigAccess.restoreLocalView();
    }
}
