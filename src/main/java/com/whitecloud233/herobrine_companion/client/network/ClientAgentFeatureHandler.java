package com.whitecloud233.herobrine_companion.client.network;

import com.whitecloud233.herobrine_companion.client.agent.ClientAgentFeatures;

/**
 * 收包后写入服务端能力位。由 {@code NetworkClientBridge} 经 DistExecutor 转发（主线程）。
 *
 * <p><b>单一职责</b>：只做"收包 → 落仓"。</p>
 */
public final class ClientAgentFeatureHandler {

    private ClientAgentFeatureHandler() {
    }

    public static void accept(int features) {
        ClientAgentFeatures.accept(features);
    }
}
