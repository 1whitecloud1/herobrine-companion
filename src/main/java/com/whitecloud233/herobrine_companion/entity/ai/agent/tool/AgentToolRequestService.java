package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.AgentFrame;
import com.whitecloud233.herobrine_companion.entity.ai.agent.AgentToolInbox;
import com.whitecloud233.herobrine_companion.entity.ai.agent.AgentToolRequest;
import com.whitecloud233.herobrine_companion.network.AgentToolResultPacket;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.ToolApprovalPromptPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 显式工具请求的<b>有界服务端执行入口</b>（交接 A1/A3）。
 *
 * <p><b>为什么独立成类</b>：交接要求"已知工具 ID 的请求不再经过自主分类，直接进入有界执行入口"。
 * 原来的路径是 请求 → {@code pendingRequest} 单槽 → 指令传感器 → 分类器 → 规划器 → 执行器，
 * 每一步都可能丢请求；现在 {@link com.whitecloud233.herobrine_companion.entity.ai.agent.HeroAgent}
 * 只做一件事：把请求交给本服务，短动作在服务端线程立刻执行，需要等待的进 {@link AgentToolInbox}。</p>
 *
 * <p><b>单一职责</b>：受理 / 去重 / 执行 / 回执 / 记录终态。
 * 不解析聊天、不规划任务、不生成自然语言。</p>
 *
 * <p><b>结果语义</b>（交接 A3）：成功、等待确认、软件拒绝（参数/未知工具/容量）、
 * 世界动作失败、拒绝、过期分别有独立原因，只有世界事实才允许写入经历。</p>
 */
public final class AgentToolRequestService {

    private final AgentToolInbox inbox;
    private final AgentToolRegistry registry;

    public AgentToolRequestService(AgentToolInbox inbox, AgentToolRegistry registry) {
        this.inbox = inbox == null ? new AgentToolInbox() : inbox;
        this.registry = registry;
    }

    public AgentToolInbox inbox() {
        return inbox;
    }

    /**
     * 受理一条显式请求。
     *
     * @return 投递结果；{@link AgentToolInbox.Offer#FULL} 时本方法已向请求者回了 busy 回执
     */
    public AgentToolInbox.Offer submit(HeroEntity hero, AgentToolRequest request) {
        if (hero == null || request == null) {
            return AgentToolInbox.Offer.DUPLICATE;
        }
        long gameTime = gameTime(hero);
        AgentToolRequest stamped = request.stamped(gameTime);
        AgentToolInbox.Offer offer = inbox.offer(stamped);
        switch (offer) {
            case FULL -> reply(hero, stamped, AgentToolResult.denied(
                    "请求队列已满（" + AgentToolInbox.CAPACITY + "），本次未执行",
                    Component.translatable("message.herobrine_companion.tool.error.busy"),
                    AgentToolResult.Cause.CAPACITY));
            case DUPLICATE -> {
                // 重复 ID 不再执行副作用，也不再发第二份回执（迟到的重复回执会被客户端忽略）。
            }
            case ACCEPTED -> {
                // 入队后立刻尝试执行：短动作不需要等下一个感知周期（A1 的"短动作及时执行"）。
                drain(hero, 1);
            }
            default -> {
            }
        }
        return offer;
    }

    /**
     * 逐 tick 推进队列：每 tick 最多执行 {@code maxPerTick} 条，避免一次抽干主线程。
     *
     * @return 本 tick 实际执行的条数
     */
    public int drain(HeroEntity hero, int maxPerTick) {
        if (hero == null || hero.level() == null || hero.level().isClientSide || hero.isRemoved() || !hero.isAlive()) {
            return 0;
        }
        int executed = 0;
        AgentFrame frame = null;
        while (executed < Math.max(1, maxPerTick)) {
            AgentToolRequest request = inbox.poll();
            if (request == null) {
                break;
            }
            long gameTime = gameTime(hero);
            if (request.isExpired(gameTime)) {
                // 过期请求给出确定终态，且绝不执行副作用（A3 验收：过期后再确认不得执行）。
                finish(hero, request, AgentToolResult.expired("请求已过期，未执行"));
                continue;
            }
            if (frame == null) {
                frame = AgentFrame.capture(hero);
            }
            AgentToolResult result = registry.invoke(request.toolId(),
                    new AgentToolContext(frame, hero, request.requesterUuid()),
                    request.args(), request.playerApproved());
            // 重复确认：同一个 requestId 已经在途 / 已终结时，注册表可能因为确认标记而被再次执行。
            // inbox 的去重已经挡住重放，这里再兜一次"已确认过一次就不再产生第二次副作用"。
            apply(hero, frame, request, result);
            executed++;
        }
        return executed;
    }

    /** 队列里的请求在实体移除 / 换世界 / 归属切换时统一收尾。 */
    public void clear() {
        inbox.clear();
    }

    /** 执行一条已经由外部取出（或内部刚取到）的请求，并处理回执与等待确认。 */
    private void apply(HeroEntity hero, AgentFrame frame, AgentToolRequest request, AgentToolResult result) {
        if (result.confirmationRequired()) {
            // 等待确认既不是成功也不是失败（A3）：入待确认仓储 + 弹审批屏，不发最终结果包，
            // 等玩家确认后按同一 requestId 再来一次；不写失败记忆。
            //
            // 【重要】这里**不能**写终态记录：终态表示"这个 requestId 永远结束了"，
            // 一旦写进去，审批通过后按同一 ID 重新投递会被 inbox 判成 DUPLICATE 而静默丢弃，
            // 工具就永远不会执行（等确认 → 确认 → 什么都没发生）。
            // 等待中的归属由 AgentToolConfirmationStore 单独维护，它有独立的有效期与一次性消费。
            if (request.requestId() != null) {
                AgentToolConfirmationStore.enqueue(new AgentToolConfirmationStore.ConfirmationEntry(
                        request.requestId(), request.toolId(), request.args(), request.requesterUuid(),
                        frame.gameTime(), result.message(), dimensionKey(hero)));
                ServerPlayer requester = player(hero, request.requesterUuid());
                if (requester != null) {
                    PacketHandler.sendToPlayer(new ToolApprovalPromptPacket(
                            request.requestId(), request.toolId(), request.args().asMap(), result.displayComponent()), requester);
                }
            }
            return;
        }
        finish(hero, request, result);
    }

    /** 结束一条请求：回执 + 终态记录。 */
    private void finish(HeroEntity hero, AgentToolRequest request, AgentToolResult result) {
        reply(hero, request, result);
        if (request.requestId() != null) {
            inbox.rememberTerminal(request.requestId(), result.ok(), result.cause().name());
        }
    }

    /**
     * 把结果发回请求者。
     *
     * <p>玩家聊天走本地化 display；{@code message} 保持中文供 LLM / 审计
     * （沿用既有 {@code DefaultAgentExecutor} 的呈现约定）。</p>
     */
    private void reply(HeroEntity hero, AgentToolRequest request, AgentToolResult result) {
        ServerPlayer requester = player(hero, request.requesterUuid());
        if (requester == null) {
            return;
        }
        String prefix = result.ok() ? "§a[Herobrine] " : "§c[Herobrine] ";
        requester.sendSystemMessage(Component.literal(prefix).append(result.displayComponent()));
        if (request.requestId() != null) {
            PacketHandler.sendToPlayer(new AgentToolResultPacket(
                    request.requestId(), request.toolId(), result.ok(), result.message()), requester);
        }
    }

    /**
     * 解析请求者。
     *
     * <p>必须走 server 的玩家表：玩家说"你过来"时往往与 Hero 不在同一维度，
     * {@code level().getPlayerByUUID()} 在那个维度恒为 null。</p>
     */
    private static ServerPlayer player(HeroEntity hero, java.util.UUID requesterUuid) {
        if (hero == null || hero.getServer() == null) {
            return null;
        }
        return requesterUuid == null ? null : hero.getServer().getPlayerList().getPlayer(requesterUuid);
    }

    private static long gameTime(HeroEntity hero) {
        return hero.level() == null ? 0L : hero.level().getGameTime();
    }

    /** 维度键（审批对象绑定世界会话，换维度后原审批不再适用）。 */
    private static String dimensionKey(HeroEntity hero) {
        return hero.level() == null ? "" : hero.level().dimension().location().toString();
    }
}
