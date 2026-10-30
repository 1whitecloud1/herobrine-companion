package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.config.ServerConfigAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;

/**
 * 配置同步通道（可选）。
 *
 * <p>独立于主通道，避免改动已发布编号；旧服务端不认这条通道就不启用。
 * 通道脚手架见 {@link OptionalChannel}。版本号 {@code 2}：Field 12（Poem 动作）
 * 扩展了快照内容，旧构建不得协商本通道。</p>
 */
public final class ConfigSyncChannel extends OptionalChannel {

    private static final String VERSION = "2";
    private static final ConfigSyncChannel INSTANCE = new ConfigSyncChannel();

    private ConfigSyncChannel() {
        super("config_sync", VERSION);
    }

    public static void register() {
        INSTANCE.registerMessage(0, ServerConfigRequest.class, ServerConfigRequest::encode, ServerConfigRequest::new,
                ServerConfigRequest::handle, NetworkDirection.PLAY_TO_SERVER);
        INSTANCE.registerMessage(1, ServerConfigReply.class, ServerConfigReply::encode, ServerConfigReply::new,
                ServerConfigReply::handle, NetworkDirection.PLAY_TO_CLIENT);
    }

    /** 该连接是否声明支持本通道（脚手架见 {@link OptionalChannel#remoteSupports}）。 */
    public static boolean isSupported(net.minecraft.network.Connection connection) {
        return INSTANCE.remoteSupports(connection);
    }

    public static void sendRequest(ServerConfigRequest request) {
        INSTANCE.sendToServer(request);
    }

    public static void reply(ServerPlayer player, int requestId, boolean accepted) {
        INSTANCE.sendToPlayer(player, new ServerConfigReply(
                requestId, ServerConfigAccess.snapshot(), ServerConfigAccess.canEdit(player), accepted));
    }

    public static void broadcast(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            reply(player, 0, true);
        }
    }
}
