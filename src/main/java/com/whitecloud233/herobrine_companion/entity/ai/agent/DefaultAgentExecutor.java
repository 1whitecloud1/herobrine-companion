package com.whitecloud233.herobrine_companion.entity.ai.agent;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolConfirmationStore;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolContext;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolRegistry;
import com.whitecloud233.herobrine_companion.network.AgentToolResultPacket;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.ToolApprovalPromptPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * M2 默认执行器：真正让"计划发生"的统一出口。
 *
 * <ul>
 *   <li><b>工具通道</b>：计划路由到 {@link AgentChannel#TOOL_INVOCATION} 且携带工具请求时，
 *       委托给 {@link AgentToolRegistry 统一工具注册表}（白名单/校验/确认/审计均已收敛其中）。</li>
 *   <li><b>世界 / 空闲通道</b>：不额外动作，交还既有 goal 系统（保持 M1 无回归）。</li>
 * </ul>
 *
 * <p>依赖倒置：执行器只依赖注册表抽象，不直接触碰具体工具。</p>
 */
public final class DefaultAgentExecutor implements AgentExecutor {

    private final AgentToolRegistry registry;

    public DefaultAgentExecutor(AgentToolRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Outcome execute(HeroEntity hero, AgentFrame frame, AgentPlan plan, AgentToolRequest request) {
        if (plan.channel() == AgentChannel.TOOL_INVOCATION && request != null) {
            AgentToolContext context = new AgentToolContext(frame, hero, request.requesterUuid());
            var result = registry.invoke(
                    request.toolId(),
                    context,
                    request.args(),
                    request.playerApproved());
            // 工具结果即时反馈给请求者（服务端线程直接播报，无需额外 S→C 包）。
            if (request.requesterUuid() != null && hero.level().getPlayerByUUID(request.requesterUuid()) instanceof ServerPlayer requester) {
                if (result.confirmationRequired()) {
                    // P3：需要玩家确认 → 入待确认队列 + 发审批包弹屏；不发最终结果包（等确认后执行再回）。
                    if (request.requestId() != null) {
                        AgentToolConfirmationStore.enqueue(new AgentToolConfirmationStore.ConfirmationEntry(
                                request.requestId(), request.toolId(), request.args(), request.requesterUuid(), frame.gameTime(), result.message()));
                        PacketHandler.sendToPlayer(new ToolApprovalPromptPacket(
                                request.requestId(), request.toolId(), request.args().asMap(), result.displayComponent()), requester);
                    }
                    return new Outcome(false, "工具 [" + request.toolId() + "] 需要玩家确认");
                }
                // 玩家聊天走本地化 display；message 保持中文供 LLM/审计。
                String prefix = result.ok() ? "§a[Herobrine] " : "§c[Herobrine] ";
                requester.sendSystemMessage(Component.literal(prefix).append(result.displayComponent()));
                // M4 共享基础设施：带 requestId 时把结果经 S→C 包送回请求客户端，
                // 供 ClientAgentToolRequestStore 关联（P2 起注入 LLM / 关联合成调用）。
                if (request.requestId() != null) {
                    PacketHandler.sendToPlayer(new AgentToolResultPacket(
                            request.requestId(), request.toolId(), result.ok(), result.message()), requester);
                }
            }
            return new Outcome(result.ok(),
                    (result.ok() ? "工具 [" + request.toolId() + "]: " : "工具 [" + request.toolId() + "] 被拒: ") + result.message());
        }
        return new Outcome(false, "M2 让位给既有层: " + plan.actionNote());
    }
}