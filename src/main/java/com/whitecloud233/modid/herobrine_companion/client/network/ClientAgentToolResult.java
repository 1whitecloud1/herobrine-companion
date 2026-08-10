package com.whitecloud233.modid.herobrine_companion.client.network;

import com.whitecloud233.modid.herobrine_companion.client.agent.ClientAgentToolRequestStore;

import java.util.UUID;

/**
 * 收包后写入客户端 agent 工具结果仓储的入口。由 {@code NetworkClientBridge} 经 DistExecutor 转发，
 * 与 {@link ClientAgentStatus} 同级。
 *
 * <p><b>单一职责</b>：只做"收包 → 落仓"，不做消费（消费方见 {@code ClientAgentToolRequestStore} 的
 * take/recentResult，由 P2 的提示词注入 / 合成编排调用）。</p>
 */
public final class ClientAgentToolResult {

    private ClientAgentToolResult() {
    }

    public static void accept(UUID requestId, String toolId, boolean ok, String message) {
        ClientAgentToolRequestStore.complete(requestId, toolId, ok, message);
    }
}
