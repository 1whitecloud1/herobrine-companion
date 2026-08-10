package com.whitecloud233.modid.herobrine_companion.client.network;

import com.whitecloud233.modid.herobrine_companion.client.agent.ClientAgentStatusStore;
import com.whitecloud233.modid.herobrine_companion.client.gui.AgentStatusScreen;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.AgentStatusSnapshot;
import net.minecraft.client.Minecraft;

/**
 * 收包后写入客户端 agent 状态仓储的入口。由 {@code NetworkClientBridge} 经 DistExecutor 转发，
 * 与 {@link ClientUiDispatch} / {@link ClientStateSync} 同级。
 *
 * <p>首次收到快照时顺带打开面板：服务端 {@code /hbagent status} 只发数据，
 * 由客户端决定"是否展示"，保持服务端不依赖任何 GUI 类。</p>
 */
public final class ClientAgentStatus {

    private ClientAgentStatus() {
    }

    public static void accept(AgentStatusSnapshot snapshot) {
        ClientAgentStatusStore.accept(snapshot);
        // 面板未打开时自动开启一次；已打开则仅刷新数据（面板每秒自行轮询）。
        if (!(Minecraft.getInstance().screen instanceof AgentStatusScreen)) {
            AgentStatusScreen.open();
        }
    }
}
