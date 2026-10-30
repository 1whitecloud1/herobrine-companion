package com.whitecloud233.herobrine_companion.network;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

/**
 * Agent 扩展能力协商（交接 4.2 / T22），NeoForge 1.21.1 版本。
 *
 * <p>与 Forge 版的差异：NeoForge 只有一个 {@code PayloadRegistrar}，没有独立
 * {@code SimpleChannel} 的 {@code acceptMissingOr} 协商。等价做法是
 * <b>按载荷探测</b>：{@link NetworkRegistry#hasChannel} 判断对端是否注册了回执载荷，
 * 判不出来时宁可不发——NeoForge 收到未注册 payload 是致命错误（直接断连），
 * 不同于 Forge 的静默丢弃。</p>
 */
public final class AgentFeatureChannel {

    /** 协议版本；新增能力必须递增。 */
    public static final String VERSION = "1";

    public static final int FEATURE_TASK_CONTROL = 1;
    public static final int FEATURE_NAVIGATE = 1 << 1;
    public static final int FEATURE_BLOCK_QUERY = 1 << 2;
    public static final int FEATURE_COOK = 1 << 3;
    public static final int FEATURE_TRANSFER = 1 << 4;
    public static final int FEATURE_INTERACT = 1 << 5;
    public static final int FEATURE_REMEMBER = 1 << 6;
    public static final int FEATURE_BUILD_PREVIEW = 1 << 7;
    public static final int FEATURE_GATHER = 1 << 8;

    public static final int ALL_FEATURES =
            FEATURE_TASK_CONTROL | FEATURE_NAVIGATE | FEATURE_BLOCK_QUERY | FEATURE_COOK
                    | FEATURE_TRANSFER | FEATURE_INTERACT | FEATURE_REMEMBER
                    | FEATURE_BUILD_PREVIEW | FEATURE_GATHER;

    private AgentFeatureChannel() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(AgentFeatureRequest.TYPE, AgentFeatureRequest.STREAM_CODEC,
                AgentFeatureRequest::handle);
        registrar.playToClient(AgentFeatureReply.TYPE, AgentFeatureReply.STREAM_CODEC,
                AgentFeatureReply::handle);
    }

    /**
     * 对端是否注册了本通道的载荷。
     *
     * <p>用回执载荷探测（服务端 → 客户端方向的那个），因为客户端要判断的正是
     * "服务端会不会回我"。判不出来时返回 false，宁可不发。</p>
     */
    public static boolean supported(Connection connection) {
        return connection != null && NetworkRegistry.hasChannel(connection, null, AgentFeatureReply.TYPE.id());
    }

    public static void sendRequest(AgentFeatureRequest request) {
        PacketDistributor.sendToServer(request);
    }

    public static void reply(ServerPlayer player, UUID requestId, int features) {
        if (player == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new AgentFeatureReply(requestId, features));
    }
}
