package com.whitecloud233.modid.herobrine_companion.client.network;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.config.ServerConfigAccess;
import com.whitecloud233.modid.herobrine_companion.config.ServerConfigSnapshot;
import com.whitecloud233.modid.herobrine_companion.config.ServerConfigUpdate;
import com.whitecloud233.modid.herobrine_companion.config.ServerRule;
import com.whitecloud233.modid.herobrine_companion.network.ConfigSyncChannel;
import com.whitecloud233.modid.herobrine_companion.network.ServerConfigReply;
import com.whitecloud233.modid.herobrine_companion.network.ServerConfigRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 远端规则只作显示快照；收到服务端确认前不修改本机规范或运行状态。 */
@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public final class ClientServerConfig {
    private static final ServerConfigClientState STATE = new ServerConfigClientState();
    private static Connection connection;
    private static int nextRequestId;
    /** 收到过任意一次回执 ⇒ 通道确实可用，即使握手表（S2CModList 的 channels）里没有它。 */
    private static boolean confirmed;

    private ClientServerConfig() { }

    public static ServerConfigSnapshot snapshot() {
        return connected() && STATE.snapshot() != null ? STATE.snapshot() : ServerConfigAccess.readLocal();
    }

    public static boolean hasSnapshot() { return !connected() || STATE.snapshot() != null; }
    public static boolean connected() { return Minecraft.getInstance().getConnection() != null; }

    public static boolean editable() {
        STATE.expire(System.nanoTime());
        return !connected() || (supported() && STATE.editable());
    }

    public static String statusKey() {
        STATE.expire(System.nanoTime());
        if (!connected()) return "";
        if (!supported()) {
            // 探测中（还没回执）显示“等待服务器”，超时才说“服务器没回应”，其余才是“不支持”。
            return switch (STATE.status()) {
                case PENDING -> "gui.herobrine_companion.config.server_pending";
                case TIMED_OUT -> "gui.herobrine_companion.config.server_timeout";
                default -> "gui.herobrine_companion.config.server_unsupported";
            };
        }
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
        // 故意不看 supported()：握手表缺失不代表通道不可用（混合端/代理会裁表），
        // 只有回执能证明通道可用；没有回执时 STATE 会在 10 秒后落到 TIMED_OUT。
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
        // 有回执就是铁证：握手表可能被混合端/代理裁掉，但服务端确实处理了这条通道。
        confirmed = true;
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

    private static boolean supported() { return remotePresent() || confirmed; }

    /** 仅指“握手表里点了名”，不含探测结果；{@link #supported()} 才是最终判定。 */
    private static boolean remotePresent() {
        var listener = Minecraft.getInstance().getConnection();
        return listener != null && ConfigSyncChannel.isSupported(listener.getConnection());
    }

    private static void ensureConnection() {
        Connection current = Minecraft.getInstance().getConnection().getConnection();
        if (connection == current) return;
        connection = current;
        confirmed = false;
        STATE.clear();
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) { requestCurrent(); }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        connection = null;
        confirmed = false;
        STATE.clear();
        ServerConfigAccess.restoreLocalView();
    }
}
