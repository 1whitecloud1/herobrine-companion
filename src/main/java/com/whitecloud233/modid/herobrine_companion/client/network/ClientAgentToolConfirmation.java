package com.whitecloud233.modid.herobrine_companion.client.network;

import com.whitecloud233.modid.herobrine_companion.client.gui.AgentConfirmScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 收包后弹出 agent 工具确认屏的入口。由 {@code NetworkClientBridge} 经 DistExecutor 转发（主线程）。
 *
 * <p><b>单一职责</b>：只做"收到审批提示 → 弹屏"；确认/拒绝动作由 {@link AgentConfirmScreen}
 * 发 {@code ApproveToolPacket} / {@code RejectToolPacket}。</p>
 */
public final class ClientAgentToolConfirmation {

    private ClientAgentToolConfirmation() {
    }

    public static void accept(UUID requestId, String toolId, Map<String, String> args, Component description) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new AgentConfirmScreen(mc.screen, requestId, toolId, args, description));
    }
}
