package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.config.ServerConfigAccess;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 独立于主消息编号的配置同步通道。
 *
 * <p>Forge 版本用独立 {@code SimpleChannel} 保证老客户端不参与协商；NeoForge 1.21.1 只有一个
 * {@code PayloadRegistrar}，等价做法是把这两个载荷按类型注册在同一个通道里，并依靠
 * {@code requestId} 的应答握手判断对端是否真的处理了请求（超时见
 * {@code ServerConfigClientState#expire}）。协议号仍为 3。</p>
 */
public final class ConfigSyncChannel {

    private ConfigSyncChannel() { }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(ServerConfigRequest.TYPE, ServerConfigRequest.STREAM_CODEC, ServerConfigRequest::handle);
        registrar.playToClient(ServerConfigReply.TYPE, ServerConfigReply.STREAM_CODEC, ServerConfigReply::handle);
    }

    public static void sendRequest(ServerConfigRequest request) { PacketDistributor.sendToServer(request); }

    /**
     * 对端是否注册了配置同步载荷。NeoForge 把未注册 payload 当成致命错误（收到即断连），
     * 与 Forge 的「静默丢弃」相反，所以这里必须按协商结果过滤；判不出来时宁可不发。
     */
    public static boolean supported(Connection connection) {
        return connection != null
                && NetworkRegistry.hasChannel(connection, null, ServerConfigReply.TYPE.id());
    }

    public static void reply(ServerPlayer player, int requestId, boolean accepted) {
        if (player == null) return;
        PacketDistributor.sendToPlayer(player, new ServerConfigReply(
                requestId, ServerConfigAccess.snapshot(), ServerConfigAccess.canEdit(player), accepted));
    }

    public static void broadcast(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) reply(player, 0, true);
    }
}
