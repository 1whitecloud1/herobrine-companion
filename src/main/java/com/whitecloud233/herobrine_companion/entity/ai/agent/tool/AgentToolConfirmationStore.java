package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 待确认工具请求仓储（P3）：按 requestId 存留"需要玩家确认"的工具调用，等待审批包取走。
 *
 * <p><b>单一职责</b>：只做"入队 / 按 requestId 取走"。审批动作在网络包侧
 * （{@code ApproveToolPacket} / {@code RejectToolPacket}），与注册表、执行器解耦。</p>
 *
 * <p>有界：满员淘汰最旧；过期/超时条目在 {@code take} 时按 gameTime 惰性丢弃。</p>
 */
public final class AgentToolConfirmationStore {

    private static final int MAX_ENTRIES = 32;

    /** 过期阈值（tick）：超此年龄的待确认条目视为已过期，审批时丢弃。 */
    private static final long EXPIRY_TICKS = 20L * 60L;

    private static final Map<UUID, ConfirmationEntry> PENDING = new LinkedHashMap<>();

    private AgentToolConfirmationStore() {
    }

    /** 一条待确认的工具调用（不可变）。 */
    public record ConfirmationEntry(
            UUID requestId,
            String toolId,
            AgentToolArgs args,
            UUID requesterUuid,
            long gameTime,
            String description) {
    }

    /** 入队（幂等覆盖同 requestId；满员淘汰最旧）。 */
    public static synchronized void enqueue(ConfirmationEntry entry) {
        if (entry == null || entry.requestId() == null) {
            return;
        }
        PENDING.put(entry.requestId(), entry);
        while (PENDING.size() > MAX_ENTRIES) {
            UUID oldest = PENDING.keySet().iterator().next();
            PENDING.remove(oldest);
        }
    }

    /** 按 requestId 取走（消费一次即移除）；过期条目直接丢弃返回 null。 */
    public static synchronized ConfirmationEntry take(UUID requestId, long currentGameTime) {
        ConfirmationEntry entry = requestId == null ? null : PENDING.remove(requestId);
        if (entry != null && currentGameTime - entry.gameTime() > EXPIRY_TICKS) {
            return null;
        }
        return entry;
    }

    /** 待确认条目数（调试 / 可观测）。 */
    public static synchronized int pendingCount() {
        return PENDING.size();
    }

    /** 清空（退出世界 / 调试）。 */
    public static synchronized void clear() {
        PENDING.clear();
    }
}
