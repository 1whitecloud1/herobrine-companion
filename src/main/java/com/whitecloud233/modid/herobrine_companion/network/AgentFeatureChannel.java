package com.whitecloud233.modid.herobrine_companion.network;

import java.util.UUID;

/**
 * Agent 扩展能力协商通道（交接 4.2 / T22）。
 *
 * <p><b>解决什么</b>：本轮的 8 个新工具与任务控制（start/status/pause/resume/cancel）
 * 只在两端都认识时才可用。旧服务端收到未知工具只会回 {@code unknown_tool}，
 * 旧客户端也不该在工具清单里看到它们——否则玩家点了必然失败。</p>
 *
 * <p><b>为什么独立成通道</b>：主通道已发布固定编号，往里塞新包会破坏旧端兼容；
 * 用 {@link OptionalChannel} 的 {@code acceptMissingOr} 协商，旧端不认就整条不启用，
 * 主通道的工具/任务旧行为完全保留。</p>
 *
 * <p><b>单一职责</b>：只做"能力声明 + 一次握手"。不执行工具、不改工具注册表。</p>
 */
public final class AgentFeatureChannel extends OptionalChannel {

    /**
     * 协议版本。
     *
     * <p>1 = 首个支持本轮扩展工具与任务控制的版本。**新增能力必须递增**，
     * 否则旧构建会以为自己支持新工具。</p>
     */
    private static final String VERSION = "1";

    private static final AgentFeatureChannel INSTANCE = new AgentFeatureChannel();

    /**
     * 本端声明支持的能力位。
     *
     * <p>用位掩码而不是字符串清单：新增能力只需新位，旧端不认识高位就自然忽略，
     * 不需要解析、也不会因为拼写差异误判。</p>
     */
    public static final int FEATURE_TASK_CONTROL = 1;      // agent_task 的 start/status/pause/resume/cancel
    public static final int FEATURE_NAVIGATE = 1 << 1;     // hero_navigate
    public static final int FEATURE_BLOCK_QUERY = 1 << 2;  // block_query
    public static final int FEATURE_COOK = 1 << 3;         // hero_cook
    public static final int FEATURE_TRANSFER = 1 << 4;     // item_transfer
    public static final int FEATURE_INTERACT = 1 << 5;     // hero_interact
    public static final int FEATURE_REMEMBER = 1 << 6;     // hero_remember
    public static final int FEATURE_BUILD_PREVIEW = 1 << 7; // build_preview
    public static final int FEATURE_GATHER = 1 << 8;       // hero_gather

    /** 本构建实现的所有扩展能力。 */
    public static final int ALL_FEATURES =
            FEATURE_TASK_CONTROL | FEATURE_NAVIGATE | FEATURE_BLOCK_QUERY | FEATURE_COOK
                    | FEATURE_TRANSFER | FEATURE_INTERACT | FEATURE_REMEMBER
                    | FEATURE_BUILD_PREVIEW | FEATURE_GATHER;

    private AgentFeatureChannel() {
        super("agent_features", VERSION);
    }

    public static void register() {
        INSTANCE.registerMessage(0, AgentFeatureRequest.class, AgentFeatureRequest::encode,
                AgentFeatureRequest::new, AgentFeatureRequest::handle,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER);
        INSTANCE.registerMessage(1, AgentFeatureReply.class, AgentFeatureReply::encode,
                AgentFeatureReply::new, AgentFeatureReply::handle,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    /** 该连接的对端是否声明支持本通道（旧端返回 false）。 */
    public static boolean isSupported(net.minecraft.network.Connection connection) {
        return INSTANCE.remoteSupports(connection);
    }

    /** 客户端发一次能力查询（仅在对端支持本通道时调用）。 */
    public static void sendRequest(AgentFeatureRequest request) {
        INSTANCE.sendToServer(request);
    }

    /** 服务端把自身能力位回给某个玩家。 */
    public static void reply(net.minecraft.server.level.ServerPlayer player, UUID requestId, int features) {
        INSTANCE.sendToPlayer(player, new AgentFeatureReply(requestId, features));
    }
}
